from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from config import settings

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="智能体管理服务"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
async def health_check():
    return {"status": "ok", "service": settings.app_name}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8090)
