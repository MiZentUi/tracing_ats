from __future__ import annotations

from dictionary_service.normalization import (
    alias_keys,
    fold_homoglyphs,
    keys_for_raw,
    lemmatize,
    normalize_base,
)


def test_case_whitespace_and_edge_punctuation() -> None:
    assert normalize_base(" JS ") == "js"
    assert normalize_base("«Python»") == "python"
    assert normalize_base("Node.js.") == "node.js"
    assert normalize_base("C++") == "c++"
    assert normalize_base(".NET") == ".net"


def test_yo_and_e_are_one_key() -> None:
    assert normalize_base("учёт") == "учет"
    assert keys_for_raw("учёта")[0] == keys_for_raw("учета")[0]


def test_homoglyphs_require_ascii_signal() -> None:
    assert fold_homoglyphs("1С") == "1c"
    assert fold_homoglyphs("С++") == "c++"
    assert fold_homoglyphs("Еxcel") == "excel"
    assert fold_homoglyphs("сок") == "сок"
    assert fold_homoglyphs("Сок") == "Сок"


def test_lemma_only_for_cyrillic_tokens() -> None:
    assert lemmatize("кубернетесом") == "кубернетес"
    assert lemmatize("учета") == "учет"
    assert lemmatize("Еxcel") == "еxcel"


def test_keys_order_and_raw_only() -> None:
    raw, folded, _lemma, _lemma_folded = keys_for_raw("1С")
    assert raw == "1с"
    assert folded == "1c"
    assert "LEMMA" in alias_keys("кубернетесом")
    assert alias_keys("кубы", "RAW_ONLY") == {"RAW": "кубы"}
