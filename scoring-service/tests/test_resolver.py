from __future__ import annotations

from api.dictionary.client import DictionaryUnavailable, Resolution, ResolvedName
from api.scoring.resolver import SkillResolver


class FakeClient:
    def __init__(self, items: dict[str, ResolvedName]) -> None:
        self._items = items

    def resolve(self, names: list[tuple[str, str]]) -> Resolution:
        return Resolution("d1", "DICTIONARY", self._items)

    def get_relations(self, pairs: list[tuple[str, str]]) -> dict[tuple[str, str], str]:
        return {}


def test_local_fallback_is_exact_only() -> None:
    resolver = SkillResolver(None, enabled=False)
    resolution = resolver.resolve([("JS", "RESUME"), ("javascript", "VACANCY")])
    assert resolution.mode == "EXACT_FALLBACK"
    assert resolver.score(resolution.identity("JS"), resolution.identity("javascript")) == 0.0


def test_dictionary_identity_wins() -> None:
    items = {
        "JS": ResolvedName("JS", "skill-1", "JavaScript", "js", "", "ALIAS_EXACT"),
        "JavaScript": ResolvedName(
            "JavaScript", "skill-1", "JavaScript", "javascript", "", "ALIAS_EXACT"
        ),
    }
    resolver = SkillResolver(FakeClient(items), enabled=True)
    resolution = resolver.resolve([("JS", "RESUME"), ("JavaScript", "VACANCY")])
    assert resolution.mode == "DICTIONARY"
    assert resolver.score(resolution.identity("JS"), resolution.identity("JavaScript")) == 1.0


def test_dictionary_unavailable_degrades(monkeypatch) -> None:
    class BrokenClient:
        def resolve(self, names):
            raise DictionaryUnavailable("down")

    resolver = SkillResolver(BrokenClient(), enabled=True)
    resolution = resolver.resolve([("Python", "RESUME")])
    assert resolution.mode == "EXACT_FALLBACK"
    assert resolution.identity("Python").keys == ("python",)


def test_unknown_skills_match_by_key() -> None:
    resolver = SkillResolver(None, enabled=False)
    resolution = resolver.resolve([("Postgres", "RESUME"), ("postgres", "VACANCY")])
    assert resolver.score(resolution.identity("Postgres"), resolution.identity("postgres")) == 1.0


def test_dictionary_returns_relation_class() -> None:
    class RelationClient(FakeClient):
        def get_relations(self, pairs):
            return {("skill-1", "skill-2"): "RELATED"}

    items = {
        "Kafka": ResolvedName("Kafka", "skill-1", "Kafka", "kafka", "", "ALIAS_EXACT"),
        "RabbitMQ": ResolvedName("RabbitMQ", "skill-2", "RabbitMQ", "rabbitmq", "", "ALIAS_EXACT"),
    }
    resolver = SkillResolver(RelationClient(items), enabled=True)
    resolution = resolver.resolve([("Kafka", "VACANCY"), ("RabbitMQ", "RESUME")])
    assert resolution.relation("skill-1", "skill-2") == "RELATED"
    assert resolution.relation("skill-2", "skill-1") == "RELATED"


def test_relation_policy_maps_classes_to_score() -> None:
    from api.scoring.policies import RelationPolicy

    policy = RelationPolicy()
    assert policy.score("SAME_GROUP") == 0.5
    assert policy.score("RELATED") == 0.5
    assert policy.score(None) == 0.0
