#!/usr/bin/env python3
# ruff: noqa: RUF001
"""Ручная проверка `ScoringService/ExtractResume` без полного пайплайна.

Скрипт отправляет только текст резюме в scoring-service и печатает полный
`ExtractResumeResponse` в JSON. Вакансия, БД, MinIO и Java-сервис не нужны.

Примеры:
    uv run python scripts/extract_resume.py --file resume.txt
    uv run python scripts/extract_resume.py --text "Python, Docker, Linux"
    cat resume.txt | uv run python scripts/extract_resume.py
    uv run python scripts/extract_resume.py --target 127.0.0.1:8082
"""

from __future__ import annotations

import argparse
import os
import sys
from datetime import date

import grpc
from google.protobuf import json_format
from google.type import date_pb2

from scoring_service.v1 import scoring_pb2_grpc
from scoring_service.v1.models import messages_pb2

DEFAULT_TARGET = os.getenv("SCORING_GRPC_TARGET", "127.0.0.1:8082")
DEFAULT_TIMEOUT = 120.0

EXAMPLE_RESUME = """\
Алексей Смирнов
Москва, +7 (900) 000-00-00, alexey.smirnov@example.com

Опыт работы

2021–2026 — ООО «Дата-Центр Сервис», инженер DevOps
- Обслуживал 120+ серверов и сетевое оборудование в двух дата-центрах.
- Автоматизировал развёртывание через Ansible и Docker.
- Настроил мониторинг Prometheus и Grafana.

Квалификации и допуски
- III группа по электробезопасности до 1000 В.
- Водительские права категории B.

Навыки
Linux, Docker, Ansible, TCP/IP, Prometheus, Grafana, Git.

Образование
Московский политехнический университет, «Информатика и вычислительная
техника», бакалавр, 2018.
"""


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Вызвать ExtractResume и напечатать ответ в JSON.",
    )
    source = parser.add_mutually_exclusive_group()
    source.add_argument(
        "--text",
        help="Текст резюме строкой. Если не задан --file и stdin не TTY, читается stdin.",
    )
    source.add_argument(
        "--file",
        help="Путь к текстовому файлу с резюме (UTF-8).",
    )
    parser.add_argument(
        "--target",
        default=DEFAULT_TARGET,
        help=f"gRPC target scoring-service (по умолчанию {DEFAULT_TARGET}).",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=DEFAULT_TIMEOUT,
        help=f"Таймаут вызова в секундах (по умолчанию {DEFAULT_TIMEOUT:g}).",
    )
    parser.add_argument(
        "--reference-date",
        help="Дата расчёта стажа в формате YYYY-MM-DD. По умолчанию — сегодня.",
    )
    parser.add_argument(
        "--tls",
        action="store_true",
        help="Использовать TLS для gRPC-соединения.",
    )
    return parser.parse_args(argv)


def read_resume_text(args: argparse.Namespace) -> str:
    if args.text is not None:
        text = args.text
    elif args.file is not None:
        with open(args.file, encoding="utf-8") as source:
            text = source.read()
    elif not sys.stdin.isatty():
        text = sys.stdin.read()
    else:
        text = EXAMPLE_RESUME

    if not text.strip():
        raise ValueError("Текст резюме пуст")
    return text


def parse_reference_date(value: str | None) -> date_pb2.Date | None:
    if value is None:
        return None
    parsed = date.fromisoformat(value)
    return date_pb2.Date(year=parsed.year, month=parsed.month, day=parsed.day)


def build_request(
    text: str,
    reference_date: date_pb2.Date | None,
) -> messages_pb2.ExtractResumeRequest:
    request = messages_pb2.ExtractResumeRequest(
        resume=messages_pb2.UnprocessedResume(text=text),
    )
    if reference_date is not None:
        request.reference_date.CopyFrom(reference_date)
    return request


def make_channel(target: str, use_tls: bool) -> grpc.Channel:
    if use_tls:
        return grpc.secure_channel(target, grpc.ssl_channel_credentials())
    return grpc.insecure_channel(target)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        text = read_resume_text(args)
        request = build_request(text, parse_reference_date(args.reference_date))
    except (OSError, ValueError) as exc:
        print(f"Ошибка ввода: {exc}", file=sys.stderr)
        return 2

    try:
        with make_channel(args.target, args.tls) as channel:
            stub = scoring_pb2_grpc.ScoringServiceStub(channel)
            response = stub.ExtractResume(request, timeout=args.timeout)
    except grpc.RpcError as exc:
        code = exc.code().name if exc.code() is not None else "UNKNOWN"
        details = exc.details() or ""
        print(f"gRPC error: {code}: {details}", file=sys.stderr)
        return 1

    output = json_format.MessageToJson(
        response,
        indent=2,
        ensure_ascii=False,
        preserving_proto_field_name=True,
    )
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
