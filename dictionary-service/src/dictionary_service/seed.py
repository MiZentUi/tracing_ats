"""Seed словаря. Агент наполняет его под критерии приёмки.

Каноническое имя хранится алиасом к самому себе и создаётся автоматически.
Здесь перечислены только дополнительные написания. ``raw_only`` — жаргон,
для которого лемматизация меняет смысл (``кубы`` -> ``куб``).
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from importlib.resources import files


@dataclass(frozen=True, slots=True)
class SeedSkill:
    canonical: str
    skill_type: str
    aliases: tuple[str, ...] = ()
    raw_only: tuple[str, ...] = ()


BASE_SEED: tuple[SeedSkill, ...] = (
    # Языки и платформы
    SeedSkill("Java", "TECH", ("джава",)),
    SeedSkill("JavaScript", "TECH", ("js", "джаваскрипт", "es6", "es6+", "ecmascript")),
    SeedSkill("TypeScript", "TECH", ("ts",)),
    SeedSkill("Python", "TECH", ("питон", "пайтон")),
    SeedSkill("Go", "TECH", ("golang",)),
    SeedSkill("Kotlin", "TECH"),
    SeedSkill("PHP", "TECH"),
    SeedSkill("Ruby", "TECH"),
    SeedSkill("Rust", "TECH"),
    SeedSkill("Swift", "TECH"),
    SeedSkill("Scala", "TECH"),
    SeedSkill("C++", "TECH", ("cpp",)),
    SeedSkill("C#", "TECH", ("csharp", "си шарп")),
    SeedSkill(".NET", "TECH", ("dotnet", "дотнет")),
    # Данные и инфраструктура
    SeedSkill("SQL", "TECH"),
    SeedSkill("PostgreSQL", "TECH", ("postgres", "постгрес", "постгресс")),
    SeedSkill("MySQL", "TECH"),
    SeedSkill("Oracle", "TECH"),
    SeedSkill("MongoDB", "TECH", ("mongo",)),
    SeedSkill("Redis", "TECH"),
    SeedSkill("Elasticsearch", "TECH", ("elastic",)),
    SeedSkill("Kafka", "TECH"),
    SeedSkill("RabbitMQ", "TECH"),
    SeedSkill("Docker", "TOOL", ("докер",)),
    SeedSkill("Kubernetes", "TOOL", ("kubernetes", "k8s", "кубернетес"), ("кубы",)),
    SeedSkill("Helm", "TOOL"),
    SeedSkill("Terraform", "TOOL"),
    SeedSkill("Ansible", "TOOL"),
    SeedSkill("Linux", "TECH"),
    SeedSkill("Bash", "TECH"),
    SeedSkill("Git", "TOOL"),
    SeedSkill("GitLab CI", "TOOL"),
    SeedSkill("GitHub Actions", "TOOL"),
    SeedSkill("Jenkins", "TOOL"),
    SeedSkill("Nginx", "TOOL"),
    # Backend и API
    SeedSkill("Spring", "TECH"),
    SeedSkill("Spring Boot", "TECH", ("springboot", "спринг бут")),
    SeedSkill("Django", "TECH"),
    SeedSkill("FastAPI", "TECH"),
    SeedSkill("Flask", "TECH"),
    SeedSkill("GraphQL", "TECH"),
    SeedSkill("REST", "TECH", ("rest api",)),
    SeedSkill("gRPC", "TECH"),
    # Frontend
    SeedSkill("React", "TECH", ("реакт",)),
    SeedSkill("React Native", "TECH"),
    SeedSkill("Vue", "TECH", ("vue.js", "вью")),
    SeedSkill("Vuex", "TECH"),
    SeedSkill("Angular", "TECH"),
    SeedSkill("Node.js", "TECH", ("nodejs", "node", "нод.js")),
    # ML и данные
    SeedSkill("Machine Learning", "TECH", ("ml", "машинное обучение")),
    SeedSkill("Data Science", "TECH", ("data science",)),
    SeedSkill("pandas", "TOOL"),
    SeedSkill("NumPy", "TOOL", ("numpy",)),
    SeedSkill("PyTorch", "TOOL"),
    SeedSkill("TensorFlow", "TOOL"),
    SeedSkill("scikit-learn", "TOOL", ("sklearn",)),
    # 1С и офис
    SeedSkill("1C", "TOOL", ("1c",)),
    SeedSkill("1C:Бухгалтерия", "TOOL", ("1с бухгалтерия", "1c бухгалтерия")),
    SeedSkill("1C:ЗУП", "TOOL", ("1с зуп", "1c зуп")),
    SeedSkill("Excel", "TOOL", ("эксель",)),
    SeedSkill("Word", "TOOL", ("ворд",)),
    SeedSkill("Outlook", "TOOL"),
    SeedSkill("Диадок", "TOOL", ("diadoc",)),
    SeedSkill("СБИС", "TOOL", ("sbis",)),
    SeedSkill("ЭДО", "DOMAIN", ("электронный документооборот",)),
    SeedSkill("КонсультантПлюс", "TOOL", ("консультант плюс",)),
    SeedSkill("Парус", "TOOL"),
    # Не-IT домены
    SeedSkill(
        "Учёт ОС и ТМЦ",
        "DOMAIN",
        (
            "учет основных средств",
            "ос и тмц",
            "основные средства",
            "учет ос",
            "движение ос",
            "списание ос",
            "инвентарные номера",
            "тмц",
        ),
    ),
    SeedSkill("Складской учёт", "DOMAIN", ("складской учет", "склад")),
    SeedSkill(
        "Делопроизводство",
        "DOMAIN",
        ("документооборот", "документоведение", "документовед", "делопроизводитель"),
    ),
    SeedSkill("Кадровое делопроизводство", "DOMAIN"),
    SeedSkill("Библиотечное дело", "DOMAIN"),
    SeedSkill("Каталогизация", "DOMAIN"),
    SeedSkill("Электромонтаж", "DOMAIN", ("электромонтажные работы",)),
    SeedSkill("Чтение схем", "DOMAIN", ("чтение электрических схем",)),
    SeedSkill("Охрана труда", "DOMAIN"),
    SeedSkill("402-ФЗ", "DOMAIN", ("402 фз",)),
    # Soft
    SeedSkill(
        "Коммуникация",
        "SOFT",
        ("коммуникабельность", "коммуникативные навыки", "деловая коммуникация"),
    ),
    SeedSkill("Работа в команде", "SOFT", ("teamwork", "командная работа")),
)


@dataclass(frozen=True, slots=True)
class SeedQualification:
    canonical: str
    qualification_type: str
    aliases: tuple[str, ...] = ()


@dataclass(frozen=True, slots=True)
class SeedRelation:
    left: str
    right: str
    kind: str


BASE_SEED_QUALIFICATIONS: tuple[SeedQualification, ...] = (
    SeedQualification(
        "I группа по электробезопасности",
        "PERMIT",
        ("1 группа по электробезопасности", "1-я группа по электробезопасности"),
    ),
    SeedQualification(
        "II группа по электробезопасности",
        "PERMIT",
        ("2 группа по электробезопасности", "2-я группа по электробезопасности"),
    ),
    SeedQualification(
        "III группа по электробезопасности",
        "PERMIT",
        ("3 группа по электробезопасности", "3-я группа по электробезопасности"),
    ),
    SeedQualification(
        "IV группа по электробезопасности",
        "PERMIT",
        ("4 группа по электробезопасности", "4-я группа по электробезопасности"),
    ),
    SeedQualification(
        "Водительские права категории B",
        "LICENSE",
        ("права категории B", "водительские права категории B"),
    ),
    SeedQualification(
        "Водительские права категории C",
        "LICENSE",
        ("права категории C", "водительские права категории C"),
    ),
    SeedQualification(
        "Аттестат профессионального бухгалтера",
        "ATTESTATION",
        ("профессиональный бухгалтер",),
    ),
)


BASE_SEED_RELATIONS: tuple[SeedRelation, ...] = (
    SeedRelation("Kafka", "RabbitMQ", "RELATED"),
    SeedRelation("PostgreSQL", "MySQL", "SAME_GROUP"),
    SeedRelation("Docker", "Kubernetes", "SAME_GROUP"),
)


def _load_open_seed() -> tuple[tuple[SeedSkill, ...], tuple[SeedQualification, ...]]:
    resource = files("dictionary_service").joinpath("data/open_seed.json")
    payload = json.loads(resource.read_text(encoding="utf-8"))
    skills = tuple(
        SeedSkill(
            canonical=item["canonical"],
            skill_type=item.get("type", "OTHER"),
            aliases=tuple(item.get("aliases", ())),
            raw_only=tuple(item.get("raw_only", ())),
        )
        for item in payload.get("skills", ())
    )
    qualifications = tuple(
        SeedQualification(
            canonical=item["canonical"],
            qualification_type=item.get("type"),
            aliases=tuple(item.get("aliases", ())),
        )
        for item in payload.get("qualifications", ())
    )
    return skills, qualifications


def _load_provided_seed() -> tuple[
    tuple[SeedSkill, ...],
    tuple[SeedQualification, ...],
    tuple[SeedRelation, ...],
]:
    resource = files("dictionary_service").joinpath("data/provided_seed.json")
    if not resource.is_file():
        return (), (), ()
    payload = json.loads(resource.read_text(encoding="utf-8"))
    skills = tuple(
        SeedSkill(
            canonical=item["canonical"],
            skill_type=item.get("type", "OTHER"),
            aliases=tuple(item.get("aliases", ())),
            raw_only=tuple(item.get("raw_only", ())),
        )
        for item in payload.get("skills", ())
    )
    qualifications = tuple(
        SeedQualification(
            canonical=item["canonical"],
            qualification_type=item.get("type"),
            aliases=tuple(item.get("aliases", ())),
        )
        for item in payload.get("qualifications", ())
    )
    relations = tuple(
        SeedRelation(
            left=item["left"],
            right=item["right"],
            kind=item["kind"],
        )
        for item in payload.get("relations", ())
    )
    return skills, qualifications, relations


def _merge_by_canonical(
    base: tuple,
    extra: tuple,
) -> tuple:
    seen = {item.canonical.strip().casefold() for item in base}
    merged = list(base)
    for item in extra:
        key = item.canonical.strip().casefold()
        if key not in seen:
            merged.append(item)
            seen.add(key)
    return tuple(merged)


def _merge_relations(
    base: tuple[SeedRelation, ...],
    extra: tuple[SeedRelation, ...],
) -> tuple[SeedRelation, ...]:
    seen = {
        (item.left, item.right) if item.left <= item.right else (item.right, item.left)
        for item in base
    }
    merged = list(base)
    for item in extra:
        key = (item.left, item.right) if item.left <= item.right else (item.right, item.left)
        if key in seen:
            continue
        merged.append(item)
        seen.add(key)
    return tuple(merged)


OPEN_SEED, OPEN_SEED_QUALIFICATIONS = _load_open_seed()
PROVIDED_SEED, PROVIDED_SEED_QUALIFICATIONS, PROVIDED_SEED_RELATIONS = _load_provided_seed()
SEED = _merge_by_canonical(_merge_by_canonical(BASE_SEED, OPEN_SEED), PROVIDED_SEED)
SEED_QUALIFICATIONS = _merge_by_canonical(
    _merge_by_canonical(BASE_SEED_QUALIFICATIONS, OPEN_SEED_QUALIFICATIONS),
    PROVIDED_SEED_QUALIFICATIONS,
)
SEED_RELATIONS = _merge_relations(BASE_SEED_RELATIONS, PROVIDED_SEED_RELATIONS)
