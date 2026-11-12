"""Детерминированное сходство признаков и текста.

`S^{ij}` не вызывает LLM. Для структурированных признаков используются
четыре категории из `docs/rfc/rfc-feature-extraction.md`:
exact = 1.0, strong = 0.85, partial = 0.5, none = 0.0. Точное совпадение
нормализованных имён всегда даёт 1.0 (`exact-override`).
"""

from __future__ import annotations

import re
from difflib import SequenceMatcher
from typing import Protocol

from ..extraction.normalize import normalize_key

_TOKEN = re.compile(r"[a-zа-я0-9+#.]+", re.IGNORECASE)
_STOPWORDS = frozenset(
    {
        "а",
        "без",
        "в",
        "во",
        "для",
        "до",
        "и",
        "из",
        "или",
        "к",
        "как",
        "на",
        "не",
        "но",
        "о",
        "от",
        "по",
        "с",
        "со",
        "у",
        "что",
        "a",
        "an",
        "and",
        "as",
        "at",
        "by",
        "for",
        "from",
        "in",
        "is",
        "of",
        "on",
        "or",
        "the",
        "to",
        "with",
    }
)


class SimilarityScorer(Protocol):
    """Контракт сходства, чтобы матрицу можно было заменить без правок ядра."""

    def score(self, left: str, right: str) -> float: ...

    def text_score(self, requirement: str, text: str) -> float: ...


def tokenize(text: str) -> set[str]:
    """Токены для текстового сходства без стоп-слов."""
    return {
        token.lower()
        for token in _TOKEN.findall(text)
        if len(token) > 1 and token.lower() not in _STOPWORDS
    }


class DeterministicSimilarity:
    """Сходство имён признаков без сети, эмбеддингов и случайности."""

    EXACT = 1.0
    STRONG = 0.85
    PARTIAL = 0.5
    NONE = 0.0

    def score(self, left: str, right: str) -> float:
        first = normalize_key(left)
        second = normalize_key(right)
        if not first or not second:
            return self.NONE
        if first == second:
            return self.EXACT

        first_tokens = tokenize(first)
        second_tokens = tokenize(second)
        if first_tokens and second_tokens:
            shorter, longer = (
                (first_tokens, second_tokens)
                if len(first_tokens) <= len(second_tokens)
                else (second_tokens, first_tokens)
            )
            if shorter < longer:
                return self.STRONG

        ratio = SequenceMatcher(None, first, second).ratio()
        if ratio >= 0.8:
            return self.STRONG
        if ratio >= 0.7:
            return self.PARTIAL
        return self.NONE

    def text_score(self, requirement: str, text: str) -> float:
        """Доля значимых токенов требования, найденных в тексте."""
        required = tokenize(requirement)
        if not required:
            return self.NONE
        found = tokenize(text)
        if not found:
            return self.NONE
        return len(required & found) / len(required)
