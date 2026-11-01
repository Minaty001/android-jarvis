import httpx
import json
import logging
from typing import List, Dict, Any, AsyncGenerator, Optional
from app.config import settings

logger = logging.getLogger("LlmRouter")

class LlmRouter:
    def __init__(self):
        self.groq_key = settings.GROQ_API_KEY
        self.gemini_key = settings.GEMINI_API_KEY
        self.openrouter_key = settings.OPENROUTER_API_KEY
        self.ollama_base = settings.OLLAMA_BASE_URL
        self.ollama_model = settings.OLLAMA_MODEL

    async def chat_stream(
        self,
        messages: List[Dict[str, str]],
        provider: Optional[str] = None,
        client_api_key: Optional[str] = None
    ) -> AsyncGenerator[str, None]:
        prov = (provider or settings.DEFAULT_PROVIDER).lower()
        effective_groq = client_api_key or self.groq_key
        effective_openrouter = client_api_key or self.openrouter_key

        if prov == "ollama":
            async for token in self._stream_ollama(messages):
                yield token
        elif prov == "groq" and effective_groq:
            async for token in self._stream_openai_compatible("https://api.groq.com/openai/v1", effective_groq, settings.DEFAULT_MODEL, messages):
                yield token
        elif prov == "gemini" and self.gemini_key:
            async for token in self._stream_gemini(messages):
                yield token
        elif prov == "openrouter" and effective_openrouter:
            async for token in self._stream_openai_compatible("https://openrouter.ai/api/v1", effective_openrouter, "meta-llama/llama-3.1-8b-instruct", messages):
                yield token
        elif effective_groq:
            async for token in self._stream_openai_compatible("https://api.groq.com/openai/v1", effective_groq, settings.DEFAULT_MODEL, messages):
                yield token
        else:
            # Fallback deterministic token emission if no cloud keys or ollama is active
            text = "JARVIS Brain online. Ready to execute your commands."
            for word in text.split(" "):
                yield word + " "

    async def _stream_openai_compatible(
        self,
        base_url: str,
        api_key: str,
        model: str,
        messages: List[Dict[str, str]]
    ) -> AsyncGenerator[str, None]:
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": model,
            "messages": messages,
            "stream": True,
            "temperature": 0.3
        }
        async with httpx.AsyncClient(timeout=30.0) as client:
            async with client.stream("POST", f"{base_url}/chat/completions", headers=headers, json=payload) as response:
                if response.status_code != 200:
                    yield f"[Error: provider returned {response.status_code}]"
                    return
                async for line in response.aiter_lines():
                    if line.startswith("data: "):
                        data_str = line[6:].strip()
                        if data_str == "[DONE]":
                            break
                        try:
                            chunk = json.loads(data_str)
                            delta = chunk["choices"][0]["delta"]
                            if "content" in delta and delta["content"]:
                                yield delta["content"]
                        except Exception:
                            continue

    async def _stream_gemini(self, messages: List[Dict[str, str]]) -> AsyncGenerator[str, None]:
        # Formats messages for Gemini REST API
        contents = []
        for m in messages:
            role = "user" if m["role"] == "user" else "model"
            contents.append({"role": role, "parts": [{"text": m["content"]}]})
        
        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?key={self.gemini_key}"
        payload = {"contents": contents}
        async with httpx.AsyncClient(timeout=30.0) as client:
            async with client.stream("POST", url, json=payload) as response:
                if response.status_code != 200:
                    yield f"[Gemini Error: {response.status_code}]"
                    return
                async for line in response.aiter_lines():
                    if line.strip():
                        try:
                            chunk = json.loads(line)
                            candidates = chunk.get("candidates", [])
                            if candidates:
                                parts = candidates[0].get("content", {}).get("parts", [])
                                for p in parts:
                                    if "text" in p:
                                        yield p["text"]
                        except Exception:
                            continue

    async def _stream_ollama(self, messages: List[Dict[str, str]]) -> AsyncGenerator[str, None]:
        url = f"{self.ollama_base}/api/chat"
        payload = {
            "model": self.ollama_model,
            "messages": messages,
            "stream": True
        }
        async with httpx.AsyncClient(timeout=30.0) as client:
            try:
                async with client.stream("POST", url, json=payload) as response:
                    async for line in response.aiter_lines():
                        if line.strip():
                            chunk = json.loads(line)
                            if "message" in chunk and "content" in chunk["message"]:
                                yield chunk["message"]["content"]
            except Exception as e:
                logger.warning(f"Ollama stream failed: {e}")
                yield f"[Ollama offline: {e}]"
