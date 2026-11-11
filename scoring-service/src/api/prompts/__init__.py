"""Промпты scoring-service. Только код, без чтения файлов в рантайме."""

from .resume_extraction import PROMPT_VERSION, RETRY_HINT, system_prompt, user_prompt

__all__ = ["PROMPT_VERSION", "RETRY_HINT", "system_prompt", "user_prompt"]
