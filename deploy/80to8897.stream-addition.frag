# FR-NX-001 — location-блок для /api/public/zakroma/stream.
#
# ВНИМАНИЕ: файл `80to8897` живёт ТОЛЬКО на прод-сервере (см. AGENTS.md,
# раздел «nginx 80to8897»). В репо его нет — он не симлинк, а отдельный
# файл, и его обновление требует ручного шага через
# `tools/deploy-nginx-stream.sh` (T026).
#
# Этот фрагмент — КОНКРЕТНЫЕ директивы, которые должны быть добавлены
# в существующий конфиг `/etc/nginx/sites-enabled/80to8897` на проде.
#
# Куда вставлять: рядом с существующими `location /api/public/...` и
# `location /api/replacesymbolsinsong` (см. `karaoke-public/nginx_karaoke-public.conf`
# для шаблона структуры на localhost). Имя upstream (`karaoke-web-upstream`
# по умолчанию) и `proxy_set_header` директивы должны СОВПАДАТЬ с теми,
# что используются в существующих location-блоках того же файла.
#
# FR-NX-002: после правки — `nginx -t` (syntax check) +
# `systemctl reload nginx`. Оба шага — через `tools/deploy-nginx-stream.sh`.

# === BEGIN 80to8897.stream-addition.frag ===

# Real-time NDJSON chunked-stream для Закромов (specs/181-zakroma-author-load-progress).
# Без proxy_buffering/gzip/proxy_cache OFF фронт получит весь ответ одним блоком после полной
# отдачи backend — никакого «real-time» не будет.
# proxy_read_timeout 300s — для авторов с очень большим каталогом (до ~1000 песен).
location /api/public/zakroma/stream {
    proxy_buffering off;
    gzip off;
    proxy_cache off;
    proxy_read_timeout 300s;
    # Эти `proxy_set_header` MUST совпадать с существующими location /api/public/*,
    # иначе backend получит неправильные rhost/remote_ip (баг в IP-based логике
    # `onlyPublishedFor` в PublicApiController).
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    # Upstream должен совпадать с именем в существующих location /api/public/* —
    # выяснить через `grep -n pass /etc/nginx/sites-enabled/80to8897` и заменить.
    proxy_pass http://karaoke-web-upstream;
}

# === END 80to8897.stream-addition.frag ===
