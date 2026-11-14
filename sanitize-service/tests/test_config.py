from __future__ import annotations

import pytest
from pydantic import ValidationError

from api.config import PipelineConfig, Settings


def test_default_pipeline_is_ru_spacy() -> None:
    settings = Settings(_env_file=None)

    assert settings.default_pipeline == "ru"
    assert settings.pipelines["ru"].layers == ["spacy"]
    assert settings.pipelines["ru"].spacy_model == "ru_core_news_md"
    assert settings.pipelines["ru"].normalize_first_line_case is True


def test_pipelines_are_read_from_json_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv(
        "SANITIZE_SERVICE_PIPELINES",
        '{"en": {"spacy_model": "en_core_web_sm", "layers": ["spacy"], '
        '"spacy_confidence": 0.5, "min_confidence": 0.4}}',
    )
    monkeypatch.setenv("SANITIZE_SERVICE_DEFAULT_PIPELINE", "en")

    settings = Settings(_env_file=None)

    assert settings.pipelines["en"].spacy_model == "en_core_web_sm"
    assert settings.pipelines["en"].spacy_confidence == 0.5
    assert settings.pipelines["en"].min_confidence == 0.4


def test_unknown_default_pipeline_fails_fast() -> None:
    with pytest.raises(ValidationError, match="default pipeline"):
        Settings(_env_file=None, default_pipeline="de")


def test_confidence_must_be_in_range() -> None:
    with pytest.raises(ValidationError):
        PipelineConfig(spacy_confidence=1.5)
    with pytest.raises(ValidationError):
        PipelineConfig(min_confidence=-0.1)


def test_llm_layer_can_be_configured_but_stays_reserved() -> None:
    config = PipelineConfig(layers=["spacy", "llm"])

    assert config.layers == ["spacy", "llm"]
