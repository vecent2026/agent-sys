# 大模型适配器设计

## 文档信息

| 项 | 内容 |
|----|------|
| 文档编号 | 13 |
| 所属层次 | 技术层 |
| 关联文档 | 02-llm-management、11-backend-design |
| 版本 | v1.0 |
| 状态 | 草稿 |

---

## 1. 设计目标

**核心问题：** 不同 LLM 供应商的 API 接口、参数格式、错误码、流式协议各不相同，如何让 agent-service 的上层代码（Agent 执行引擎、Skill 执行器等）屏蔽这些差异？

**解决方案：** 统一的 LLM Adapter 层，所有模型供应商通过适配器映射到同一接口，上层代码只调用统一接口。

**架构分层：**

```
上层调用（Agent Executor / Skill Executor）
    │ 调用统一接口
    ▼
LLM Adapter Layer（本文档）
    ├── LiteLLM Adapter（默认，100+ 模型）
    ├── Anthropic Adapter（Claude 特有功能：Extended Thinking）
    ├── Azure OpenAI Adapter（AD 认证、部署名差异）
    ├── Ollama Adapter（本地推理）
    └── Custom Gateway Adapter（自建网关）
    │ 调用实际 API
    ▼
各供应商 API
```

---

## 2. 统一接口定义

### 2.1 LLMAdapter 基类

```python
# app/core/llm/adapter.py

from abc import ABC, abstractmethod
from typing import AsyncGenerator
from pydantic import BaseModel

class LLMMessage(BaseModel):
    role: str                   # system / user / assistant / tool
    content: str | list         # 文本或多模态内容
    tool_calls: list | None = None
    tool_call_id: str | None = None
    name: str | None = None

class LLMTool(BaseModel):
    name: str
    description: str
    parameters: dict            # JSON Schema

class LLMRequest(BaseModel):
    model_identifier: str       # 实际调用的模型标识（如 gpt-4.1-mini）
    messages: list[LLMMessage]
    tools: list[LLMTool] | None = None
    tool_choice: str | dict = "auto"  # auto / none / required / specific tool
    temperature: float = 0.7
    top_p: float = 1.0
    max_tokens: int | None = None
    stream: bool = False
    response_format: dict | None = None     # structured output
    thinking_budget_tokens: int | None = None  # Claude Extended Thinking

class LLMUsage(BaseModel):
    prompt_tokens: int
    completion_tokens: int
    total_tokens: int
    thinking_tokens: int | None = None      # Claude 推理 Token

class LLMToolCall(BaseModel):
    id: str
    name: str
    arguments: dict

class LLMResponse(BaseModel):
    content: str | None         # 文本内容
    tool_calls: list[LLMToolCall] | None = None
    thinking_content: str | None = None     # Claude Extended Thinking 推理内容
    usage: LLMUsage
    finish_reason: str          # stop / tool_calls / length / content_filter
    model: str                  # 实际使用的模型

class LLMStreamEvent(BaseModel):
    type: str                   # text_delta / tool_call_delta / thinking_delta / done
    content: str | None = None
    tool_call_id: str | None = None
    tool_name: str | None = None
    tool_arguments_delta: str | None = None
    usage: LLMUsage | None = None   # type=done 时包含

class LLMAdapter(ABC):
    @abstractmethod
    async def complete(self, request: LLMRequest) -> LLMResponse:
        """非流式调用"""
        pass

    @abstractmethod
    async def stream_complete(
        self, request: LLMRequest
    ) -> AsyncGenerator[LLMStreamEvent, None]:
        """流式调用，生成 LLMStreamEvent 序列"""
        pass

    @abstractmethod
    async def test_connectivity(self, model_identifier: str) -> dict:
        """可用性测试，返回 {success: bool, latency_ms: int, error: str | None}"""
        pass
```

---

## 3. LiteLLM 适配器（默认）

### 3.1 为什么用 LiteLLM

