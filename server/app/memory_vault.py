import sqlite3
import os
import time
from typing import List, Dict, Optional, Any

class MemoryVault:
    def __init__(self, db_path: str = "server_memory.db"):
        self.db_path = db_path
        self._init_db()

    def _get_connection(self):
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self):
        with self._get_connection() as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS conversation_turns (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    timestamp REAL NOT NULL
                )
            """)
            conn.execute("""
                CREATE TABLE IF NOT EXISTS facts (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    updated_at REAL NOT NULL
                )
            """)
            conn.commit()

    def remember_turn(self, session_id: str, role: str, content: str):
        with self._get_connection() as conn:
            conn.execute(
                "INSERT INTO conversation_turns (session_id, role, content, timestamp) VALUES (?, ?, ?, ?)",
                (session_id, role, content, time.time())
            )
            conn.commit()

    def get_history(self, session_id: str, limit: int = 10) -> List[Dict[str, str]]:
        with self._get_connection() as conn:
            cursor = conn.execute(
                "SELECT role, content FROM conversation_turns WHERE session_id = ? ORDER BY id ASC LIMIT ?",
                (session_id, limit)
            )
            return [{"role": row["role"], "content": row["content"]} for row in cursor.fetchall()]

    def set_fact(self, key: str, value: str):
        with self._get_connection() as conn:
            conn.execute(
                "INSERT OR REPLACE INTO facts (key, value, updated_at) VALUES (?, ?, ?)",
                (key, value, time.time())
            )
            conn.commit()

    def get_fact(self, key: str) -> Optional[str]:
        with self._get_connection() as conn:
            cursor = conn.execute("SELECT value FROM facts WHERE key = ?", (key,))
            row = cursor.fetchone()
            return row["value"] if row else None

    def list_all_facts(self) -> Dict[str, str]:
        with self._get_connection() as conn:
            cursor = conn.execute("SELECT key, value FROM facts")
            return {row["key"]: row["value"] for row in cursor.fetchall()}
