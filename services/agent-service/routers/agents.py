from fastapi import APIRouter, HTTPException, Depends
from typing import List
from schemas import AgentCreate, AgentUpdate, AgentResponse
import uuid
from datetime import datetime

router = APIRouter(prefix="/agents", tags=["智能体管理"])

agents_db = {}

@router.post("", response_model=AgentResponse)
async def create_agent(agent: AgentCreate):
    agent_id = str(uuid.uuid4())
    now = datetime.now()
    agent_data = {
        "id": agent_id,
        "name": agent.name,
        "description": agent.description,
        "model": agent.model,
        "system_prompt": agent.system_prompt,
        "temperature": agent.temperature,
        "created_at": now,
        "updated_at": now
    }
    agents_db[agent_id] = agent_data
    return AgentResponse(**agent_data)

@router.get("", response_model=List[AgentResponse])
async def list_agents():
    return list(agents_db.values())

@router.get("/{agent_id}", response_model=AgentResponse)
async def get_agent(agent_id: str):
    if agent_id not in agents_db:
        raise HTTPException(status_code=404, detail="智能体不存在")
    return AgentResponse(**agents_db[agent_id])

@router.put("/{agent_id}", response_model=AgentResponse)
async def update_agent(agent_id: str, agent: AgentUpdate):
    if agent_id not in agents_db:
        raise HTTPException(status_code=404, detail="智能体不存在")
    
    existing = agents_db[agent_id]
    update_data = agent.model_dump(exclude_unset=True)
    existing.update(update_data)
    existing["updated_at"] = datetime.now()
    
    return AgentResponse(**existing)

@router.delete("/{agent_id}")
async def delete_agent(agent_id: str):
    if agent_id not in agents_db:
        raise HTTPException(status_code=404, detail="智能体不存在")
    del agents_db[agent_id]
    return {"message": "删除成功"}