LiteLLM 是目前最成熟的 LLM 统一 Gateway 库（GitHub 14k+ Stars，每月 npm 下载量 97M+），支持：

- 100+ 模型供应商（OpenAI、Anthropic、Google、Azure、Moonshot、通义等）
- 统一的 OpenAI 接口格式
- 内置重试、负载均衡、成本追踪
- 异步支持（aiolite）

### 3.2 实现

```python
# app/core/llm/litellm_adapter.py

import litellm
from litellm import acompletion
from typing import AsyncGenerator
from app.core.llm.adapter import LLMAdapter, LLMRequest, LLMResponse, LLMStreamEvent
from app.services.secret_service import SecretService

class LiteLLMAdapter(LLMAdapter):
    """
    使用 LiteLLM 统一调用各主流模型供应商
    支持：OpenAI / Azure OpenAI / Anthropic / Google Gemini /
          Moonshot / 通义千问 / DeepSeek / 百川 / 文心 等
    """

    def __init__(self, secret_service: SecretService):
        self.secret_service = secret_service

    async def _build_litellm_params(self, request: LLMRequest, model_config: dict) -> dict:
        """将 LLMRequest 转为 LiteLLM 参数"""
        # 获取解密后的 API Key
        api_key = await self.secret_service.get_decrypted_key(model_config["secret_id"])

        params = {
            "model": request.model_identifier,
            "messages": [m.dict(exclude_none=True) for m in request.messages],
            "temperature": request.temperature,
            "max_tokens": request.max_tokens,
            "stream": request.stream,
            "api_key": api_key,
            "api_base": model_config.get("base_url"),
            "timeout": model_config.get("timeout_ms", 30000) / 1000,
        }

        if request.tools:
            params["tools"] = [
                {
                    "type": "function",
                    "function": {
                        "name": t.name,
                        "description": t.description,
                        "parameters": t.parameters
                    }
                }
                for t in request.tools
            ]
            params["tool_choice"] = request.tool_choice

        if request.response_format:
            params["response_format"] = request.response_format

        return params

    async def complete(self, request: LLMRequest, model_config: dict) -> LLMResponse:
        params = await self._build_litellm_params(request, model_config)

        try:
            response = await acompletion(**params)
        except litellm.exceptions.AuthenticationError as e:
            raise LLMAuthError(f"API Key 认证失败: {e}")
        except litellm.exceptions.RateLimitError as e:
            raise LLMRateLimitError(f"请求频率超限: {e}")
        except litellm.exceptions.Timeout as e:
            raise LLMTimeoutError(f"请求超时: {e}")
        except litellm.exceptions.BadRequestError as e:
            raise LLMBadRequestError(f"请求参数错误: {e}")

        choice = response.choices[0]
        message = choice.message

        return LLMResponse(
            content=message.content,
            tool_calls=[
                LLMToolCall(
                    id=tc.id,
                    name=tc.function.name,
                    arguments=json.loads(tc.function.arguments)
                )
                for tc in (message.tool_calls or [])
            ] or None,
            usage=LLMUsage(
                prompt_tokens=response.usage.prompt_tokens,
                completion_tokens=response.usage.completion_tokens,
                total_tokens=response.usage.total_tokens
            ),
            finish_reason=choice.finish_reason,
            model=response.model
        )

    async def stream_complete(
        self, request: LLMRequest, model_config: dict
    ) -> AsyncGenerator[LLMStreamEvent, None]:
        params = await self._build_litellm_params(request, model_config)
        params["stream"] = True

        tool_call_accumulator = {}  # 累积流式工具调用的 arguments

        async for chunk in await acompletion(**params):
            choice = chunk.choices[0]
            delta = choice.delta

            if delta.content:
                yield LLMStreamEvent(type="text_delta", content=delta.content)

            if delta.tool_calls:
                for tc_delta in delta.tool_calls:
                    idx = tc_delta.index
                    if idx not in tool_call_accumulator:
                        tool_call_accumulator[idx] = {
                            "id": tc_delta.id or "",
                            "name": tc_delta.function.name or "",
                            "arguments": ""
                        }
                    if tc_delta.function.arguments:
                        tool_call_accumulator[idx]["arguments"] += tc_delta.function.arguments

                    yield LLMStreamEvent(
                        type="tool_call_delta",
                        tool_call_id=tc_delta.id,
                        tool_name=tc_delta.function.name,
                        tool_arguments_delta=tc_delta.function.arguments
                    )

            if choice.finish_reason:
                # 流结束时发出 done 事件（含完整 usage）
                if hasattr(chunk, 'usage') and chunk.usage:
                    yield LLMStreamEvent(
                        type="done",
                        usage=LLMUsage(
                            prompt_tokens=chunk.usage.prompt_tokens,
                            completion_tokens=chunk.usage.completion_tokens,
                            total_tokens=chunk.usage.total_tokens
                        )
                    )

    async def test_connectivity(self, model_identifier: str, model_config: dict) -> dict:
        import time
        start = time.time()
        try:
            request = LLMRequest(
                model_identifier=model_identifier,
                messages=[LLMMessage(role="user", content="Say 'OK' in one word.")],
                max_tokens=5,
                stream=False
            )
            await self.complete(request, model_config)
            latency_ms = int((time.time() - start) * 1000)
            return {"success": True, "latency_ms": latency_ms, "error": None}
        except Exception as e:
            latency_ms = int((time.time() - start) * 1000)
            return {
                "success": False,
                "latency_ms": latency_ms,
                "error": str(e),
                "error_type": type(e).__name__
            }
```

