"""Заглушка `S^{ij}`: детерминированная псевдослучайная матрица.

Здесь намеренно нет модели сходства. Матрица нужна, чтобы пайплайн и контракт
работали до появления формулы из `docs/rfc/rfc-scoring-model.md`. Seed — SHA-256
текста резюме, а не встроенный `hash()`, иначе результат зависел бы от процесса.
"""

from __future__ import annotations

import hashlib
from collections.abc import Sequence

_SCALE = 1 << 64


def resume_seed(text: str) -> int:
    """Стабильный seed из текста резюме."""
    return int.from_bytes(hashlib.sha256(text.encode("utf-8")).digest()[:8], "big")


def similarity(seed: int, requirement_key: str, feature_key: str) -> float:
    """Псевдослучайное `S^{ij}` в `[0, 1)`; порядок пар не важен."""
    digest = hashlib.sha256(f"{seed}:{requirement_key}:{feature_key}".encode()).digest()
    return int.from_bytes(digest[:8], "big") / _SCALE


def similarity_matrix(
    seed: int,
    requirement_keys: Sequence[str],
    feature_keys: Sequence[str],
) -> dict[tuple[str, str], float]:
    """Материализовать заглушку `S` для требований × признаков."""
    return {
        (requirement_key, feature_key): similarity(seed, requirement_key, feature_key)
        for requirement_key in requirement_keys
        for feature_key in feature_keys
    }


def best_match[T](
    seed: int,
    requirement_key: str,
    features: Sequence[tuple[str, T]],
) -> tuple[T, float] | None:
    """Вернуть признак с максимальным `S^{ij}` или `None`, если признаков нет."""
    best_value: T | None = None
    best_score = -1.0

    for feature_key, value in features:
        score = similarity(seed, requirement_key, feature_key)
        if score > best_score:
            best_value = value
            best_score = score

    if best_value is None:
        return None
    return best_value, best_score
