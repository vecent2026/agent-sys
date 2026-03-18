# agent-service 后端架构设计

## 文档信息

| 项 | 内容 |
|----|------|
| 文档编号 | 11 |
| 所属层次 | 技术层 |
| 关联文档 | 09-database-design、10-api-spec、13-llm-adapter-design |
| 版本 | v1.0 |
| 状态 | 草稿 |

---

## 1. 技术选型理由

### 1.1 为什么选 Python + FastAPI

| 维度 | Python + FastAPI | Java + Spring Boot |
|------|-----------------|-------------------|
| AI 生态集成 | 原生支持（LangChain/LlamaIndex/Anthropic SDK 等） | 需要封装 REST 调用 |
| 开发效率 | 更快（LLM 相关库直接使用） | 较慢（需要大量 wrapper） |
| 异步支持 | 原生 async/await | 需要 WebFlux 或线程池 |
| SSE 流式输出 | FastAPI 原生支持 | 需额外配置 |
| 团队技术栈 | admin-service 已有 Java，agent-service 独立 | 与 admin-service 重复 |
| 社区生态 | AI/ML 首选语言 | 企业后台首选 |

**结论：** agent-service 需要深度集成 AI 生态（模型调用、Embedding、向量检索、Tool 执行等），Python 是最合适的选择。

### 1.2 核心依赖库

| 功能 | 选用库 | 版本 | 理由 |
|------|--------|------|------|
| Web 框架 | FastAPI | 0.115+ | 高性能，原生 async，Pydantic v2 |
| ASGI 服务器 | Uvicorn | 0.30+ | FastAPI 推荐 |
| ORM | SQLAlchemy 2.0 | 2.0+ | async 支持，与 FastAPI 结合最佳 |
| 数据库驱动 | aiomysql | 0.2+ | 异步 MySQL |
| 缓存 | redis-py | 5.0+ | async 模式 |
| LLM 统一调用 | LiteLLM | 1.40+ | 支持 100+ 模型，统一接口 |
| 向量检索 | qdrant-client | 1.9+ | Qdrant 官方 async 客户端 |
| 文档处理 | unstructured | 0.13+ | PDF/Word/Excel 解析 |
| Embedding | openai / sentence-transformers | — | 支持多种 Embedding 模型 |
| 任务队列 | Celery + Redis | 5.3+ | 批量测试、Embedding 等异步任务 |
| Kafka | aiokafka | 0.10+ | 异步 Kafka 生产/消费 |
| 加密 | cryptography | 42.0+ | AES-256-GCM 密钥加密 |
| 测试 | pytest-asyncio | 0.23+ | 异步测试支持 |
| 校验 | Pydantic v2 | 2.7+ | 数据校验，Schema 生成 |

---

## 2. 项目结构

