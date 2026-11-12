"""Даты, стаж и давность. Все функции чистые: «сегодня» передаётся параметром."""

from __future__ import annotations

import calendar
from collections.abc import Iterable
from datetime import date

from google.type import date_pb2

from scoring_service.v1.models.features import experience_pb2

_DAYS_PER_YEAR = 365.25


def resolve_reference_date(value: date_pb2.Date | None, today: date) -> date:
    """Вернуть дату из protobuf или `today`, если она не задана."""
    if value is None or not value.year:
        return today
    month = value.month or 1
    day = value.day or 1
    try:
        return date(value.year, month, day)
    except ValueError:
        return date(value.year, 1, 1)


def start_date(value: date_pb2.Date | None) -> date | None:
    """Начало периода. Неполная дата трактуется как начало периода."""
    if value is None or not value.year:
        return None
    month = value.month or 1
    day = value.day or 1
    try:
        return date(value.year, month, day)
    except ValueError:
        return date(value.year, 1, 1)


def end_date(value: date_pb2.Date | None, reference: date) -> date | None:
    """Конец периода. Незаданный конец означает «по reference_date»."""
    if value is None or not value.year:
        return reference
    month = value.month or 12
    day = value.day or calendar.monthrange(value.year, month)[1]
    try:
        return date(value.year, month, day)
    except ValueError:
        return date(value.year, 12, 31)


def years_between(start: date, end: date) -> float:
    """Длительность в годах, не меньше нуля."""
    return max(0.0, (end - start).days / _DAYS_PER_YEAR)


def experience_years(
    item: experience_pb2.PriorExperiencePerResume,
    reference: date,
) -> float | None:
    """Стаж одного места работы или `None`, если начало неизвестно."""
    begin = start_date(item.begin)
    if begin is None:
        return None
    end = end_date(item.end, reference)
    if end is None:
        return None
    return years_between(begin, end)


def total_experience_years(
    items: Iterable[experience_pb2.PriorExperiencePerResume],
    reference: date,
) -> float | None:
    """Суммарный стаж без двойного счёта пересекающихся периодов."""
    intervals: list[tuple[date, date]] = []
    for item in items:
        begin = start_date(item.begin)
        if begin is None:
            continue
        end = end_date(item.end, reference)
        if end is None or end < begin:
            continue
        intervals.append((begin, end))

    if not intervals:
        return None

    intervals.sort()
    merged: list[list[date]] = []
    for begin, end in intervals:
        if not merged or begin > merged[-1][1]:
            merged.append([begin, end])
            continue
        if end > merged[-1][1]:
            merged[-1][1] = end

    return sum(years_between(begin, end) for begin, end in merged)