---

## 4. Anthropic 专用适配器（Extended Thinking）

Claude 3.7 Sonnet 的 Extended Thinking 功能（推理 Token）需要专门处理，LiteLLM 目前对其支持有限：

```python
# app/core/llm/anthropic_adapter.py

import anthropic

class AnthropicAdapter(LLMAdapter):
    """
    直接使用 Anthropic Python SDK
    主要用于支持 Extended Thinking（推理 Token）功能
    """

    def __init__(self, secret_service: SecretService):
        self.secret_service = secret_service

    async def complete(self, request: LLMRequest, model_config: dict) -> LLMResponse:
        api_key = await self.secret_service.get_decrypted_key(model_config["secret_id"])
        client = anthropic.AsyncAnthropic(api_key=api_key)

        # 转换消息格式（system 消息单独处理）
        system_prompt = None
        messages = []
        for msg in request.messages:
            if msg.role == "system":
                system_prompt = msg.content
            else:
                messages.append({"role": msg.role, "content": msg.content})

        params = {
            "model": request.model_identifier,
            "messages": messages,
            "max_tokens": request.max_tokens or 4096,
        }
        if system_prompt:
            params["system"] = system_prompt
        if request.temperature is not None:
            params["temperature"] = request.temperature

        # Extended Thinking 配置
        if request.thinking_budget_tokens:
            params["thinking"] = {
                "type": "enabled",
                "budget_tokens": request.thinking_budget_tokens
            }
            # Extended Thinking 时 temperature 必须为 1
            params["temperature"] = 1.0

        # 工具转 Anthropic 格式
        if request.tools:
            params["tools"] = [
                {
                    "name": t.name,
                    "description": t.description,
                    "input_schema": t.parameters
                }
                for t in request.tools
            ]

        response = await client.messages.create(**params)

        # 解析响应
        content_text = None
        thinking_content = None
        tool_calls = []

        for block in response.content:
            if block.type == "thinking":
                thinking_content = block.thinking
            elif block.type == "text":
                content_text = block.text
            elif block.type == "tool_use":
                tool_calls.append(LLMToolCall(
                    id=block.id,
                    name=block.name,
                    arguments=block.input
                ))

        return LLMResponse(
            content=content_text,
            tool_calls=tool_calls or None,
            thinking_content=thinking_content,
            usage=LLMUsage(
                prompt_tokens=response.usage.input_tokens,
                completion_tokens=response.usage.output_tokens,
                total_tokens=response.usage.input_tokens + response.usage.output_tokens,
                thinking_tokens=getattr(response.usage, 'cache_read_input_tokens', None)
            ),
            finish_reason=response.stop_reason,
            model=response.model
        )

    async def stream_complete(
        self, request: LLMRequest, model_config: dict
    ) -> AsyncGenerator[LLMStreamEvent, None]:
        api_key = await self.secret_service.get_decrypted_key(model_config["secret_id"])
        client = anthropic.AsyncAnthropic(api_key=api_key)

        # 构建参数（同 complete）
        params = self._build_params(request)

        async with client.messages.stream(**params) as stream:
            async for event in stream:
                if event.type == "content_block_delta":
                    if event.delta.type == "text_delta":
                        yield LLMStreamEvent(type="text_delta", content=event.delta.text)
                    elif event.delta.type == "thinking_delta":
                        yield LLMStreamEvent(type="thinking_delta", content=event.delta.thinking)
                    elif event.delta.type == "input_json_delta":
                        yield LLMStreamEvent(
                            type="tool_call_delta",
                            tool_arguments_delta=event.delta.partial_json
                        )
                elif event.type == "message_stop":
                    usage = stream.get_final_message().usage
                    yield LLMStreamEvent(
                        type="done",
                        usage=LLMUsage(
                            prompt_tokens=usage.input_tokens,
                            completion_tokens=usage.output_tokens,
                            total_tokens=usage.input_tokens + usage.output_tokens
                        )
                    )
```