```
services/agent-service/
├── main.py                     # FastAPI 应用入口
├── pyproject.toml              # 依赖管理（uv/poetry）
├── .env.example                # 环境变量示例
│
├── app/
│   ├── __init__.py
│   ├── config.py               # 配置加载（Pydantic Settings）
│   ├── dependencies.py         # FastAPI 依赖注入（DB session, 当前用户等）
│   ├── exceptions.py           # 统一异常处理
│   ├── middleware.py           # 中间件（认证、日志、限流）
│   │
│   ├── api/                    # 路由层
│   │   ├── __init__.py
│   │   ├── router.py           # 主路由注册
│   │   ├── v1/
│   │   │   ├── llm/            # LLM 模型管理接口
│   │   │   ├── secrets/        # 密钥管理接口
│   │   │   ├── knowledge/      # 知识库接口
│   │   │   ├── skills/         # Skill 接口
│   │   │   ├── agents/         # Agent 接口
│   │   │   ├── workflows/      # Workflow 接口
│   │   │   ├── chat/           # 对话接口（含 SSE）
│   │   │   ├── test/           # 测试相关接口
│   │   │   ├── observability/  # 可观测性接口
│   │   │   └── api_keys/       # API Key 管理
│   │
│   ├── services/               # 业务逻辑层
│   │   ├── llm_service.py
│   │   ├── secret_service.py
│   │   ├── knowledge_service.py
│   │   ├── skill_service.py
│   │   ├── agent_service.py
│   │   ├── workflow_service.py
│   │   ├── chat_service.py
│   │   ├── publish_service.py  # 发布/回滚逻辑
│   │   ├── test_service.py
│   │   └── observability_service.py
│   │
│   ├── core/                   # 核心引擎
│   │   ├── llm/
│   │   │   ├── adapter.py      # LLM 适配器基类
│   │   │   ├── litellm_adapter.py   # LiteLLM 适配器（默认）
│   │   │   └── provider_registry.py # 供应商注册表
│   │   ├── agent/
│   │   │   ├── executor.py     # Agent 执行引擎（ReAct 循环）
│   │   │   ├── planner.py      # 任务规划
│   │   │   └── memory.py       # 记忆管理
│   │   ├── workflow/
│   │   │   ├── engine.py       # 工作流执行引擎
│   │   │   ├── node_executor.py # 节点执行器（策略模式）
│   │   │   └── dag_validator.py # DAG 校验
│   │   ├── rag/
│   │   │   ├── retriever.py    # 检索器
│   │   │   ├── embedder.py     # Embedding 执行
│   │   │   └── reranker.py     # Reranker
│   │   ├── tools/
│   │   │   ├── registry.py     # Tool 注册表
│   │   │   ├── executor.py     # Tool 执行器
│   │   │   ├── builtin/        # 内置工具实现
│   │   │   └── mcp/            # MCP 客户端
│   │   ├── guardrails/
│   │   │   ├── input_guard.py  # 输入防护
│   │   │   └── output_guard.py # 输出防护
│   │   └── trace/
│   │       ├── collector.py    # Trace 数据采集
│   │       └── publisher.py    # Trace 异步发布（Kafka）
│   │
│   ├── models/                 # SQLAlchemy ORM 模型
│   │   ├── llm.py
│   │   ├── agent.py
│   │   ├── skill.py
│   │   ├── knowledge.py
│   │   ├── workflow.py
│   │   ├── trace.py
│   │   └── ...
│   │
│   ├── schemas/                # Pydantic 请求/响应 Schema
│   │   ├── llm.py
│   │   ├── agent.py
│   │   └── ...
│   │
│   ├── repositories/           # 数据访问层
│   │   ├── base.py             # 通用 CRUD 基类
│   │   ├── llm_repo.py
│   │   ├── agent_repo.py
│   │   └── ...
│   │
│   └── workers/                # Celery 异步任务
│       ├── celery_app.py
│       ├── embedding_worker.py # 文档 Embedding 任务
│       ├── eval_worker.py      # LLM-as-Judge 评估任务
│       └── test_worker.py      # 批量测试任务
│
├── tests/
│   ├── unit/
│   ├── integration/
│   └── conftest.py
│
├── migrations/                 # Alembic 数据库迁移
│   ├── env.py
│   └── versions/
│
└── scripts/
    ├── init_db.sql             # 初始化 SQL
    └── seed_data.py            # 初始化数据
```

---

## 3. 认证与授权

### 3.1 JWT 认证复用

agent-service **不维护自己的用户体系**，完全复用 admin-service 的 JWT：

```python
# app/core/auth.py

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import jwt
from app.config import settings

security = HTTPBearer()

async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security)
) -> dict:
    """解析 JWT，返回当前用户信息"""
    token = credentials.credentials
    try:
        payload = jwt.decode(
            token,
            settings.JWT_SECRET,      # 与 admin-service 共用同一个密钥
            algorithms=["HS256"]
        )
        return {
            "user_id": payload["sub"],
            "tenant_id": payload.get("tenant_id"),
            "roles": payload.get("roles", []),
            "is_platform_admin": payload.get("is_platform_admin", False)
        }
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token 已过期")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Token 无效")
```

### 3.2 权限检查装饰器

```python
# app/core/permissions.py

from functools import wraps
from fastapi import Depends, HTTPException
from app.core.auth import get_current_user

def require_platform_admin(func):
    """只有平台管理员可以访问（LLM 模型管理、密钥管理等）"""
    @wraps(func)
    async def wrapper(*args, current_user=Depends(get_current_user), **kwargs):
        if not current_user["is_platform_admin"]:
            raise HTTPException(status_code=403, detail="需要平台管理员权限")
        return await func(*args, current_user=current_user, **kwargs)
    return wrapper

def require_tenant_member(func):
    """需要租户成员身份"""
    @wraps(func)
    async def wrapper(*args, current_user=Depends(get_current_user), **kwargs):
        if not current_user.get("tenant_id"):
            raise HTTPException(status_code=403, detail="需要租户成员身份")
        return await func(*args, current_user=current_user, **kwargs)
    return wrapper
```

