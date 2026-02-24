from fastapi import APIRouter, Depends, HTTPException
from schemas import ChatRequest, ChatResponse
from typing import AsyncGenerator
import json
from datetime import datetime

router = APIRouter(prefix="/chat", tags=["对话交互"])

@router.post("")
async def chat(request: ChatRequest):
    response_text = f"这是来自智能体 {request.agent_id} 的回复: {request.message}"
    
    return ChatResponse(
        response=response_text,
        agent_id=request.agent_id,
        timestamp=datetime.now()
    )

@router.post("/stream")
async def chat_stream(request: ChatRequest):
    async def generate():
        response = f"这是来自智能体 {request.agent_id} 的流式回复: {request.message}"
        for char in response:
            yield f"data: {json.dumps({'content': char})}\n\n"
    
    return generate()
