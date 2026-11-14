"""Local stateless PII sanitizer."""

from .server import run

__all__ = ["run"]


def main() -> None:
    run()