### 3.3 API Key 认证（对话接口）

对外开放的 `/api/v1/agents/{id}/chat` 接口同时支持 JWT 和 API Key 两种认证：

```python
async def get_caller_identity(
    authorization: str = Header(None),
    x_api_key: str = Header(None, alias="X-Api-Key")
) -> CallerIdentity:
    if x_api_key:
        # API Key 认证路径
        api_key = await verify_api_key(x_api_key)
        return CallerIdentity(type="api_key", ...)
    elif authorization and authorization.startswith("Bearer "):
        # JWT 认证路径
        user = await get_current_user(authorization[7:])
        return CallerIdentity(type="user", ...)
    else:
        raise HTTPException(status_code=401, detail="需要认证")
```

---

## 4. 数据库访问层

### 4.1 异步 SQLAlchemy 配置

```python
# app/database.py

from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from app.config import settings

engine = create_async_engine(
    settings.DATABASE_URL,      # mysql+aiomysql://user:pass@host/db
    pool_size=20,
    max_overflow=10,
    pool_timeout=30,
    pool_recycle=3600,
    echo=settings.DEBUG
)

AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False
)

async def get_db() -> AsyncSession:
    async with AsyncSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
```

### 4.2 通用 Repository 基类

```python
# app/repositories/base.py

from typing import TypeVar, Generic, Type, Optional, List
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from app.models.base import BaseModel

ModelType = TypeVar("ModelType", bound=BaseModel)

class BaseRepository(Generic[ModelType]):
    def __init__(self, model: Type[ModelType], db: AsyncSession):
        self.model = model
        self.db = db

    async def get_by_id(self, id: str, tenant_id: str = None) -> Optional[ModelType]:
        stmt = select(self.model).where(
            self.model.id == id,
            self.model.is_deleted == False
        )
        if tenant_id:
            stmt = stmt.where(self.model.tenant_id == tenant_id)
        result = await self.db.execute(stmt)
        return result.scalar_one_or_none()

    async def list_with_pagination(
        self, tenant_id: str, page: int = 1, size: int = 20, **filters
    ) -> tuple[List[ModelType], int]:
        stmt = select(self.model).where(
            self.model.tenant_id == tenant_id,
            self.model.is_deleted == False
        )
        for key, value in filters.items():
            if value is not None:
                stmt = stmt.where(getattr(self.model, key) == value)

        count_stmt = select(func.count()).select_from(stmt.subquery())
        total = (await self.db.execute(count_stmt)).scalar()

        stmt = stmt.offset((page - 1) * size).limit(size)
        result = await self.db.execute(stmt)
        return result.scalars().all(), total

    async def create(self, **data) -> ModelType:
        obj = self.model(**data)
        self.db.add(obj)
        await self.db.flush()
        return obj

    async def update(self, id: str, **data) -> Optional[ModelType]:
        obj = await self.get_by_id(id)
        if not obj:
            return None
        for key, value in data.items():
            setattr(obj, key, value)
        await self.db.flush()
        return obj

    async def soft_delete(self, id: str) -> bool:
        from datetime import datetime
        obj = await self.get_by_id(id)
        if not obj:
            return False
        obj.is_deleted = True
        obj.deleted_at = datetime.utcnow()
        await self.db.flush()
        return True
```

---

## 5. Agent 执行引擎

### 5.1 ReAct 循环设计

