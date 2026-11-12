from __future__ import annotations

import pytest

from api.scoring.similarity import DeterministicSimilarity, tokenize


@pytest.fixture
def similarity() -> DeterministicSimilarity:
    return DeterministicSimilarity()


def test_exact_normalized_match_is_one(similarity: DeterministicSimilarity) -> None:
    assert similarity.score("Python", " python ") == 1.0
    assert similarity.score("C++", "c++") == 1.0


def test_strong_match_for_token_subset_and_close_names(
    similarity: DeterministicSimilarity,
) -> None:
    assert similarity.score("React", "React Native") == 0.85
    assert similarity.score("Postgres", "PostgreSQL") == 0.85


def test_partial_match_for_close_but_different_names(
    similarity: DeterministicSimilarity,
) -> None:
    assert similarity.score("Node.js", "Node") == 0.5


def test_java_does_not_match_javascript(similarity: DeterministicSimilarity) -> None:
    assert similarity.score("Java", "JavaScript") == 0.0
    assert similarity.score("C++", "C#") == 0.0


def test_unrelated_skills_do_not_match(similarity: DeterministicSimilarity) -> None:
    assert similarity.score("Kafka", "RabbitMQ") == 0.0
    assert similarity.score("", "Python") == 0.0


def test_text_score_uses_significant_token_overlap(
    similarity: DeterministicSimilarity,
) -> None:
    assert similarity.text_score("Python backend", "Python developer") == 0.5
    assert similarity.text_score("коммуникация", "Python developer") == 0.0


def test_tokenize_drops_stopwords_and_short_tokens() -> None:
    assert tokenize("Опыт работы с Python и SQL") == {"опыт", "работы", "python", "sql"}
