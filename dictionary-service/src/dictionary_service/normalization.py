"""Детерминированная нормализация названий навыков.

Порядок важен и закреплён тестами:

1. NFKC, схлопывание пробелов, lower, снятие краевой пунктуации и
   завершающей точки; ``ё -> е``.
2. Свёртка гомоглифов: токен меняется, только если в нём есть ASCII-сигнал
   (латиница, цифра, ``+`` или ``#``) и кириллический двойник.
3. Лемматизация кириллических токенов (pymorphy3), если библиотека доступна.

Свёрнутый ключ используется только для точного поиска по алиасу. Поэтому
слово «сок» не превращается в «cok»: алиаса «cok» нет.
"""

from __future__ import annotations

import re
import unicodedata
from functools import lru_cache
from typing import Literal

EDGE_CHARS = ",;:!?\"'()[]«»"
_WHITESPACE = re.compile(r"[\s\u00A0\u202F]+")

# Кириллические символы, визуально совпадающие с латинскими.
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
_HOMO_TRANSLATION = str.maketrans(_HOMOGLYPHS)

KeyKind = Literal["RAW", "FOLDED", "LEMMA", "LEMMA_FOLDED"]


def normalize_base(raw: str | None) -> str:
    """Нормализованный ключ без лемматизации и без свёртки гомоглифов."""
    if raw is None:
        return ""
    value = unicodedata.normalize("NFKC", raw)
    value = _WHITESPACE.sub(" ", value).strip()
    value = value.lower().replace("ё", "е")
    value = value.strip(EDGE_CHARS)
    if value.endswith("."):
        value = value[:-1]
    return value.strip(EDGE_CHARS)


def _token_has_ascii_signal(token: str) -> bool:
    return any(char.isascii() and (char.isalnum() or char in "+#") for char in token)


def _token_has_homoglyph(token: str) -> bool:
    return any(char.lower() in _HOMOGLYPHS for char in token)


def _fold_token(token: str) -> str:
    if not _token_has_ascii_signal(token) or not _token_has_homoglyph(token):
        return token
    return token.lower().translate(_HOMO_TRANSLATION)


def fold_homoglyphs(value: str) -> str:
    """Свернуть кириллические двойники, не трогая чисто кириллические слова."""
    if not value:
        return value
    return " ".join(_fold_token(token) for token in value.split(" "))


@lru_cache(maxsize=1)
def _morph():
    try:
        import pymorphy3
    except ImportError:  # pragma: no cover - зависит от окружения
        return None
    return pymorphy3.MorphAnalyzer()


def pymorphy_version() -> str:
    try:
        import pymorphy3
    except ImportError:  # pragma: no cover - зависит от окружения
        return "none"
    return getattr(pymorphy3, "__version__", "unknown")


def lemmatize(value: str) -> str:
    """Лемматизировать только полностью кириллические токены."""
    if not value:
        return value
    morph = _morph()
    if morph is None:
        return value

    def lemma_token(token: str) -> str:
        if not re.fullmatch(r"[А-Яа-яЁё]+", token):
            return token
        try:
            return morph.parse(token)[0].normal_form
        except (IndexError, ValueError):  # pragma: no cover - защита от словаря
            return token

    return normalize_base(" ".join(lemma_token(token) for token in value.split(" ")))


def keys_for_raw(raw: str | None) -> tuple[str, str, str, str]:
    """Ключи одного написания в порядке приоритета: RAW, FOLDED, LEMMA, LEMMA_FOLDED."""
    raw_key = normalize_base(raw)
    if not raw_key:
        return "", "", "", ""
    folded = fold_homoglyphs(raw_key)
    lemma = lemmatize(raw_key)
    lemma_folded = fold_homoglyphs(lemma)
    return raw_key, folded, lemma, lemma_folded


def alias_keys(raw: str, lemma_mode: str = "AUTO") -> dict[KeyKind, str]:
    """Ключи, под которыми алиас сохраняется в словаре."""
    raw_key, folded, lemma, lemma_folded = keys_for_raw(raw)
    if not raw_key:
        return {}
    result: dict[KeyKind, str] = {"RAW": raw_key}
    if folded != raw_key:
        result["FOLDED"] = folded
    if lemma_mode != "RAW_ONLY" and lemma != raw_key:
        result["LEMMA"] = lemma
        if lemma_folded != lemma:
            result["LEMMA_FOLDED"] = lemma_folded
    return result