```python
# app/core/agent/executor.py

class AgentExecutor:
    """
    ReAct (Reason + Act) 循环执行引擎
    每次循环：
      1. LLM 推理（决定下一步：回复用户 or 调用工具）
      2. 执行工具（如果 LLM 决定调用）
      3. 将工具结果注入上下文，继续推理
      4. 直到 LLM 决定直接回复用户（无工具调用）
    """

    async def run(
        self,
        agent_config: AgentConfig,
        messages: List[Message],
        stream: bool = False,
        trace_collector: TraceCollector = None
    ) -> AsyncGenerator[AgentEvent, None]:
        context = AgentContext(
            config=agent_config,
            messages=messages,
            step_count=0,
            max_steps=agent_config.behavior.max_steps
        )

        while context.step_count < context.max_steps:
            context.step_count += 1

            # Step 1: 注入知识库检索结果
            if agent_config.knowledge_bases:
                retrieval_result = await self._retrieve_knowledge(
                    context, agent_config.knowledge_bases
                )
                context.inject_knowledge(retrieval_result)

            # Step 2: 应用输入防护
            await self.input_guard.check(context.current_user_message)

            # Step 3: LLM 推理
            llm_response = await self._llm_call(context, stream, trace_collector)

            # Step 4: 判断是否需要工具调用
            if not llm_response.tool_calls:
                # 无工具调用，直接输出最终回复
                await self.output_guard.check(llm_response.content)
                yield AgentEvent(type="final_response", content=llm_response.content)
                break

            # Step 5: 执行工具调用（可能并行）
            tool_results = await self._execute_tools(
                llm_response.tool_calls, agent_config, trace_collector
            )

            # Step 6: 将工具结果追加到消息历史，继续循环
            context.add_tool_results(tool_results)

        else:
            # 超过最大步数
            yield AgentEvent(type="max_steps_exceeded")

    async def _execute_tools(
        self,
        tool_calls: List[ToolCall],
        agent_config: AgentConfig,
        trace_collector: TraceCollector
    ) -> List[ToolResult]:
        """
        工具调用执行，支持并行
        对绑定了 Skill 的调用，路由到 SkillExecutor
        对普通 Tool，路由到 ToolExecutor
        """
        tasks = []
        for call in tool_calls:
            # 检查是 Skill 还是 Tool
            if call.name in agent_config.skill_aliases:
                skill_id = agent_config.skill_aliases[call.name]
                task = self.skill_executor.invoke(skill_id, call.arguments)
            else:
                # 权限检查：工具必须在 Agent 绑定白名单内
                if call.name not in agent_config.allowed_tools:
                    task = asyncio.create_task(
                        asyncio.coroutine(lambda: ToolResult(
                            tool_name=call.name,
                            error="工具未授权"
                        ))()
                    )
                else:
                    task = self.tool_executor.execute(call.name, call.arguments)
            tasks.append(task)

        return await asyncio.gather(*tasks)
```

### 5.2 流式输出处理

```python
# app/api/v1/chat/router.py

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
import json

router = APIRouter()

@router.post("/agents/{agent_id}/chat")
async def chat(
    agent_id: str,
    request: ChatRequest,
    caller: CallerIdentity = Depends(get_caller_identity),
    db: AsyncSession = Depends(get_db)
):
    agent = await get_agent_or_404(agent_id, db)
    await check_rate_limit(agent_id, caller)

    if request.stream:
        return StreamingResponse(
            _stream_response(agent, request, caller),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "X-Accel-Buffering": "no"   # Nginx 不缓冲 SSE
            }
        )
    else:
        result = await chat_service.chat(agent, request, caller)
        return {"code": 0, "data": result}

async def _stream_response(agent, request, caller):
    """生成 SSE 事件流"""
    trace_collector = TraceCollector()

    async for event in agent_executor.run(
        agent_config=agent.config,
        messages=request.messages,
        stream=True,
        trace_collector=trace_collector
    ):
        yield f"data: {json.dumps(event.dict(), ensure_ascii=False)}\n\n"

    # 异步保存 Trace
    asyncio.create_task(trace_collector.save_async())
```

---

## 6. 异步任务处理

### 6.1 Celery 配置

```python
# app/workers/celery_app.py

from celery import Celery
from app.config import settings

celery_app = Celery(
    "agent_service",
    broker=settings.REDIS_URL,
    backend=settings.REDIS_URL,
    include=[
        "app.workers.embedding_worker",
        "app.workers.eval_worker",
        "app.workers.test_worker"
    ]
)

celery_app.conf.update(
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    task_track_started=True,
    task_acks_late=True,
    worker_prefetch_multiplier=1,
)
```

### 6.2 文档 Embedding 任务

