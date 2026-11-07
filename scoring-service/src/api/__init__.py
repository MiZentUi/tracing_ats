"""Online ML scoring service."""

from .server import run

__all__ = ["run"]


def main() -> None:
    run()
