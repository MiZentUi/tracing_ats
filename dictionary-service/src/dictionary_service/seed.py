"""Seed словаря. Агент наполняет его под критерии приёмки.

Каноническое имя хранится алиасом к самому себе и создаётся автоматически.
Здесь перечислены только дополнительные написания. ``raw_only`` — жаргон,
для которого лемматизация меняет смысл (``кубы`` -> ``куб``).
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class SeedSkill:
    canonical: str
    skill_type: str
    aliases: tuple[str, ...] = ()
    raw_only: tuple[str, ...] = ()


SEED: tuple[SeedSkill, ...] = (
    # Языки и платформы
    SeedSkill("Java", "TECH", ("джава",)),
    SeedSkill("JavaScript", "TECH", ("js", "джаваскрипт")),
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
    SeedSkill("Учёт ОС и ТМЦ", "DOMAIN", ("учет основных средств", "ос и тмц")),
    SeedSkill("Складской учёт", "DOMAIN", ("складской учет",)),
    SeedSkill("Делопроизводство", "DOMAIN"),
    SeedSkill("Кадровое делопроизводство", "DOMAIN"),
    SeedSkill("Библиотечное дело", "DOMAIN"),
    SeedSkill("Каталогизация", "DOMAIN"),
    SeedSkill("Электромонтаж", "DOMAIN", ("электромонтажные работы",)),
    SeedSkill("Чтение схем", "DOMAIN", ("чтение электрических схем",)),
    SeedSkill("Охрана труда", "DOMAIN"),
    SeedSkill("402-ФЗ", "DOMAIN", ("402 фз",)),
    # Soft
    SeedSkill("Коммуникация", "SOFT", ("коммуникабельность",)),
    SeedSkill("Работа в команде", "SOFT", ("teamwork",)),
)
