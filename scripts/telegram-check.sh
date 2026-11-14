#!/usr/bin/env bash
# Проверка Telegram-бота: отправляет одно тестовое сообщение в указанный чат.
# Токен и chat_id берутся из окружения и не печатаются.
set -euo pipefail

: "${ATS_TELEGRAM_BOT_TOKEN:?Задай ATS_TELEGRAM_BOT_TOKEN (токен из @BotFather)}"
: "${ATS_TELEGRAM_CHAT_ID:?Задай ATS_TELEGRAM_CHAT_ID}"

TEXT="${1:-Tracing ATS: проверка Telegram-алертов}"

RESPONSE=$(curl -fsS -X POST \
  "https://api.telegram.org/bot${ATS_TELEGRAM_BOT_TOKEN}/sendMessage" \
  --data-urlencode "chat_id=${ATS_TELEGRAM_CHAT_ID}" \
  --data-urlencode "text=${TEXT}" \
  --data-urlencode "disable_web_page_preview=true")

if printf '%s' "${RESPONSE}" | grep -q '"ok":true'; then
  echo "Telegram: сообщение отправлено"
else
  echo "Telegram: отправка не удалась" >&2
  exit 1
fi
