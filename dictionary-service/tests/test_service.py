from __future__ import annotations

import pytest

from dictionary_service.domain import AliasConflict, CanonicalRequired
from dictionary_service.seed import SEED
from dictionary_service.service import (
    ApplyAliasInput,
    ApplySkillInput,
    DictionaryService,
    ResolveInput,
    ResolveQualificationInput,
)
from dictionary_service.store import InMemoryStore


@pytest.fixture()
def service() -> DictionaryService:
    store = InMemoryStore()
    store.seed(SEED)
    instance = DictionaryService(store, config_hash="test")
    instance.seed_if_empty()
    return instance


def resolve(service: DictionaryService, raw: str, as_of: str = ""):
    _, items = service.resolve([ResolveInput("k", raw, "mixed", "RESUME")], as_of=as_of)
    return items[0]


def test_js_and_javascript_are_one_skill(service: DictionaryService) -> None:
    js = resolve(service, "js")
    javascript = resolve(service, "JavaScript")
    assert js.skill_id is not None
    assert js.skill_id == javascript.skill_id
    assert js.method == "ALIAS_EXACT"


def test_piton_python(service: DictionaryService) -> None:
    assert resolve(service, "питон").skill_id == resolve(service, "python").skill_id


def test_cyrillic_and_latin_1c(service: DictionaryService) -> None:
    assert resolve(service, "1С").skill_id == resolve(service, "1C").skill_id
    assert resolve(service, "1С").method == "ALIAS_FOLDED"


def test_kubernetes_variants(service: DictionaryService) -> None:
    target = resolve(service, "kubernetes").skill_id
    assert resolve(service, "кубернетесом").skill_id == target
    assert resolve(service, "k8s").skill_id == target
    assert resolve(service, "кубы").skill_id == target
    assert resolve(service, "куб").skill_id is None


def test_sok_is_not_folded(service: DictionaryService) -> None:
    assert resolve(service, "сок").skill_id is None


def test_yo_is_normalized(service: DictionaryService) -> None:
    assert resolve(service, "учёт").skill_id == resolve(service, "учет").skill_id


def test_vue_and_vuex_are_different(service: DictionaryService) -> None:
    assert resolve(service, "vue").skill_id != resolve(service, "vuex").skill_id


def test_java_and_javascript_are_different(service: DictionaryService) -> None:
    assert resolve(service, "java").skill_id != resolve(service, "javascript").skill_id


def test_unknown_goes_to_review(service: DictionaryService) -> None:
    item = resolve(service, "НеизвестныйНавык")
    assert item.skill_id is None
    assert item.method == "NEW"
    items, total = service.list_review("PENDING", 10, 0)
    assert total == 1
    assert items[0].key == "неизвестныйнавык"


def test_alias_conflict_is_rejected(service: DictionaryService) -> None:
    py = resolve(service, "python").skill_id
    js = resolve(service, "javascript").skill_id
    assert py and js
    with pytest.raises(AliasConflict):
        service.add_alias(js, "питон", "ru", "AUTO", "hr")


def test_exactly_one_canonical_and_delete_guard(service: DictionaryService) -> None:
    skill = service.create_skill("Тестовый навык", "OTHER", "hr")
    canonical = next(alias for alias in skill.aliases if alias.is_canonical)
    with pytest.raises(CanonicalRequired):
        service.delete_alias(canonical.id, "hr")
    extra = service.add_alias(skill.id, "тест-алиас", "ru", "AUTO", "hr")
    updated = service.set_canonical(skill.id, extra.id, "hr")
    assert sum(1 for alias in updated.aliases if alias.is_canonical) == 1
    assert updated.canonical_name == "тест-алиас"


def test_version_grows_on_write_and_as_of_is_stable(service: DictionaryService) -> None:
    first = service.version().resolver_version
    service.create_skill("Новый навык", "OTHER", "hr")
    second = service.version().resolver_version
    assert first != second
    _, old = service.resolve([ResolveInput("k", "Новый навык", "ru", "VACANCY")], as_of=first)
    assert old[0].skill_id is None
    assert resolve(service, "Новый навык").skill_id is not None


def test_review_alias_resolution(service: DictionaryService) -> None:
    item = resolve(service, "постгрессс")
    assert item.skill_id is None
    review, _ = service.list_review("PENDING", 10, 0)
    pg = resolve(service, "postgresql").skill_id
    resolved, skill = service.resolve_review(review[0].id, "ALIAS", "hr", pg, None, None, [])
    assert resolved.status == "RESOLVED_ALIAS"
    assert skill is not None
    assert resolve(service, "постгрессс").skill_id == pg


def test_skip_review_does_not_enqueue() -> None:
    store = InMemoryStore()
    store.seed(SEED)
    service = DictionaryService(store, config_hash="test")
    service.resolve([ResolveInput("k", "НеизвестныйНавык", "ru", "ADMIN")], skip_review=True)
    _, total = service.list_review("PENDING", 10, 0)
    assert total == 0


