"""Проверка цитат как подстрок с сохранением offsets.

Схлопывание пробельных последовательностей повторяет `QuoteMatcher` из Java:
`[\\s\\u00A0\\u202F]+` → один пробел. Offsets всегда считаются по исходному
обезличенному тексту, а не по нормализованной копии.
"""

from __future__ import annotations

import re

_WHITESPACE = re.compile(r"[\s\u00A0\u202F]+")


def collapse(text: str) -> tuple[str, list[int]]:
    """Вернуть схлопнутый текст и карту «индекс схлопнутого → индекс исходного»."""
    collapsed: list[str] = []
    index_map: list[int] = []
    position = 0
    length = len(text)

    while position < length:
        match = _WHITESPACE.match(text, position)
        if match is not None:
            collapsed.append(" ")
            index_map.append(position)
            position = match.end()
            continue

        collapsed.append(text[position])
        index_map.append(position)
        position += 1

    return "".join(collapsed), index_map


class QuoteIndex:
    """Поиск цитаты в исходном тексте после схлопывания пробелов."""

    def __init__(self, text: str) -> None:
        self._text = text
        self._collapsed, self._index_map = collapse(text)

    @property
    def text(self) -> str:
        return self._text

    def locate(self, quote: str) -> tuple[int, int] | None:
        """Вернуть `[start, end)` в исходном тексте или `None`, если цитаты нет."""
        needle, _ = collapse(quote.strip())
        needle = needle.strip()
        if not needle:
            return None

        position = self._collapsed.find(needle)
        if position < 0:
            return None

        start = self._index_map[position]
        end = self._index_map[position + len(needle) - 1] + 1
        return start, end

    def slice(self, start: int, end: int) -> str:
        """Исходный текст в границах — канонический вид доказательства."""
        return self._text[start:end]
