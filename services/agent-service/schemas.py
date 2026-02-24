from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime

class AgentCreate(BaseModel):
    name: str = Field(..., description="智能体名称")
    description: Optional[str] = Field(None, description="智能体描述")
    model: str = Field(..., description="使用的模型")
    system_prompt: Optional[str] = Field(None, description="系统提示词")
    temperature: float = Field(0.7, ge=0.0, le=2.0, description="温度参数")

class AgentUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    model: Optional[str] = None
    system_prompt: Optional[str] = None
    temperature: Optional[float] = Field(None, ge=0.0, le=2.0)

class AgentResponse(BaseModel):
    id: str
    name: str
    description: Optional[str]
    model: str
    system_prompt: Optional[str]
    temperature: float
    created_at: datetime
    updated_at: datetime

class ChatRequest(BaseModel):
    agent_id: str = Field(..., description="智能体ID")
    message: str = Field(..., description="用户消息")
    user_id: str = Field(..., description="用户ID")

class ChatResponse(BaseModel):
    response: str
    agent_id: str
    timestamp: datetime
