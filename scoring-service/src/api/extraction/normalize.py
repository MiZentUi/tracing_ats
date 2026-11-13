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
    normalized = _WHITESPACE.sub(" ", normalized).strip().lower().replace("ё", "е")
    normalized = normalized.strip(_EDGE_PUNCTUATION)
    if normalized.endswith("."):
        normalized = normalized[:-1]
    return normalized.strip(_EDGE_PUNCTUATION)


_HOMOGLYPHS = {
    "а": "a",
    "в": "b",
    "е": "e",
    "к": "k",
    "м": "m",
    "н": "h",
    "о": "o",
    "р": "p",
    "с": "c",
    "т": "t",
    "у": "y",
    "х": "x",
}


def _has_ascii_signal(token: str) -> bool:
    return any(char.isascii() and (char.isalnum() or char in "+#") for char in token)


def _fold_token(token: str) -> str:
    if not _has_ascii_signal(token) or not any(char.lower() in _HOMOGLYPHS for char in token):
        return token
    return token.lower().translate(str.maketrans(_HOMOGLYPHS))


def fold_homoglyphs(value: str) -> str:
    if not value:
        return value
    return " ".join(_fold_token(token) for token in value.split(" "))


def candidate_keys(name: str | None) -> tuple[str, ...]:
    """Ключи fallback-режима: нормализация и свёрнутые гомоглифы, без лемм."""
    key = normalize_key(name or "")
    if not key:
        return ()
    folded = fold_homoglyphs(key)
    return (key,) if folded == key else (key, folded)
