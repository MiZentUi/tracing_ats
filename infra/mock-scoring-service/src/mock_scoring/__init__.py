"""Детерминированная заглушка контракта `scoring_service.v1`."""

from __future__ import annotations

from .server import run

__all__ = ["run"]


def main() -> None:
    run()