---

## 5. 供应商注册表与路由

```python
# app/core/llm/provider_registry.py

from enum import Enum

class ProviderType(str, Enum):
    OPENAI = "openai"
    AZURE_OPENAI = "azure_openai"
    ANTHROPIC = "anthropic"
    GOOGLE = "google"
    MOONSHOT = "moonshot"
    TONGYI = "tongyi"         # 通义千问
    BAICHUAN = "baichuan"     # 百川
    WENXIN = "wenxin"         # 文心一言
    DEEPSEEK = "deepseek"
    OLLAMA = "ollama"
    VLLM = "vllm"             # 自建 vLLM
    CUSTOM = "custom"         # 自建网关

# 供应商 -> 使用哪个适配器
ADAPTER_REGISTRY = {
    ProviderType.ANTHROPIC: "anthropic_adapter",   # 专用适配器（Extended Thinking）
    ProviderType.AZURE_OPENAI: "litellm_adapter",  # LiteLLM 支持 Azure
    # 其余全部使用 LiteLLM
    "_default": "litellm_adapter"
}

# 供应商默认 Base URL
DEFAULT_BASE_URLS = {
    ProviderType.OPENAI: "https://api.openai.com/v1",
    ProviderType.ANTHROPIC: "https://api.anthropic.com",
    ProviderType.GOOGLE: "https://generativelanguage.googleapis.com/v1beta",
    ProviderType.MOONSHOT: "https://api.moonshot.cn/v1",
    ProviderType.TONGYI: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    ProviderType.BAICHUAN: "https://api.baichuan-ai.com/v1",
    ProviderType.DEEPSEEK: "https://api.deepseek.com/v1",
    ProviderType.OLLAMA: "http://localhost:11434/v1",
    ProviderType.VLLM: "http://localhost:8000/v1",
}

# LiteLLM 调用时的模型前缀
LITELLM_MODEL_PREFIX = {
    ProviderType.AZURE_OPENAI: "azure/",
    ProviderType.ANTHROPIC: "anthropic/",
    ProviderType.GOOGLE: "gemini/",
    ProviderType.MOONSHOT: "moonshot/",
    ProviderType.TONGYI: "openai/",    # 通义千问兼容 OpenAI 接口
    ProviderType.DEEPSEEK: "deepseek/",
    ProviderType.OLLAMA: "ollama/",
    ProviderType.VLLM: "openai/",      # vLLM 兼容 OpenAI 接口
    ProviderType.CUSTOM: "openai/",    # 自建网关通常兼容 OpenAI 接口
    "_default": ""                     # OpenAI 无需前缀
}


class LLMAdapterRouter:
    """根据供应商类型路由到正确的适配器"""

    def __init__(
        self,
        litellm_adapter: LiteLLMAdapter,
        anthropic_adapter: AnthropicAdapter
    ):
        self.adapters = {
            "litellm_adapter": litellm_adapter,
            "anthropic_adapter": anthropic_adapter
        }

    def get_adapter(self, provider_type: str) -> LLMAdapter:
        adapter_name = ADAPTER_REGISTRY.get(provider_type, ADAPTER_REGISTRY["_default"])
        return self.adapters[adapter_name]

    def build_model_identifier(self, provider_type: str, model_identifier: str) -> str:
        """为 LiteLLM 构建完整的模型标识符"""
        prefix = LITELLM_MODEL_PREFIX.get(provider_type, "")
        return f"{prefix}{model_identifier}"
```

