from __future__ import annotations

from api.dictionary.client import QualificationResolution, ResolvedQualificationName
from api.scoring.qualification import QualificationResolver
from scoring_service.v1.models.features import qualification_pb2


class FakeQualificationClient:
    def __init__(self, items: dict[str, ResolvedQualificationName]) -> None:
        self._items = items

    def resolve_qualifications(self, labels):
        return QualificationResolution("d1-q1-r1", "DICTIONARY", self._items)


def test_dictionary_matches_qualification_id() -> None:
    items = {
        "права категории B": ResolvedQualificationName(
            "права категории B",
            "q-b",
            "Водительские права категории B",
            "права категории b",
            "",
            "ALIAS_EXACT",
        ),
        "водительские права категории B": ResolvedQualificationName(
            "водительские права категории B",
            "q-b",
            "Водительские права категории B",
            "водительские права категории b",
            "",
            "ALIAS_EXACT",
        ),
    }
    resolver = QualificationResolver(FakeQualificationClient(items), enabled=True)
    resolution = resolver.resolve(
        [("права категории B", "VACANCY"), ("водительские права категории B", "RESUME")]
    )
    assert resolution.mode == "DICTIONARY"
    assert resolution.matches(
        resolution.identity("права категории B"),
        resolution.identity("водительские права категории B"),
    )


def test_catalog_fallback_matches_aliases() -> None:
    catalog = qualification_pb2.QualificationCatalog(
        version="test-v1",
        aliases=[
            qualification_pb2.QualificationAlias(
                alias_normalized="3 группа по электробезопасности",
                canonical_key="electrical_safety_group_iii",
                canonical_label="III группа по электробезопасности",
            ),
            qualification_pb2.QualificationAlias(
                alias_normalized="iii группа по электробезопасности",
                canonical_key="electrical_safety_group_iii",
                canonical_label="III группа по электробезопасности",
            ),
        ],
    )
    resolver = QualificationResolver(None, enabled=False)
    resolution = resolver.resolve(
        [
            ("3 группа по электробезопасности", "VACANCY"),
            ("III группа по электробезопасности", "RESUME"),
        ],
        catalog,
    )
    assert resolution.mode == "EXACT_FALLBACK"
    assert resolution.matches(
        resolution.identity("3 группа по электробезопасности"),
        resolution.identity("III группа по электробезопасности"),
    )
