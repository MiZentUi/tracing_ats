#!/usr/bin/env bash
# Демо словаря навыков: вакансия с JS/Tauri, очередь NEW, создание навыка,
# резюме и рейтинг вакансий. Требует запущенный docker compose.
set -euo pipefail

BASE="${ATS_URL:-http://localhost:8080}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RESUME="$ROOT/scripts/demo-resume.txt"

echo "1. Версия словаря"
curl -s "$BASE/api/v1/admin/dictionary/version"
echo

echo "2. Создаём вакансию: JS, Tauri, PostgreSQL"
TEXT=$'Требования:\n- JS от 2 лет\n- Tauri\n- PostgreSQL'
VACANCY_JSON="$(
  curl -s -X POST "$BASE/api/v1/vacancies" \
    -F specialization=backend -F level=middle -F 'title=Demo dictionary' \
    -F "text=$TEXT"
)"
echo "$VACANCY_JSON" | python3 -m json.tool
VACANCY_ID="$(
  echo "$VACANCY_JSON" |
    python3 -c 'import sys,json; print(json.load(sys.stdin)["vacancy_id"])'
)"

echo "3. Очередь NEW"
REVIEW_JSON="$(curl -s "$BASE/api/v1/admin/dictionary/review-items?status=PENDING")"
echo "$REVIEW_JSON" | python3 -m json.tool
TARGET_JSON="$(
  echo "$REVIEW_JSON" |
    python3 -c 'import sys,json
items=json.load(sys.stdin)
target=next((item for item in items
             if item["key"].strip("- ") == "tauri"
             or any("tauri" in sample.lower() for sample in item["samples"])), None)
print(json.dumps(target or {}, ensure_ascii=False))'
)"
REVIEW_ID="$(echo "$TARGET_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id", ""))')"
SUGGESTED_ID="$(
  echo "$TARGET_JSON" |
    python3 -c 'import sys,json; print(json.load(sys.stdin).get("suggestedSkillId") or "")'
)"

if [ -n "$REVIEW_ID" ] && [ -n "$SUGGESTED_ID" ]; then
  echo "4. HR подтверждает подсказку: Tauri"
  curl -s -X POST "$BASE/api/v1/admin/dictionary/review-items/$REVIEW_ID/resolve" \
    -H 'Content-Type: application/json' \
    -d "{\"action\":\"ALIAS\",\"skillId\":\"$SUGGESTED_ID\"}" |
    python3 -m json.tool
elif [ -n "$REVIEW_ID" ]; then
  echo "4. HR создаёт навык Tauri"
  curl -s -X POST "$BASE/api/v1/admin/dictionary/review-items/$REVIEW_ID/resolve" \
    -H 'Content-Type: application/json' \
    -d '{"action":"NEW_SKILL","canonicalName":"Tauri","skillType":"TECH"}' |
    python3 -m json.tool
else
  echo "4. Tauri уже в словаре или записи нет — пропускаем"
fi

echo "5. Загружаем резюме"
UPLOAD_JSON="$(
  curl -s -X POST "$BASE/api/v1/resumes" -F "files=@$RESUME"
)"
echo "$UPLOAD_JSON" | python3 -m json.tool
RESUME_ID="$(
  echo "$UPLOAD_JSON" |
    python3 -c 'import sys,json; print(json.load(sys.stdin)["resumes"][0]["resumeId"])'
)"

echo "6. Ждём MATCHED"
for _ in $(seq 1 30); do
  STATUS="$(
    curl -s "$BASE/api/v1/resumes/$RESUME_ID" |
      python3 -c 'import sys,json; print(json.load(sys.stdin)["status"])'
  )"
  [ "$STATUS" = "MATCHED" ] && break
  [ "$STATUS" = "MATCH_FAILED" ] && break
  sleep 2
done
echo "status=$STATUS"

echo "7. Рейтинг вакансий"
curl -s "$BASE/api/v1/resumes/$RESUME_ID/ranking" | python3 -m json.tool

echo "8. Отчёт по Demo dictionary"
curl -s "$BASE/api/v1/resumes/$RESUME_ID/vacancies/$VACANCY_ID/report" |
  python3 -m json.tool