---

## 6. 密钥加密服务

### 6.1 AES-256-GCM 加密实现

```python
# app/services/secret_service.py

import base64
import os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from app.config import settings

class SecretService:
    """
    API Key 加密存储服务
    使用 AES-256-GCM：对称加密，带认证标签，防篡改

    设计原则：
    1. 加密密钥存储在环境变量（settings.ENCRYPTION_KEY），不存 DB
    2. 每次加密生成随机 nonce（12 字节），存储在密文前缀
    3. 解密时从密文中提取 nonce
    4. SecretService 为抽象接口，未来可替换为 Vault/KMS
    """

    def __init__(self):
        self._key = base64.b64decode(settings.ENCRYPTION_KEY)  # 32 字节

    def encrypt(self, plaintext: str) -> str:
        """加密 API Key，返回 Base64 编码的密文（nonce + ciphertext）"""
        aesgcm = AESGCM(self._key)
        nonce = os.urandom(12)  # 96 位随机 nonce
        ciphertext = aesgcm.encrypt(nonce, plaintext.encode("utf-8"), None)
        # 存储格式：nonce（12字节）+ ciphertext
        return base64.b64encode(nonce + ciphertext).decode("utf-8")

    def decrypt(self, encrypted: str) -> str:
        """解密，返回原始 API Key"""
        data = base64.b64decode(encrypted)
        nonce = data[:12]
        ciphertext = data[12:]
        aesgcm = AESGCM(self._key)
        plaintext = aesgcm.decrypt(nonce, ciphertext, None)
        return plaintext.decode("utf-8")

    async def get_decrypted_key(self, secret_id: str) -> str:
        """从数据库取出密文，解密返回明文 API Key"""
        from app.repositories.secret_repo import SecretRepository
        # 这里应通过依赖注入获取 db，此处为简化示意
        secret = await self._secret_repo.get_by_id(secret_id)
        if not secret:
            raise ValueError(f"密钥配置不存在：{secret_id}")
        return self.decrypt(secret.encrypted_key)

    def mask_key(self, plaintext: str) -> str:
        """返回脱敏显示的 Key（如 sk-abcd...efgh）"""
        if len(plaintext) <= 8:
            return "****"
        return f"{plaintext[:6]}...{plaintext[-4:]}"
```

### 6.2 扩展接口（为未来迁移 Vault 预留）

