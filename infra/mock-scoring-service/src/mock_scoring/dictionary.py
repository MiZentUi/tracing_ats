"""Словарь навыков заглушки.

Набор намеренно маленький и фиксированный: заглушка не должна зависеть от
справочника `skill_alias` в БД (инвариант 4: `scoring-service` stateless).
"""

from __future__ import annotations

import re

# Каноническое название → алиасы. Порядок не важен, длина учитывается при
# построении регулярного выражения.
SKILL_ALIASES: dict[str, tuple[str, ...]] = {
    "Java": ("java",),
    "JavaScript": ("javascript", "js"),
    "TypeScript": ("typescript", "ts"),
    "Python": ("python",),
    "Go": ("golang", "go"),
    "Kotlin": ("kotlin",),
    "Scala": ("scala",),
    "Rust": ("rust",),
    "C++": ("c++",),
    "C#": ("c#", "c sharp"),
    ".NET": (".net", "dotnet", "asp.net"),
    "SQL": ("sql",),
    "PostgreSQL": ("postgresql", "postgres"),
    "MySQL": ("mysql",),
    "Oracle": ("oracle",),
    "MongoDB": ("mongodb", "mongo"),
    "Redis": ("redis",),
    "Kafka": ("kafka",),
    "RabbitMQ": ("rabbitmq",),
    "Elasticsearch": ("elasticsearch",),
    "Docker": ("docker",),
    "Kubernetes": ("kubernetes", "k8s"),
    "Spring Boot": ("spring boot", "spring"),
    "React": ("react",),
    "Vue": ("vue", "vue.js"),
    "Angular": ("angular",),
    "Node.js": ("node.js", "nodejs", "node"),
    "Django": ("django",),
    "FastAPI": ("fastapi",),
    "Flask": ("flask",),
    "gRPC": ("grpc",),
    "REST": ("rest", "rest api"),
    "GraphQL": ("graphql",),
    "Git": ("git",),
    "Linux": ("linux",),
    "Machine Learning": ("machine learning", "ml"),
    "Data Science": ("data science",),
}

_ALIAS_TO_CANONICAL: dict[str, str] = {}
for _canonical, _aliases in SKILL_ALIASES.items():
    for _alias in _aliases:
        if _alias in _ALIAS_TO_CANONICAL:
            raise RuntimeError(f"duplicate skill alias: {_alias}")
        _ALIAS_TO_CANONICAL[_alias] = _canonical

_ALIASES_BY_LENGTH = sorted(_ALIAS_TO_CANONICAL, key=len, reverse=True)
_ALIAS_ALTERNATION = "|".join(re.escape(alias) for alias in _ALIASES_BY_LENGTH)
SKILL_PATTERN = re.compile(
    r"(?<![0-9A-Za-z_+#.])(" + _ALIAS_ALTERNATION + r")(?![0-9A-Za-z_+#])",
    re.IGNORECASE,
)

_WHITESPACE_RE = re.compile(r"\s+")


def normalize(value: str | None) -> str:
    """Нижний регистр, схлопнутые пробелы, без обрамляющих пробелов."""
    return _WHITESPACE_RE.sub(" ", (value or "").strip()).lower()


def canonical_skill(value: str | None) -> str | None:
    """Каноническое название навыка по алиасу или `None`, если навык неизвестен."""
    return _ALIAS_TO_CANONICAL.get(normalize(value))
