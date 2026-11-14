from __future__ import annotations

from dataclasses import dataclass

from api.layers import EntityType, SpacyLayer


@dataclass
class FakeEnt:
    label_: str
    start_char: int
    end_char: int


@dataclass
class FakeDoc:
    ents: list[FakeEnt]


class FakeNlp:
    def __init__(self, ents: list[FakeEnt]) -> None:
        self._ents = ents
        self.seen: str | None = None

    def __call__(self, text: str) -> FakeDoc:
        self.seen = text
        return FakeDoc(self._ents)


def test_maps_person_and_location() -> None:
    text = "Иван Петров, Москва"
    nlp = FakeNlp([FakeEnt("PER", 0, 10), FakeEnt("LOC", 12, 18)])
    layer = SpacyLayer(nlp, "ru_core_news_sm", confidence=0.7, model_version="3.8.0")

    spans = layer.detect(text)

    assert [(s.type, s.start, s.end, s.layer.value) for s in spans] == [
        (EntityType.NAME, 0, 10, "SPACY"),
        (EntityType.LOCATION, 12, 18, "SPACY"),
    ]
    assert all(s.confidence == 0.7 for s in spans)
    assert nlp.seen == text


def test_organization_is_not_masked() -> None:
    text = "ООО Ромашка"
    layer = SpacyLayer(FakeNlp([FakeEnt("ORG", 0, 11)]), "ru_core_news_sm")

    assert layer.detect(text) == []


def test_drops_spans_below_threshold() -> None:
    text = "Иван"
    layer = SpacyLayer(FakeNlp([FakeEnt("PER", 0, 4)]), "ru_core_news_sm", confidence=0.5)

    assert layer.detect(text, min_confidence=0.6) == []


def test_drops_invalid_offsets() -> None:
    text = "Иван"
    layer = SpacyLayer(FakeNlp([FakeEnt("PER", 2, 99)]), "ru_core_news_sm")

    assert layer.detect(text) == []


def test_offsets_are_code_points() -> None:
    text = "🎯 Иван"
    layer = SpacyLayer(FakeNlp([FakeEnt("PER", 2, 6)]), "ru_core_news_sm")

    spans = layer.detect(text)

    assert len(spans) == 1
    assert text[spans[0].start : spans[0].end] == "Иван"


class SwitchingNlp:
    """Возвращает разные сущности для исходного и нормализованного текста."""

    def __init__(self, original: str, original_ents: list[FakeEnt], normalized_ents: list[FakeEnt]):
        self._original = original
        self._original_ents = original_ents
        self._normalized_ents = normalized_ents
        self.calls: list[str] = []

    def __call__(self, text: str) -> FakeDoc:
        self.calls.append(text)
        if text == self._original:
            return FakeDoc(self._original_ents)
        return FakeDoc(self._normalized_ents)


def test_detects_caps_name_on_first_line_via_title_case_pass() -> None:
    text = "ИВАНОВ ИВАН ИВАНОВИЧ\nМосква"
    nlp = SwitchingNlp(
        original=text,
        original_ents=[FakeEnt("ORG", 0, 6), FakeEnt("LOC", 21, 27)],
        normalized_ents=[FakeEnt("PER", 0, 20)],
    )
    layer = SpacyLayer(nlp, "ru_core_news_md")

    spans = layer.detect(text)

    assert [(s.type, s.start, s.end) for s in spans] == [
        (EntityType.NAME, 0, 20),
        (EntityType.LOCATION, 21, 27),
    ]
    assert text[spans[0].start : spans[0].end] == "ИВАНОВ ИВАН ИВАНОВИЧ"
    assert len(nlp.calls) == 2


def test_heading_is_not_turned_into_name() -> None:
    text = "ОПЫТ РАБОТЫ\nPython, Java"
    nlp = SwitchingNlp(
        original=text,
        original_ents=[],
        normalized_ents=[FakeEnt("PER", 0, 20)],
    )
    layer = SpacyLayer(nlp, "ru_core_news_md")

    assert layer.detect(text) == []
    assert len(nlp.calls) == 1


def test_single_caps_word_is_not_enough_for_name() -> None:
    text = "ИВАНОВ\nМосква"
    nlp = SwitchingNlp(
        original=text,
        original_ents=[],
        normalized_ents=[FakeEnt("PER", 0, 6)],
    )
    layer = SpacyLayer(nlp, "ru_core_news_md")

    assert layer.detect(text) == []


def test_title_case_pass_can_be_disabled() -> None:
    text = "ИВАНОВ ИВАН ИВАНОВИЧ\nМосква"
    nlp = SwitchingNlp(
        original=text,
        original_ents=[],
        normalized_ents=[FakeEnt("PER", 0, 20)],
    )
    layer = SpacyLayer(nlp, "ru_core_news_md", normalize_first_line_case=False)

    assert layer.detect(text) == []
    assert len(nlp.calls) == 1