```python
# app/workers/embedding_worker.py

@celery_app.task(bind=True, max_retries=3, default_retry_delay=60)
def embed_document_chunk(self, chunk_id: str):
    """异步对文档切片进行 Embedding"""
    import asyncio
    asyncio.run(_embed_chunk(chunk_id))

async def _embed_chunk(chunk_id: str):
    async with get_db_context() as db:
        chunk = await kb_repo.get_chunk(chunk_id, db)
        if not chunk:
            return

        # 获取知识库配置的 Embedding 模型
        kb = await kb_repo.get(chunk.kb_id, db)
        embedding = await embedder.embed(
            text=chunk.content,
            model=kb.embedding_model
        )

        # 写入向量数据库
        await qdrant_client.upsert(
            collection_name=f"kb_{chunk.kb_id}",
            points=[{
                "id": chunk_id,
                "vector": embedding,
                "payload": {
                    "chunk_id": chunk_id,
                    "document_id": chunk.document_id,
                    "content": chunk.content,
                    "metadata": chunk.metadata
                }
            }]
        )

        # 更新 MySQL 中的状态
        await kb_repo.update_chunk_status(chunk_id, "embedded", db)
```

### 6.3 批量测试任务

```python
# app/workers/test_worker.py

@celery_app.task(bind=True)
def run_test_suite(self, test_run_id: str):
    """执行批量测试，更新进度"""
    asyncio.run(_run_test_suite(test_run_id, self))

async def _run_test_suite(test_run_id: str, task):
    async with get_db_context() as db:
        test_run = await test_repo.get_run(test_run_id, db)
        test_cases = await test_repo.get_cases(test_run.suite_id, db)

        # 更新状态为运行中
        await test_repo.update_run(test_run_id, status="running", db=db)

        results = []
        for i, case in enumerate(test_cases):
            try:
                result = await execute_test_case(case, test_run.target_version)
                results.append(result)
            except Exception as e:
                results.append(TestCaseResult(case_id=case.id, status="error", error=str(e)))

            # 更新进度
            task.update_state(
                state="PROGRESS",
                meta={"completed": i + 1, "total": len(test_cases)}
            )

        # 汇总并保存结果
        report = generate_report(results)
        await test_repo.update_run(
            test_run_id, status="completed", report=report, db=db
        )
```

---

## 7. Trace 采集与发布

### 7.1 Trace 采集器

```python
# app/core/trace/collector.py

class TraceCollector:
    """
    在 Agent/Workflow 执行过程中收集 Trace 数据
    使用上下文管理器确保数据正确收集
    所有写操作在 Agent 执行完成后异步批量提交，不影响主链路性能
    """

    def __init__(self):
        self.session_span = None
        self.turn_span = None
        self.steps = []
        self._start_time = None

    def start_session(self, agent_id: str, tenant_id: str, channel: str, user_id: str = None):
        self.session_span = SessionSpan(
            id=uuid4_str(),
            agent_id=agent_id,
            tenant_id=tenant_id,
            channel=channel,
            user_id=user_id,
            started_at=now_ms()
        )

    def start_turn(self, user_message: str):
        self.turn_span = TurnSpan(
            id=uuid4_str(),
            session_id=self.session_span.id,
            user_message=pii_masker.mask(user_message),
            started_at=now_ms()
        )

    def record_llm_call(self, model_id: str, input_messages, output, tokens):
        self.steps.append(StepSpan(
            span_type="llm_call",
            run_id=self.current_run_id,
            step_index=len(self.steps),
            model_id=model_id,
            input=input_messages,
            output=output,
            input_tokens=tokens.prompt,
            output_tokens=tokens.completion,
            latency_ms=...,
        ))

    async def save_async(self):
        """异步批量写入，通过 Kafka 发布"""
        await kafka_producer.send("agent.trace.turns", self.turn_span.dict())
        for step in self.steps:
            await kafka_producer.send("agent.trace.steps", step.dict())
```

### 7.2 Kafka 生产者配置

```python
# app/core/trace/publisher.py

from aiokafka import AIOKafkaProducer
import json

class KafkaTracePublisher:
    def __init__(self):
        self._producer = None

    async def start(self):
        self._producer = AIOKafkaProducer(
            bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
            value_serializer=lambda v: json.dumps(v).encode("utf-8")
        )
        await self._producer.start()

    async def send(self, topic: str, data: dict):
        if settings.KAFKA_ENABLED:
            await self._producer.send_and_wait(topic, data)
        else:
            # 非生产环境直接写 ES（方便本地开发）
            await es_client.index(index=topic, document=data)
```

