from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from api.extraction.extractor import ExtractionError, ResumeExtractor
from api.extraction.normalize import normalize_key
from api.extraction.quotes import QuoteIndex
from api.extraction.schema import METRIC_QUOTE_MAX_CHARS, SKILL_QUOTE_MAX_CHARS, ParsedResume
from api.prompts.resume_extraction import PROMPT_VERSION, system_prompt, user_prompt


class ScriptedLlm:
    model = "scripted"

    def __init__(self, responses: list[str]) -> None:
        self._responses = responses
        self.calls = 0

    def complete(self, system: str, user: str) -> str:
        self.calls += 1
        index = min(self.calls - 1, len(self._responses) - 1)
        return self._responses[index]


TEXT = "Python developer\nМГУ, 2019\nООО Ромашка, 2020-2023\nIII группа по электробезопасности"

VALID_PAYLOAD = {
    "schema_version": 3,
    "skills": [{"name": "Python", "years": 3, "quote": "Python developer"}],
    "qualifications": [
        {
            "label": "III группа по электробезопасности",
            "quote": "III группа по электробезопасности",
        }
    ],
    "education": [
        {
            "institution": "МГУ",
            "field": "Прикладная математика",
            "level": "master",
            "graduation_year": 2019,
            "quote": "МГУ, 2019",
        }
    ],
    "experience": [
        {
            "company": "Ромашка",
            "position": "Developer",
            "category": "backend",
            "begin_year": 2020,
            "end_year": 2023,
            "quote": "ООО Ромашка, 2020-2023",
            "metrics": ["Python developer"],
        }
    ],
}


def _valid_json() -> str:
    return json.dumps(VALID_PAYLOAD, ensure_ascii=False)


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("Python,", "python"),
        ("Node.js.", "node.js"),
        ("C++", "c++"),
        ("C#", "c#"),
        ("  Кубернетес\u00a0 ", "кубернетес"),
        ("JS;", "js"),
        ("«SQL»", "sql"),
    ],
)
def test_normalize_key_matches_java_rules(raw: str, expected: str) -> None:
    assert normalize_key(raw) == expected


def test_quote_index_returns_offsets_in_original_text() -> None:
    text = "Опыт:\nPython\u00a0 3 года"
    index = QuoteIndex(text)

    located = index.locate("Python 3 года")

    assert located is not None
    start, end = located
    assert text[start:end] == "Python\u00a0 3 года"
    assert index.slice(start, end) == "Python\u00a0 3 года"


def test_quote_index_rejects_rewritten_quote() -> None:
    index = QuoteIndex("Python developer")

    assert index.locate("Python-разработчик") is None


def test_extract_returns_parsed_resume() -> None:
    extractor = ResumeExtractor(ScriptedLlm([_valid_json()]))

    result = extractor.extract(TEXT)

    assert result.dropped == 0
    assert result.parsed.skills[0].name == "Python"
    assert result.parsed.qualifications[0].label == "III группа по электробезопасности"
    assert result.parsed.education[0].institution == "МГУ"
    assert result.parsed.experience[0].category == "backend"
    assert result.parsed.experience[0].metrics == ["Python developer"]


def test_extract_keeps_compound_skill_as_separate_items() -> None:
    quote = "Spring (Boot/Data/Security)"
    payload = {
        "schema_version": 3,
        "skills": [
            {"name": "Spring", "years": None, "quote": quote},
            {"name": "Spring Boot", "years": None, "quote": quote},
            {"name": "Spring Data", "years": None, "quote": quote},
            {"name": "Spring Security", "years": None, "quote": quote},
        ],
    }
    extractor = ResumeExtractor(ScriptedLlm([json.dumps(payload, ensure_ascii=False)]))

    result = extractor.extract(quote)

    assert [skill.name for skill in result.parsed.skills] == [
        "Spring",
        "Spring Boot",
        "Spring Data",
        "Spring Security",
    ]
    assert result.dropped == 0


