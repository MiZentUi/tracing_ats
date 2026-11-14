from __future__ import annotations

import pytest

from api.scoring.versioning import (
    VersionMatchPolicy,
    match_skill_names,
    parse_skill_name,
)


@pytest.mark.parametrize(
    ("raw", "base", "versions"),
    [
        ("Java 11/17", "java", ("11", "17")),
        ("Python 3.12", "python", ("3.12",)),
        ("Java 1.8", "java", ("1.8",)),
        ("Java 8–17", "java", ("8", "17")),
        ("Java 11, 17", "java", ("11", "17")),
        ("Java 11 and 17", "java", ("11", "17")),
        ("Java 11 или 17", "java", ("11", "17")),
        ("Java 11+", "java", ("11",)),
        ("C++17", "c++", ("17",)),
        ("C# 12", "c#", ("12",)),
        (".NET 8", ".net", ("8",)),
        ("Node.js 18", "node.js", ("18",)),
        ("Vue 3", "vue", ("3",)),
        ("1С 8.3", "1c", ("8.3",)),
        ("Spring Boot 3", "spring boot", ("3",)),
        ("Java EE", "java ee", ()),
        ("AngularJS", "angularjs", ()),
        ("JavaScript", "javascript", ()),
        ("Spring Boot", "spring boot", ()),
        ("Java developer", "java developer", ()),
        ("Java, Python", "java, python", ()),
        ("C++", "c++", ()),
        ("C#", "c#", ()),
        (".NET", ".net", ()),
    ],
)
def test_parse_skill_name_separates_only_version_tails(
    raw: str,
    base: str,
    versions: tuple[str, ...],
) -> None:
    parsed = parse_skill_name(raw)

    assert parsed.base == base
    assert parsed.versions == versions


def test_java_version_matches_base_skill() -> None:
    match = match_skill_names("Java", "Java 11/17")

    assert match is not None
    assert match.base == "java"
    assert match.versions_compatible is True
    assert match.mismatch is False
    assert match.score == 1.0


def test_intersecting_versions_match() -> None:
    match = match_skill_names("Java 17", "Java 11/17")

    assert match is not None
    assert match.versions_compatible is True
    assert match.score == 1.0


def test_non_intersecting_versions_are_full_by_default() -> None:
    match = match_skill_names("Java 21", "Java 11/17")

    assert match is not None
    assert match.versions_compatible is False
    assert match.mismatch is True
    assert match.score == 1.0


def test_version_mismatch_policy_can_be_stricter() -> None:
    policy = VersionMatchPolicy(mismatch_score=0.5)
    match = match_skill_names("Java 21", "Java 11/17", policy)

    assert match is not None
    assert match.score == 0.5


def test_missing_version_does_not_count_as_mismatch() -> None:
    match = match_skill_names("Java 21", "Java")

    assert match is not None
    assert match.mismatch is False
    assert match.score == 1.0


@pytest.mark.parametrize(
    ("required", "found"),
    [
        ("Java", "JavaScript"),
        ("Java", "Java EE"),
        ("Angular", "AngularJS"),
        ("Spring", "Spring Boot"),
        ("React", "React Native"),
        ("Java", "Java developer"),
        ("Java", "Java, Python"),
        ("C", "C++17"),
        ("C", "C# 12"),
    ],
)
def test_non_version_suffixes_are_not_stripped(required: str, found: str) -> None:
    assert match_skill_names(required, found) is None


@pytest.mark.parametrize(
    ("required", "found"),
    [
        ("C++", "C++17"),
        ("C#", "C# 12"),
        (".NET", ".NET 8"),
        ("1C", "1С 8.3"),
        ("Python", "Python 3.12"),
        ("Vue", "Vue 3"),
    ],
)
def test_supported_version_forms_match(required: str, found: str) -> None:
    match = match_skill_names(required, found)

    assert match is not None
    assert match.score == 1.0