---

## 8. 配置管理

### 8.1 环境变量配置

```python
# app/config.py

from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    # 服务配置
    APP_NAME: str = "agent-service"
    DEBUG: bool = False
    HOST: str = "0.0.0.0"
    PORT: int = 8090

    # 数据库
    DATABASE_URL: str  # mysql+aiomysql://user:pass@localhost/agent_db
    REDIS_URL: str     # redis://localhost:6379/0

    # JWT（与 admin-service 共用）
    JWT_SECRET: str
    JWT_ALGORITHM: str = "HS256"

    # 加密
    ENCRYPTION_KEY: str   # AES-256-GCM 密钥（32 字节，Base64 编码）

    # Elasticsearch
    ES_HOST: str = "localhost"
    ES_PORT: int = 9200

    # Kafka
    KAFKA_ENABLED: bool = True
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"

    # 向量数据库
    QDRANT_HOST: str = "localhost"
    QDRANT_PORT: int = 6333

    # Celery
    CELERY_BROKER_URL: str  # 通常与 REDIS_URL 相同

    # 平台级安全
    PROMPT_INJECTION_DETECTION: bool = True
    PII_MASKING: bool = True

    class Config:
        env_file = ".env"

settings = Settings()
```

### 8.2 配置热更新

部分运行时配置（告警阈值、限流参数、安全规则）存储在 MySQL 中，支持不重启更新：

```python
# app/services/config_service.py

class RuntimeConfigService:
    """运行时可热更新的配置，从数据库加载，Redis 缓存"""

    async def get_rate_limit_config(self, agent_id: str) -> RateLimitConfig:
        cache_key = f"rate_limit:{agent_id}"
        cached = await redis.get(cache_key)
        if cached:
            return RateLimitConfig.parse_raw(cached)

        config = await db_config_repo.get_agent_config(agent_id, "rate_limit")
        await redis.setex(cache_key, 300, config.json())  # 5 分钟缓存
        return config
```

---

## 9. 限流实现

### 9.1 滑动窗口限流（Redis）

```python
# app/middleware/rate_limiter.py

import time
import redis.asyncio as aioredis

class RateLimiter:
    def __init__(self, redis_client):
        self.redis = redis_client

    async def check_and_increment(
        self,
        key: str,
        limit: int,
        window_seconds: int
    ) -> bool:
        """滑动窗口限流，返回 True 表示允许，False 表示超限"""
        now = time.time()
        window_start = now - window_seconds

        pipe = self.redis.pipeline()
        pipe.zremrangebyscore(key, 0, window_start)  # 清理过期记录
        pipe.zadd(key, {str(now): now})               # 添加当前请求
        pipe.zcard(key)                                # 统计窗口内请求数
        pipe.expire(key, window_seconds + 1)
        results = await pipe.execute()

        current_count = results[2]
        return current_count <= limit

    async def check_rate_limit(
        self,
        tenant_id: str,
        agent_id: str,
        user_id: str = None
    ):
        limits = await config_service.get_rate_limit_config(agent_id)

        # 检查 Agent 级 RPM
        agent_key = f"rl:agent:{agent_id}:rpm"
        if not await self.check_and_increment(agent_key, limits.agent_rpm, 60):
            raise RateLimitExceeded(f"Agent 调用频率超限")

        # 检查单用户 RPM
        if user_id:
            user_key = f"rl:agent:{agent_id}:user:{user_id}:rpm"
            if not await self.check_and_increment(user_key, limits.per_user_rpm, 60):
                raise RateLimitExceeded("您的请求过于频繁，请稍后再试")

        # 检查租户级 TPM（Token Per Minute）
        # Token 消耗在请求完成后异步更新，这里只做请求数检查
        tenant_key = f"rl:tenant:{tenant_id}:rpm"
        if not await self.check_and_increment(tenant_key, limits.tenant_rpm, 60):
            raise RateLimitExceeded("租户调用频率超限")
```

---

## 10. 错误处理

### 10.1 统一异常处理

