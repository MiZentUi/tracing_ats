"""spaCy-слой: именованные сущности из локальной модели.

Слой не ходит в сеть и не логирует текст. Маппинг намеренно узкий: маскируются
только имена и локации. Организации не маскируются — это решение проекта.

spaCy плохо распознаёт ФИО, записанные капсом (типичный заголовок резюме).
Поэтому первая непустая строка, если она целиком капсом, дополнительно
прогоняется через модель в title case. Регистр меняется без изменения длины,
поэтому offsets остаются координатами исходного текста. Результат принимается,
только если сущность — имя и покрывает минимум два слова: так заголовки вроде
«ОПЫТ РАБОТЫ» не превращаются в ложное PII.
"""

from __future__ import annotations

import re
from typing import Any, Protocol

from .base import EntitySpan, EntityType, MaskLayer

# Метки spaCy для русского и английского пайплайнов.
_LABEL_TO_TYPE: dict[str, EntityType] = {
    "PER": EntityType.NAME,
    "PERSON": EntityType.NAME,
    "LOC": EntityType.LOCATION,
    "GPE": EntityType.LOCATION,
    "FAC": EntityType.LOCATION,
    "LOCATION": EntityType.LOCATION,
}

_NAME_LABELS = {"PER", "PERSON"}

# Слово целиком капсом: кириллица или латиница, допускаем дефис.
_CAPS_WORD = re.compile(r"^[А-ЯЁA-Z][А-ЯЁA-Z-]+$")
_WORD = re.compile(r"\S+")
_PUNCTUATION = ".,;:!?()[]{}\"'«»—–-"

# Частые заголовки разделов резюме. Нужны только для того, чтобы не принять
# заголовок капсом за ФИО после нормализации регистра.
_SECTION_HEADINGS = {
    "опыт",
    "работы",
    "образование",
    "навыки",
    "контакты",
    "резюме",
    "проекты",
    "достижения",
    "себе",
    "языки",
    "сертификаты",
    "курсы",
    "интересы",
    "дополнительно",
    "цель",
    "должность",
}


class Nlp(Protocol):
    """Минимальный контракт spaCy-пайплайна: нужен только вызов `nlp(text)`."""

    def __call__(self, text: str) -> Any: ...


class SpacyLayer:
    """Детерминированный NER-слой поверх spaCy."""

    name = MaskLayer.SPACY

    def __init__(
        self,
        nlp: Nlp,
        model_name: str,
        confidence: float = 0.7,
        model_version: str = "",
        normalize_first_line_case: bool = True,
        min_caps_name_words: int = 2,
    ) -> None:
        self._nlp = nlp
        self.model_name = model_name
        self.model_version = model_version
        self._confidence = confidence
        self._normalize_first_line_case = normalize_first_line_case
        self._min_caps_name_words = max(2, min_caps_name_words)

    def detect(self, text: str, min_confidence: float = 0.0) -> list[EntitySpan]:
        if self._confidence < min_confidence:
            return []

        doc = self._nlp(text)
        spans = self._map_entities(doc, text)
        if self._normalize_first_line_case:
            spans.extend(self._detect_caps_first_line(text, spans))
        spans.sort(key=lambda span: (span.start, span.end))
        return spans

    def _map_entities(self, doc: Any, text: str) -> list[EntitySpan]:
        spans: list[EntitySpan] = []
        for ent in doc.ents:
            entity_type = _LABEL_TO_TYPE.get(ent.label_)
            if entity_type is None:
                continue
            span = EntitySpan(
                type=entity_type,
                start=ent.start_char,
                end=ent.end_char,
                confidence=self._confidence,
                layer=self.name,
            )
            if span.is_valid(len(text)):
                spans.append(span)
        return spans

    def _detect_caps_first_line(
        self,
        text: str,
        already_found: list[EntitySpan],
    ) -> list[EntitySpan]:
        line_start, line_end = _first_non_empty_line(text)
        if line_start == line_end:
            return []

        line = text[line_start:line_end]
        if not _looks_like_caps_line(line):
            return []

        # Если обычный прогон уже нашёл имя в первой строке, второй не нужен.
        if any(
            span.type is EntityType.NAME and span.start < line_end and span.end > line_start
            for span in already_found
        ):
            return []

        normalized = text[:line_start] + line.title() + text[line_end:]
        if len(normalized) != len(text):
            # Регистр поменял длину (экзотические символы): offsets не совпадут.
            return []

        result: list[EntitySpan] = []
        for ent in self._nlp(normalized).ents:
            if ent.label_ not in _NAME_LABELS:
                continue
            start = max(ent.start_char, line_start)
            end = min(ent.end_char, line_end)
            if start >= end:
                continue
            if len(_WORD.findall(text[start:end])) < self._min_caps_name_words:
                continue
            span = EntitySpan(
                type=EntityType.NAME,
                start=start,
                end=end,
                confidence=self._confidence,
                layer=self.name,
            )
            if span.is_valid(len(text)):
                result.append(span)
        return result


def _first_non_empty_line(text: str) -> tuple[int, int]:
    offset = 0
    for raw_line in text.splitlines(keepends=True):
        stripped = raw_line.strip()
        if stripped:
            start = offset + raw_line.index(stripped)
            return start, start + len(stripped)
        offset += len(raw_line)
    return 0, 0


def _looks_like_caps_line(line: str) -> bool:
    words = [word.strip(_PUNCTUATION) for word in line.split()]
    alpha_words = [word for word in words if any(ch.isalpha() for ch in word)]
    if not 2 <= len(alpha_words) <= 4:
        return False
    if not all(_CAPS_WORD.match(word) for word in alpha_words):
        return False
    return not all(word.lower() in _SECTION_HEADINGS for word in alpha_words)
