import os
from typing import Optional
from pydantic import BaseModel
from dotenv import load_dotenv

load_dotenv()

class ServerConfig(BaseModel):
    HOST: str = os.getenv("JARVIS_HOST", "0.0.0.0")
    PORT: int = int(os.getenv("JARVIS_PORT", "8000"))
    AUTH_TOKEN: str = os.getenv("JARVIS_AUTH_TOKEN", "")
    DEFAULT_PROVIDER: str = os.getenv("JARVIS_DEFAULT_PROVIDER", "groq") # 'groq', 'gemini', 'ollama', 'openrouter'
    
    # API Keys & Endpoints
    GROQ_API_KEY: str = os.getenv("GROQ_API_KEY", "")
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "")
    OPENROUTER_API_KEY: str = os.getenv("OPENROUTER_API_KEY", "")
    OLLAMA_BASE_URL: str = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    OLLAMA_MODEL: str = os.getenv("OLLAMA_MODEL", "llama3.2")
    DEFAULT_MODEL: str = os.getenv("DEFAULT_MODEL", "llama3-70b-8192")

settings = ServerConfig()
