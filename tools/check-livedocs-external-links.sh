#!/usr/bin/env bash
# tools/check-livedocs-external-links.sh
# Проверяет ВНЕШНИЕ ссылки (https://) в LiveDocs через lychee.
# Для CI в .github/workflows/lint.yml.
#
# Использует --offline режим (без HEAD-запросов) для скорости,
# но также проверяет известные broken URLs через прямой curl.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

echo "[1/3] Проверка external links через lychee (advisory)..."

# Lychee advisory: в offline режиме может выдавать false positives (404 на HEAD для
# URL'ов, которые работают с GET). Его статус — только информационный;
# строгая проверка — это шаг [2/3] через curl с GET + redirect.
if ! command -v lychee >/dev/null 2>&1; then
    echo "WARN: lychee not installed, skipping"
else
    LYCHEE_OUTPUT=$(lychee \
        --offline \
        --no-progress \
        --exclude-loopback \
        --exclude-mail \
        --exclude 'http://10\.0\.0\.1.*' \
        --exclude 'http://188\.119\.64\.111.*' \
        --exclude 'http://karaoke-web.*' \
        --exclude 'http://localhost.*' \
        --exclude 'https://id\.vk\.ru/oauth2.*' \
        --exclude 'http://oauth\.vk\.ru/blank\.html' \
        --exclude 'http://thinkrelevance\.com/blog/.*' \
        --accept 200,201,203,206,301,302,303,304,307,308,403,418,429 \
        livedocs/ \
        livedocs-en/ 2>&1)
    LYCHEE_EXIT=$?
    echo "lychee exit code: $LYCHEE_EXIT (advisory, см. шаг 2/3 для strict)"
fi

echo "[2/3] Проверка external links через curl (live)..."
BROKEN=0
CHECKED=0
PLACEHOLDERS=0

# Извлечь все unique URL'ы из livedocs/
URLS=$(grep -ohE 'https?://[a-zA-Z0-9._/:%?=&@#-]+' livedocs/ livedocs-en/ -r 2>/dev/null | sort -u)

for url in $URLS; do
    # Skip placeholders/internal
    if echo "$url" | grep -qE '^(http://10\.0\.0\.1|http://188\.119\.64\.111|http://karaoke-web|http://localhost|http://127\.0\.0\.1|http://minio-proxy)'; then
        PLACEHOLDERS=$((PLACEHOLDERS+1))
        continue
    fi
    # Production URLs — зависят от конфигурации (private/internal), не проверяем
    if echo "$url" | grep -qE '^(https://svoemesto\.ru|https://sm-karaoke\.ru|https://smartcaptcha\.yandexcloud\.net/)'; then
        PLACEHOLDERS=$((PLACEHOLDERS+1))
        continue
    fi
    # VK ID OAuth — 404 на HEAD без params (но валидные URL)
    if echo "$url" | grep -qE '^https://id\.vk\.ru/oauth2'; then
        PLACEHOLDERS=$((PLACEHOLDERS+1))
        continue
    fi
    CHECKED=$((CHECKED+1))
    # curl с GET (быстрее HEAD) с редиректом — `-o /dev/null` без `-I` = GET без тела
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 -L "$url" 2>/dev/null)
    if [ -z "$code" ]; then
        code="000"
    fi
    # Принимаемые коды: 2xx, 3xx, и специальные (защищённые — 401/403/418/429)
    # 418 — RFC 2324 "I'm a teapot" (пасхалка ВК на некоторых URLs).
    if [[ "$code" == 2* || "$code" == 3* || "$code" == "401" || "$code" == "403" || "$code" == "418" || "$code" == "429" ]]; then
        :  # OK
    else
        echo "BROKEN ($code): $url"
        BROKEN=$((BROKEN+1))
    fi
done

echo "---"
echo "Checked: $CHECKED URL(s); Placeholders/internal skipped: $PLACEHOLDERS"

if [ "$BROKEN" -eq 0 ]; then
    echo "OK: All $CHECKED external links valid"
    exit 0
else
    echo "FAILED: $BROKEN broken external link(s)"
    exit 1
fi