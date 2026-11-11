from __future__ import annotations

from api.scoring.similarity import best_match, resume_seed, similarity, similarity_matrix


def test_resume_seed_is_stable_and_not_process_random() -> None:
    # Значение зафиксировано, чтобы поймать переход на встроенный hash().
    assert resume_seed("Python") == 1767767477899464722
    assert resume_seed("Python") != resume_seed("Java")


def test_similarity_is_deterministic_and_bounded() -> None:
    seed = resume_seed("Python developer")

    first = similarity(seed, "python", "skill:0:python")
    second = similarity(seed, "python", "skill:0:python")

    assert first == second
    assert 0.0 <= first < 1.0
    assert first != similarity(seed, "python", "skill:1:java")


def test_best_match_selects_maximum() -> None:
    seed = resume_seed("Python developer")
    features = [("a", "first"), ("b", "second")]

    match = best_match(seed, "python", features)

    assert match is not None
    value, score = match
    assert score == max(similarity(seed, "python", key) for key, _ in features)
    assert value == (
        "first" if similarity(seed, "python", "a") >= similarity(seed, "python", "b") else "second"
    )


def test_best_match_returns_none_without_features() -> None:
    assert best_match(1, "python", []) is None


def test_similarity_matrix_covers_all_pairs_and_is_deterministic() -> None:
    seed = resume_seed("Python developer")
    requirements = ["python", "kafka"]
    features = ["skill:0:python", "skill:1:kafka"]

    first = similarity_matrix(seed, requirements, features)
    second = similarity_matrix(seed, requirements, features)

    assert first == second
    assert set(first) == {
        (requirement, feature) for requirement in requirements for feature in features
    }
    assert all(0.0 <= value < 1.0 for value in first.values())
