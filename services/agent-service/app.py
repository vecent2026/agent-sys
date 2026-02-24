from fastapi import FastAPI
from routers import agents, chat

app = FastAPI(
    title="Agent Service",
    version="1.0.0",
    description="智能体管理服务"
)

app.include_router(agents.router)
app.include_router(chat.router)

@app.get("/health")
async def health_check():
    return {"status": "ok", "service": "agent-service"}