```python
# app/services/secret_service_interface.py

from abc import ABC, abstractmethod

class ISecretService(ABC):
    """密钥服务抽象接口，当前实现为 AES 本地加密，未来可替换为 Vault/KMS"""

    @abstractmethod
    def encrypt(self, plaintext: str) -> str:
        """加密密钥"""
        pass

    @abstractmethod
    def decrypt(self, encrypted: str) -> str:
        """解密密钥"""
        pass

    @abstractmethod
    async def get_decrypted_key(self, secret_id: str) -> str:
        """通过 secret_id 获取解密后的 API Key"""
        pass


# 未来 HashiCorp Vault 实现（示意）
class VaultSecretService(ISecretService):
    def __init__(self, vault_url: str, token: str):
        self.vault_url = vault_url
        self.token = token

    async def get_decrypted_key(self, secret_id: str) -> str:
        # 直接从 Vault 读取，无需本地解密
        response = await http_client.get(
            f"{self.vault_url}/v1/secret/data/api-keys/{secret_id}",
            headers={"X-Vault-Token": self.token}
        )
        return response.json()["data"]["data"]["api_key"]
```

---

## 7. 错误处理与重试

### 7.1 错误类型映射

```python
# app/core/llm/exceptions.py

class LLMError(Exception):
    """LLM 调用基础异常"""
    def __init__(self, message: str, provider: str = None, model: str = None):
        super().__init__(message)
        self.provider = provider
        self.model = model

class LLMAuthError(LLMError):
    """API Key 无效或过期"""
    pass

class LLMRateLimitError(LLMError):
    """调用频率超限（供应商侧）"""
    pass

class LLMTimeoutError(LLMError):
    """请求超时"""
    pass

class LLMQuotaExceededError(LLMError):
    """配额耗尽"""
    pass

class LLMContentFilterError(LLMError):
    """内容被供应商安全过滤"""
    pass

class LLMBadRequestError(LLMError):
    """请求参数错误"""
    pass

class LLMContextLengthExceeded(LLMError):
    """上下文长度超过模型限制"""
    pass

class LLMServiceUnavailableError(LLMError):
    """供应商服务不可用（5xx 错误）"""
    pass
```

### 7.2 自动重试

```python
# app/core/llm/retry.py

import asyncio
from functools import wraps

def with_retry(max_retries: int = 2, retryable_errors: tuple = (LLMTimeoutError, LLMRateLimitError, LLMServiceUnavailableError)):
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            last_error = None
            for attempt in range(max_retries + 1):
                try:
                    return await func(*args, **kwargs)
                except retryable_errors as e:
                    last_error = e
                    if attempt < max_retries:
                        # 指数退避：1s, 2s, 4s...
                        wait = 2 ** attempt
                        if isinstance(e, LLMRateLimitError):
                            wait = max(wait, 5)  # 频率限制至少等 5 秒
                        await asyncio.sleep(wait)
                except Exception as e:
                    raise  # 非重试错误直接抛出
            raise last_error
        return wrapper
    return decorator
```

### 7.3 备用模型 Fallback

```python
# app/core/llm/fallback.py

class FallbackLLMCaller:
    """
    支持备用模型的 LLM 调用器
    当主模型调用失败时，自动切换到备用模型
    """

    def __init__(self, router: LLMAdapterRouter, model_repo: LLMModelRepository):
        self.router = router
        self.model_repo = model_repo

    async def complete_with_fallback(
        self,
        request: LLMRequest,
        primary_model_id: str,
        fallback_model_id: str | None = None
    ) -> tuple[LLMResponse, str]:
        """
        返回 (response, actual_model_id)
        """
        primary_config = await self.model_repo.get_by_id(primary_model_id)

        try:
            adapter = self.router.get_adapter(primary_config.provider_type)
            response = await adapter.complete(request, primary_config.to_dict())
            return response, primary_model_id

        except (LLMAuthError, LLMServiceUnavailableError, LLMQuotaExceededError) as e:
            if not fallback_model_id:
                raise

            # 记录主模型失败，切换到备用模型
            logger.warning(f"主模型 {primary_model_id} 失败，切换备用模型 {fallback_model_id}: {e}")

            fallback_config = await self.model_repo.get_by_id(fallback_model_id)
            fallback_request = request.copy(
                update={"model_identifier": fallback_config.model_identifier}
            )
            adapter = self.router.get_adapter(fallback_config.provider_type)
            response = await adapter.complete(fallback_request, fallback_config.to_dict())
            return response, fallback_model_id
```