```python
# app/exceptions.py

from fastapi import Request
from fastapi.responses import JSONResponse

class AgentServiceException(Exception):
    def __init__(self, code: int, message: str, status_code: int = 400):
        self.code = code
        self.message = message
        self.status_code = status_code

class ResourceNotFoundException(AgentServiceException):
    def __init__(self, resource: str, id: str):
        super().__init__(40004, f"{resource} 不存在：{id}", 404)

class RateLimitExceeded(AgentServiceException):
    def __init__(self, message: str):
        super().__init__(50007, message, 429)

class PublishValidationError(AgentServiceException):
    def __init__(self, errors: list):
        super().__init__(50002, "发布前检查未通过", 400)
        self.errors = errors

# 全局异常处理器
async def agent_exception_handler(request: Request, exc: AgentServiceException):
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "code": exc.code,
            "message": exc.message
        }
    )
```

---

## 11. 健康检查与运维

### 11.1 健康检查端点

```python
@router.get("/health")
async def health_check(db: AsyncSession = Depends(get_db)):
    checks = {}

    # MySQL
    try:
        await db.execute(text("SELECT 1"))
        checks["mysql"] = "ok"
    except Exception as e:
        checks["mysql"] = f"error: {e}"

    # Redis
    try:
        await redis_client.ping()
        checks["redis"] = "ok"
    except Exception as e:
        checks["redis"] = f"error: {e}"

    # Qdrant
    try:
        await qdrant_client.get_collections()
        checks["qdrant"] = "ok"
    except Exception as e:
        checks["qdrant"] = f"error: {e}"

    all_ok = all(v == "ok" for v in checks.values())
    return JSONResponse(
        status_code=200 if all_ok else 503,
        content={"status": "healthy" if all_ok else "degraded", "checks": checks}
    )
```

### 11.2 启动与关闭

```python
# main.py

from fastapi import FastAPI
from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动：初始化连接池
    await kafka_publisher.start()
    await qdrant_client.connect()
    await redis_client.connect()
    yield
    # 关闭：优雅释放资源
    await kafka_publisher.stop()
    await engine.dispose()

app = FastAPI(
    title="Agent Service",
    version="1.0.0",
    lifespan=lifespan
)
```

---

## 12. 部署配置

### 12.1 Docker 镜像

```dockerfile
FROM python:3.12-slim

WORKDIR /app

# 安装系统依赖（PDF 处理等需要）
RUN apt-get update && apt-get install -y \
    libmagic1 poppler-utils tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml uv.lock ./
RUN pip install uv && uv sync --frozen --no-dev

COPY . .

EXPOSE 8090

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "4"]
```

### 12.2 docker-compose 配置片段

```yaml
# infrastructure/docker-compose.yml（新增）

agent-service:
  build: ./services/agent-service
  ports:
    - "8090:8090"
  environment:
    DATABASE_URL: mysql+aiomysql://root:password@mysql:3306/agent_db
    REDIS_URL: redis://redis:6379/1
    JWT_SECRET: ${JWT_SECRET}
    ENCRYPTION_KEY: ${AGENT_ENCRYPTION_KEY}
    ES_HOST: elasticsearch
    KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    QDRANT_HOST: qdrant
  depends_on:
    - mysql
    - redis
    - elasticsearch
    - kafka
    - qdrant

qdrant:
  image: qdrant/qdrant:v1.9.0
  ports:
    - "6333:6333"
  volumes:
    - qdrant_data:/qdrant/storage

celery-worker:
  build: ./services/agent-service
  command: celery -A app.workers.celery_app worker --loglevel=info --concurrency=4
  environment:
    # 与 agent-service 共用相同环境变量
    ...
  depends_on:
    - redis
    - mysql
```

---

## 13. 性能优化策略

| 优化点 | 方案 |
|--------|------|
| LLM 调用延迟 | SSE 流式输出，降低用户感知延迟 |
| 数据库查询 | 联合索引 + 查询缓存（Redis，5 分钟 TTL） |
| Trace 写入 | 异步 Kafka 解耦，不阻塞主链路 |
| Embedding 计算 | Celery 异步任务，批量处理 |
| 知识库检索 | Qdrant HNSW 向量索引，毫秒级响应 |
| 并发处理 | asyncio 全链路异步，单进程高并发 |
| 连接池 | SQLAlchemy pool_size=20，Redis 连接池 |

---

*文档版本：v1.0 | 最后更新：2026-03-18*
