#!/usr/bin/env python3
"""Детерминированная OpenAI-совместимая заглушка для CI и демо.

Реальные ключи и внешняя сеть не нужны. Тела запросов не логируются.
"""

from __future__ import annotations

import json
import logging
import os
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

SCENARIO_RE = re.compile(r"MOCK_SCENARIO:\s*([a-z-]+)", re.IGNORECASE)
YEAR_RE = re.compile(r"(?:от|более)\s+(\d+)\s*(?:лет|года|год)", re.IGNORECASE)

SKILLS = [
    "JavaScript",
    "TypeScript",
    "PostgreSQL",
    "Kubernetes",
    "Spring Boot",
    "React Native",
    "Node.js",
    "Objective-C",
    "C++",
    "C#",
    ".NET",
    "Python",
    "Kotlin",
    "Java",
    "Docker",
    "Kafka",
    "Redis",
    "MongoDB",
    "SQL",
    "React",
    "Vue",
    "Go",
    "Git",
    "Linux",
    "JS",
    "Tauri",
]
SKILLS_SORTED = sorted(SKILLS, key=len, reverse=True)

STATE: dict[str, int] = {}
logger = logging.getLogger("mock_llm")


def scenario_of(text: str) -> str:
    match = SCENARIO_RE.search(text)
    return match.group(1).lower() if match else "ok"


def kind_of(line: str) -> str:
    lower = line.lower()
    if any(marker in lower for marker in ("научим", "обучим", "не обязательн", "без опыта")):
        return "OPTIONAL"
    if any(marker in lower for marker in ("будет плюсом", "желательно", "приветствуется")):
        return "NICE"
    return "MUST"


def find_skill(line: str) -> str | None:
    lower = line.lower()
    for skill in SKILLS_SORTED:
        if skill.lower() in lower:
            start = lower.index(skill.lower())
            return line[start : start + len(skill)]
    return None


def years_of(line: str) -> int | None:
    match = YEAR_RE.search(line)
    return int(match.group(1)) if match else None


def build_requirements(text: str) -> list[dict[str, Any]]:
    requirements: list[dict[str, Any]] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.upper().startswith("MOCK_SCENARIO:"):
            continue

        vague = "высоконагруженн" in line.lower() or "большие данные" in line.lower()
        if vague:
            requirements.append(
                {
                    "skill": None,
                    "kind": kind_of(line),
                    "years_required": None,
                    "quote": line,
                    "clarity_warning": "Расплывчато: укажите нагрузку (rps, объём данных) и роль",
                }
            )
            continue

        skill = find_skill(line)
        if skill is None:
            continue
        requirements.append(
            {
                "skill": skill,
                "kind": kind_of(line),
                "years_required": years_of(line),
                "quote": line,
                "clarity_warning": None,
            }
        )
    return requirements


def payload_for(text: str) -> dict[str, Any]:
    return {
        "schema_version": 2,
        "title": "Синтетическая вакансия",
        "requirements": build_requirements(text),
    }


def extract_vacancy_text(body: dict[str, Any]) -> str:
    messages = body.get("messages") or []
    for message in messages:
        if message.get("role") != "user":
            continue
        content = message.get("content") or ""
        match = re.search(
            r"<vacancy_text>(.*?)</vacancy_text>", content, re.DOTALL | re.IGNORECASE
        )
        if match:
            return match.group(1).strip()
        return content
    return ""


def completion(content: str, finish_reason: str = "stop") -> dict[str, Any]:
    return {
        "id": "chatcmpl-mock",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": "mock/mock-model",
        "choices": [
            {
                "index": 0,
                "message": {"role": "assistant", "content": content},
                "finish_reason": finish_reason,
            }
        ],
        "usage": {
            "prompt_tokens": 10,
            "completion_tokens": max(1, len(content) // 4),
            "total_tokens": 10 + max(1, len(content) // 4),
        },
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "mock-llm/1.0"

    def do_POST(self) -> None:  # noqa: N802
        started_at = time.perf_counter()
        self._started_at = started_at
        self._scenario = "unknown"
        if not self.path.rstrip("/").endswith("/chat/completions"):
            logger.info(
                "mock_llm_request method=POST path=%s status=404 scenario=unknown elapsed_ms=%.1f",
                self.path,
                (time.perf_counter() - started_at) * 1000,
            )
            self.send_error(404, "not found")
            return
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            body = json.loads(raw)
        except json.JSONDecodeError:
            self.json_response(400, {"error": {"message": "invalid request json"}})
            return

        text = extract_vacancy_text(body)
        scenario = scenario_of(text)
        self._scenario = scenario
        key = scenario
        STATE[key] = STATE.get(key, 0) + 1
        attempt = STATE[key]

        if scenario == "rate-limit-once" and attempt == 1:
            self.json_response(429, {"error": {"message": "provider overloaded", "type": "rate_limit"}})
            return
        if scenario == "rate-limit-always":
            self.json_response(429, {"error": {"message": "provider overloaded", "type": "rate_limit"}})
            return
        if scenario == "unauthorized":
            self.json_response(401, {"error": {"message": "invalid api key"}})
            return
        if scenario == "quota":
            self.json_response(402, {"error": {"message": "quota exhausted"}})
            return
        if scenario == "slow":
            time.sleep(float(os.environ.get("MOCK_SLOW_SECONDS", "5")))
        if scenario == "truncated":
            self.json_response(200, completion("", "length"))
            return
        if scenario == "always-invalid":
            self.json_response(200, completion("это не json"))
            return
        if scenario == "invalid-json" and attempt == 1:
            self.json_response(200, completion('{"schema_version": 2, "requirements": ['))
            return

        payload = payload_for(text)
        if scenario == "bad-quote":
            payload["requirements"].append(
                {
                    "skill": "Tauri",
                    "kind": "NICE",
                    "years_required": None,
                    "quote": "цитата, которой нет в тексте",
                    "clarity_warning": None,
                }
            )
        content = "\n\n" + json.dumps(payload, ensure_ascii=False)
        if scenario == "fenced":
            content = "\n\n```json\n" + json.dumps(payload, ensure_ascii=False) + "\n```"
        self.json_response(200, completion(content))

    def json_response(self, status: int, body: dict[str, Any]) -> None:
        logger.info(
            "mock_llm_request method=POST path=%s status=%s scenario=%s elapsed_ms=%.1f",
            self.path,
            status,
            getattr(self, "_scenario", "unknown"),
            (time.perf_counter() - getattr(self, "_started_at", time.perf_counter())) * 1000,
        )
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, fmt: str, *args: Any) -> None:
        logger.debug("mock_llm_http %s", fmt % args)


def main() -> None:
    port = int(os.environ.get("MOCK_LLM_PORT", "8089"))
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    logger.info("mock_llm_started port=%s", port)
    server.serve_forever()


if __name__ == "__main__":
    main()