---

## 8. Token 计量

```python
# app/core/llm/token_meter.py

import asyncio
from app.workers.celery_app import celery_app

class TokenMeter:
    """Token 消耗计量，异步写入统计表"""

    async def record(
        self,
        tenant_id: str,
        agent_id: str | None,
        model_id: str,
        input_tokens: int,
        output_tokens: int
    ):
        # 1. 更新 Redis 实时计数（用于限流判断）
        await self._update_redis_counter(tenant_id, agent_id, input_tokens + output_tokens)

        # 2. 异步写入 MySQL 统计表（通过 Celery 任务）
        celery_app.send_task(
            "app.workers.stats_worker.record_token_usage",
            args=[{
                "tenant_id": tenant_id,
                "agent_id": agent_id,
                "model_id": model_id,
                "input_tokens": input_tokens,
                "output_tokens": output_tokens,
                "stat_date": today()
            }]
        )

    async def _update_redis_counter(self, tenant_id: str, agent_id: str | None, tokens: int):
        keys = [f"tokens:tenant:{tenant_id}:minute"]
        if agent_id:
            keys.append(f"tokens:agent:{agent_id}:minute")

        pipe = redis_client.pipeline()
        for key in keys:
            pipe.incrby(key, tokens)
            pipe.expire(key, 120)   # 2 分钟 TTL
        await pipe.execute()
```

---

## 9. 供应商特殊配置说明

### 9.1 Azure OpenAI

Azure OpenAI 使用「部署名」而非模型标识，配置时需注意：

```yaml
provider_type: azure_openai
base_url: https://<your-resource>.openai.azure.com
model_identifier: <deployment_name>   # 不是 gpt-4，而是 Azure 中配置的部署名
# LiteLLM 调用时使用：azure/<deployment_name>
additional_config:
  api_version: "2024-02-01"          # Azure API 版本
```

### 9.2 通义千问

通义千问提供 OpenAI 兼容接口，可直接通过 LiteLLM 调用：

```yaml
provider_type: tongyi
base_url: https://dashscope.aliyuncs.com/compatible-mode/v1
model_identifier: qwen-plus   # qwen-turbo / qwen-plus / qwen-max / qwen-long
```

### 9.3 Ollama（本地推理）

Ollama 无需 API Key：

```yaml
provider_type: ollama
base_url: http://localhost:11434/v1
model_identifier: llama3.1:8b   # 任何 ollama pull 的模型名
secret_id: null    # 无需密钥
```

### 9.4 自建网关（vLLM / 其他兼容 OpenAI 协议的网关）

```yaml
provider_type: custom
base_url: https://your-gateway.internal/v1
model_identifier: your-model-name
# API Key 由网关验证
```

---

## 10. 可用性测试实现

```python
# 在 llm_service.py 中

async def test_model_availability(model_id: str) -> dict:
    """执行一键测试，结果写回数据库"""
    model = await llm_repo.get_by_id(model_id)

    adapter = router.get_adapter(model.provider_type)
    result = await adapter.test_connectivity(
        model_identifier=model.model_identifier,
        model_config=model.to_dict()
    )

    # 写回数据库
    await llm_repo.update(model_id,
        last_test_status="available" if result["success"] else "error",
        last_test_at=now(),
        last_test_latency_ms=result["latency_ms"],
        last_test_error=result.get("error")
    )

    # 记录历史（llm_availability_test 表）
    await llm_test_repo.create(
        model_id=model_id,
        success=result["success"],
        latency_ms=result["latency_ms"],
        error=result.get("error"),
        tested_at=now()
    )

    return result
```

---

*文档版本：v1.0 | 最后更新：2026-03-18*