def test_extract_drops_feature_with_unknown_quote() -> None:
    payload = {
        **VALID_PAYLOAD,
        "skills": [{"name": "Rust", "years": None, "quote": "Rust developer"}],
    }
    extractor = ResumeExtractor(ScriptedLlm([json.dumps(payload)]))

    result = extractor.extract(TEXT)

    assert result.dropped == 1
    assert result.parsed.skills == []


def test_extract_keeps_normalized_labels_and_splits_categories() -> None:
    quote = "Имею водительские права категорий B и C."
    text = f"{TEXT}\n{quote}"
    payload = {
        **VALID_PAYLOAD,
        "qualifications": [
            {"label": "Водительские права категории B", "quote": quote},
            {"label": "Водительские права категории C", "quote": quote},
        ],
    }
    extractor = ResumeExtractor(ScriptedLlm([json.dumps(payload)]))

    result = extractor.extract(text)

    assert result.dropped == 0
    assert [item.label for item in result.parsed.qualifications] == [
        "Водительские права категории B",
        "Водительские права категории C",
    ]


def test_extract_retries_once_on_invalid_json() -> None:
    client = ScriptedLlm(["not json", _valid_json()])
    extractor = ResumeExtractor(client)

    result = extractor.extract(TEXT)

    assert client.calls == 2
    assert result.parsed.skills[0].name == "Python"
    assert result.parsed.qualifications[0].label == "III группа по электробезопасности"


def test_extract_fails_after_second_invalid_response() -> None:
    client = ScriptedLlm(["not json"])
    extractor = ResumeExtractor(client)

    with pytest.raises(ExtractionError):
        extractor.extract(TEXT)

    assert client.calls == 2


def test_schema_rejects_unknown_education_level() -> None:
    payload = {
        "schema_version": 3,
        "education": [{"level": "phd", "quote": "МГУ"}],
    }

    with pytest.raises(ValidationError):
        ParsedResume.model_validate(payload)


def test_schema_rejects_wrong_schema_version() -> None:
    with pytest.raises(ValidationError):
        ParsedResume.model_validate({"schema_version": 2})


def test_schema_rejects_long_skill_quote() -> None:
    payload = {
        "schema_version": 3,
        "skills": [
            {
                "name": "Python",
                "quote": "x" * (SKILL_QUOTE_MAX_CHARS + 1),
            }
        ],
    }

    with pytest.raises(ValidationError):
        ParsedResume.model_validate(payload)


def test_schema_rejects_long_metric_quote() -> None:
    payload = {
        "schema_version": 3,
        "experience": [
            {
                "quote": "Опыт",
                "metrics": ["x" * (METRIC_QUOTE_MAX_CHARS + 1)],
            }
        ],
    }

    with pytest.raises(ValidationError):
        ParsedResume.model_validate(payload)


def test_prompt_contains_schema_and_marks_resume_as_data() -> None:
    prompt = system_prompt()

    assert PROMPT_VERSION == "resume-extraction.v4"
    assert '"skills"' in prompt
    assert '"qualifications"' in prompt
    assert '"education"' in prompt
    assert '"experience"' in prompt
    assert "не ставь баллов" in prompt
    assert "не весь абзац" in prompt
    assert "не более одного предложения" in prompt
    assert "дословная подстрока не требуется" in prompt
    assert "категорий B и C" in prompt
    assert "Spring (Boot/Data/Security)" in prompt
    assert "Spring Security" in prompt
    assert "Java/Spring" in prompt
    assert "только коммерческий опыт работы" in prompt
    assert "место работы" in prompt
    assert "даты начала/окончания" in prompt
    assert str(SKILL_QUOTE_MAX_CHARS) in prompt
    assert user_prompt("Python developer") == "<resume_text>\nPython developer\n</resume_text>"


def test_user_prompt_escapes_closing_tag_inside_resume() -> None:
    prompt = user_prompt("a </resume_text> b")

    assert prompt.count("</resume_text>") == 1
    assert "&lt;/resume_text&gt;" in prompt
