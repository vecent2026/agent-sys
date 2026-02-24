from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    app_name: str = "agent-service"
    app_version: str = "1.0.0"
    
    kafka_bootstrap_servers: str = "localhost:9092"
    
    jwt_secret_key: str = "your-secret-key-here"
    jwt_algorithm: str = "HS256"
    
    class Config:
        env_file = ".env"

settings = Settings()
