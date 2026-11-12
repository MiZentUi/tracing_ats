"""Нормализация ключа навыка — побайтово как Java `SkillNormalizer`.

Порядок: NFKC → схлопывание пробелов → trim → lower → снятие краевой
пунктуации, кроме `+`, `#` и ведущей точки. Совпадение ключей важно: иначе
`Postgres`, `PostgreSQL` и `JS,` разойдутся между Java и Python.
"""

from __future__ import annotations

import re
import unicodedata

_WHITESPACE = re.compile(r"[\s\u00A0\u202F]+")
_EDGE_PUNCTUATION = ",;:!?\"'()[]«»"


def normalize_key(name: str) -> str:
    normalized = unicodedata.normalize("NFKC", name)
    normalized = _WHITESPACE.sub(" ", normalized).strip().lower()
    normalized = normalized.strip(_EDGE_PUNCTUATION)
    if normalized.endswith("."):
        normalized = normalized[:-1]
    return normalized.strip(_EDGE_PUNCTUATION)
