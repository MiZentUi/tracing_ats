#!/usr/bin/env bash
# Демо hh-пайплайна: публикация вакансии в mock-hh → вебхук отклика → Score_pre.
set -euo pipefail

API_URL="${ATS_API_URL:-http://localhost:8080/api/v1}"
MOCK_HH_URL="${MOCK_HH_URL:-http://localhost:8090}"
VACANCY_ID="${VACANCY_ID:-00000000-0000-0000-0000-000000000001}"
RESUME_ID="${RESUME_ID:-resume-strong}"

json_value() {
  python3 -c "import json,sys; print(json.load(sys.stdin)$1)"
}

echo "1/3 Публикуем вакансию ${VACANCY_ID} на hh.ru"
PUBLICATION=$(curl -fsS -X POST "${API_URL}/vacancies/${VACANCY_ID}/hh-publication")
echo "${PUBLICATION}"
HH_VACANCY_ID=$(printf '%s' "${PUBLICATION}" | json_value "['hh_vacancy_id']")
if [ -z "${HH_VACANCY_ID}" ] || [ "${HH_VACANCY_ID}" = "None" ]; then
  echo "Публикация не завершена: проверьте, что шаблон подтверждён и mock-hh запущен." >&2
  exit 1
fi

echo "2/3 Отправляем отклик ${RESUME_ID} на hh-вакансию ${HH_VACANCY_ID}"
RESPONSE=$(curl -fsS -X POST "${MOCK_HH_URL}/__admin/respond" \
  -H 'Content-Type: application/json' \
  -d "{\"vacancy_id\":\"${HH_VACANCY_ID}\",\"resume_id\":\"${RESUME_ID}\"}")
echo "${RESPONSE}"
EVENT_ID=$(printf '%s' "${RESPONSE}" | json_value "['event_id']")

echo "3/3 Ждём Score_pre и показываем рейтинг"
for _ in $(seq 1 60); do
  RANKING=$(curl -fsS "${API_URL}/vacancies/${VACANCY_ID}/ranking?phase=PRELIMINARY" || true)
  if printf '%s' "${RANKING}" | grep -q '"score"'; then
    echo "${RANKING}" | python3 -m json.tool
    echo "event_id=${EVENT_ID}"
    exit 0
  fi
  sleep 2
done

echo "Score_pre не появился за 120 секунд. Проверьте логи ats-service." >&2
exit 1
