from __future__ import annotations

import pytest

from dictionary_service.seed import PROVIDED_SEED, PROVIDED_SEED_RELATIONS
from dictionary_service.service import DictionaryService, ResolveInput
from dictionary_service.store import InMemoryStore


@pytest.fixture()
def service() -> DictionaryService:
    store = InMemoryStore()
    instance = DictionaryService(store, config_hash="test")
    instance.seed_if_empty()
    return instance


def resolve(service: DictionaryService, raw: str):
    _, items = service.resolve([ResolveInput("k", raw, "mixed", "RESUME")])
    return items[0]


def test_provided_seed_has_expected_volume() -> None:
    assert len(PROVIDED_SEED) >= 60
    assert len(PROVIDED_SEED_RELATIONS) >= 20


@pytest.mark.parametrize(
    ("raw", "canonical"),
    [
        ("HTML5", "HTML"),
        ("TailwindCSS", "Tailwind CSS"),
        ("Nuxt.js", "Nuxt"),
        ("Vue Router", "Vue Router"),
        ("Pinia", "Pinia"),
        ("SCSS", "Sass"),
        ("WordPress", "WordPress"),
        ("Битрикс", "1C-Битрикс"),
        ("Google Sheets", "Google Sheets"),
        ("Google Workspace", "Google Workspace"),
        ("отчетность", "Отчётность"),
        ("документооборот", "Делопроизводство"),
        ("основные средства", "Учёт ОС и ТМЦ"),
        ("инвентаризация", "Инвентаризация"),
        ("бухгалтерия", "Бухгалтерский учёт"),
        ("деловая коммуникация", "Коммуникация"),
        ("командная работа", "Работа в команде"),
        ("es6+", "JavaScript"),
        ("Vite", "Vite"),
        ("Jest", "Jest"),
        ("JWT", "JWT"),
        ("Playwright", "Playwright"),
        ("SQLite", "SQLite"),
        ("P&L", "P&L"),
        ("Unit-экономика", "Unit-экономика"),
        ("LLM", "LLM"),
    ],
)
def test_documented_terms_resolve_to_canonical(
    service: DictionaryService,
    raw: str,
    canonical: str,
) -> None:
    item = resolve(service, raw)

    assert item.skill_id is not None
    assert item.canonical_name == canonical


def test_ambiguous_terms_are_not_merged(service: DictionaryService) -> None:
    assert resolve(service, "Bitrix24").skill_id != resolve(service, "Битрикс").skill_id
    assert resolve(service, "WordPress").skill_id != resolve(service, "Word").skill_id
    assert resolve(service, "P&L").skill_id != resolve(service, "Финансовый анализ").skill_id
    assert resolve(service, "LLM").skill_id != resolve(service, "Machine Learning").skill_id


def test_provided_relations_are_available(service: DictionaryService) -> None:
    vue = resolve(service, "Vue").skill_id
    nuxt = resolve(service, "Nuxt").skill_id
    excel = resolve(service, "Excel").skill_id
    sheets = resolve(service, "Google Sheets").skill_id
    wordpress = resolve(service, "WordPress").skill_id
    bitrix = resolve(service, "Битрикс").skill_id
    llm = resolve(service, "LLM").skill_id
    prompt = resolve(service, "Prompt Engineering").skill_id
    assert vue and nuxt and excel and sheets and wordpress and bitrix and llm and prompt

    assert service.get_relations([(vue, nuxt)])[1][0].kind == "RELATED"
    assert service.get_relations([(excel, sheets)])[1][0].kind == "SAME_GROUP"
    assert service.get_relations([(wordpress, bitrix)])[1][0].kind == "SAME_GROUP"
    assert service.get_relations([(llm, prompt)])[1][0].kind == "RELATED"


def test_seed_if_empty_adds_provided_terms_to_existing_dictionary() -> None:
    store = InMemoryStore()
    store.seed(())
    service = DictionaryService(store, config_hash="test")

    service.seed_if_empty()

    assert resolve(service, "Tailwind CSS").skill_id is not None
    assert resolve(service, "Инвентаризация").skill_id is not None
    assert resolve(service, "документооборот").canonical_name == "Делопроизводство"