def test_apply_skills_creates_one_version_and_aliases(service: DictionaryService) -> None:
    before = service.version().resolver_version
    applied, version = service.apply_skills(
        [
            ApplySkillInput(
                canonical="Электромонтаж",
                skill_type="DOMAIN",
                aliases=(
                    ApplyAliasInput("электромонтажные работы", "ru"),
                    ApplyAliasInput("монтаж электрики", "ru"),
                ),
            )
        ],
        "hr",
    )
    assert version != before
    assert applied[0].canonical_name == "Электромонтаж"
    assert resolve(service, "электромонтажные работы").canonical_name == "Электромонтаж"
    assert service.version().dictionary_version >= 2


def test_apply_skills_targets_existing_skill(service: DictionaryService) -> None:
    target = resolve(service, "postgresql").skill_id
    applied = service.apply_skills(
        [
            ApplySkillInput(
                canonical="PostgreSQL",
                skill_type="TECH",
                target_skill_id=target,
                aliases=(ApplyAliasInput("постгрес 15", "ru"),),
            )
        ],
        "hr",
    )[0][0]
    assert resolve(service, "постгрес 15").skill_id == applied.id


def test_apply_skills_conflict_leaves_no_version(service: DictionaryService) -> None:
    before = service.version().dictionary_version
    target = resolve(service, "javascript").skill_id
    with pytest.raises(AliasConflict):
        service.apply_skills(
            [
                ApplySkillInput(
                    canonical="JavaScript",
                    skill_type="TECH",
                    target_skill_id=target,
                    aliases=(ApplyAliasInput("питон", "ru"),),
                )
            ],
            "hr",
        )
    assert service.version().dictionary_version == before


def resolve_qualification(service: DictionaryService, raw: str, as_of: str = ""):
    _, items = service.resolve_qualifications(
        [ResolveQualificationInput("q", raw, "RESUME")],
        as_of=as_of,
    )
    return items[0]


def test_qualification_group_aliases(service: DictionaryService) -> None:
    third = resolve_qualification(service, "3 группа по электробезопасности")
    roman = resolve_qualification(service, "III группа по электробезопасности")
    assert third.qualification_id is not None
    assert third.qualification_id == roman.qualification_id


def test_driver_licence_categories_are_distinct(service: DictionaryService) -> None:
    b = resolve_qualification(service, "права категории B")
    c = resolve_qualification(service, "водительские права категории C")
    assert b.qualification_id is not None
    assert c.qualification_id is not None
    assert b.qualification_id != c.qualification_id


def test_unknown_qualification_is_new(service: DictionaryService) -> None:
    item = resolve_qualification(service, "Свидетельство пилота")
    assert item.qualification_id is None
    assert item.method == "NEW"


def test_skill_relations_are_symmetric(service: DictionaryService) -> None:
    kafka = resolve(service, "Kafka").skill_id
    rabbit = resolve(service, "RabbitMQ").skill_id
    assert kafka and rabbit
    _, relations = service.get_relations([(kafka, rabbit)])
    assert len(relations) == 1
    assert relations[0].kind == "RELATED"
    _, reverse = service.get_relations([(rabbit, kafka)])
    assert reverse[0].kind == "RELATED"


def test_relation_version_is_part_of_resolver_version(service: DictionaryService) -> None:
    version = service.version()
    assert version.qualification_version >= 2
    assert version.relation_version >= 2
    assert f"-q{version.qualification_version}" in version.resolver_version
    assert f"-r{version.relation_version}" in version.resolver_version


def test_qualification_admin_operations(service: DictionaryService) -> None:
    qualification = service.create_qualification("Тестовая квалификация", "OTHER", "hr")
    assert qualification.canonical_name == "Тестовая квалификация"
    alias = service.add_qualification_alias(qualification.id, "тестовый допуск", "ru", "AUTO", "hr")
    assert alias.qualification_id == qualification.id
    updated = service.set_canonical_qualification_alias(qualification.id, alias.id, "hr")
    assert updated.canonical_name == "тестовый допуск"
    assert any(item.is_canonical and item.id == alias.id for item in updated.aliases)
    resolved = resolve_qualification(service, "тестовый допуск")
    assert resolved.qualification_id == qualification.id


def test_relation_admin_operations(service: DictionaryService) -> None:
    kafka = resolve(service, "Kafka").skill_id
    docker = resolve(service, "Docker").skill_id
    assert kafka and docker
    created = service.create_relation(kafka, docker, "RELATED", "hr")
    assert created.kind == "RELATED"
    items, total = service.list_relations(500, 0)
    assert total >= 4
    assert any(
        item.left_skill_id == created.left_skill_id
        and item.right_skill_id == created.right_skill_id
        for item in items
    )
    service.delete_relation(kafka, docker, "hr")
    _, remaining = service.get_relations([(kafka, docker)])
    assert remaining == ()
