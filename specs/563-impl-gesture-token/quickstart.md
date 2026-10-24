# Quickstart: проверка 563 — закрытие обхода оплаты по «скрытому жесту» (спека 557)

**Branch**: `563-impl-gesture-token` | **Issue**: OpenProject #358 (аудит BR-018)

## Предпосылки

- Работа в worktree: `cd /home/nsa/Karaoke/.worktrees/impl358`.
- Gradle — с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle` (жёсткое правило `AGENTS.md`).
- Фронт — только из каталога фронтенда: `cd karaoke-public && npm run …` (в корне нет `package.json`).
- Прод-контур **не трогаем**: проверки 1–3 выполняются автоматическими тестами на локальной
  копии кода; ручная проверка на бою — только по отдельному решению владельца.

---

## Проверка 1. Анонимный «жест» не открывает полный доступ (US1)

**Что проверяем**: публичный эндпоинт событий не возвращает значения доступа, и анонимный
запрос полного аудио платной песни отклоняется.

```bash
cd /home/nsa/Karaoke/.worktrees/impl358
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test \
  --tests 'com.svoemesto.karaokeweb.controllers.PublicPlayerControllerAccessTest'
```

**Ожидаемо**:

- **До правки** тест падает: `meta` содержит токен (жест выдал доступ) и/или анонимный
  запрос полного стема отдаёт полные байты.
- **После правки** — PASS: в ответе `POST /api/public/events` нет значения доступа
  (`meta` — `null`), анонимный запрос полного стема → отказ, а «полный» токен без
  действующего права даёт демо-фрагмент, а не полный файл.

**Ручная проверка на работающем экземпляре** (не прод; локальный стенд):

```bash
# 1. Три «жеста» по полю метаданных платной песни (анонимно)
for i in 1 2 3; do
  curl -s -X POST http://localhost:7906/api/public/events \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data 'eventType=clickToLink&linkType=songMeta&linkName=key&songId=<ID>&shiftKey=true&clientId=anon-1'
  echo
done
# Ожидаемо: {"ok":true,"meta":null} — значения доступа нет (ДО правки в meta был токен)

# 2. Запрос полного стема этой песни без токена
curl -s -o /dev/null -w '%{http_code}\n' \
  "http://localhost:7906/api/public/player/<ID>/fileminus.mp3"
# Ожидаемо: 404
```

---

## Проверка 2. Легитимные пути продолжают работать (US2)

```bash
cd /home/nsa/Karaoke/.worktrees/impl358
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test \
  --tests 'com.svoemesto.karaokeweb.services.PlayerAccessServiceTest'
```

**Ожидаемо** (PASS) — по каждому основанию доступа:

| Основание | Ожидаемое решение | Спека |
|---|---|---|
| Песня публично доступна (всегда бесплатно / эфир) | `full` | FR-003 |
| Действующий премиум | `full` | FR-003, US2.1 |
| Бессрочная подписка на эту песню | `full` | FR-003, US2.2 |
| Подписка на **другую** песню | `demo` | US2.2 |
| Песня в эфире, аноним | `full` | US2.3 |
| Гостевая share-сессия | `full` | FR-008 |
| Предпросмотр по своему заданию редактора | `full` | FR-009 |
| Задание редактора отозвано | отказ | FR-009 |
| Демо-токен (штатный демо-режим) | `demo` | FR-005 |
| «Полный» токен без действующего права | `demo` (не `full`) | FR-004 |
| Токен от другой песни | отказ | FR-006 |
| Без токена и без сессии | отказ | FR-010 |

---

## Проверка 3. Отказы видны в журнале (US3)

```bash
cd /home/nsa/Karaoke/.worktrees/impl358
grep -rn 'player:access:' --include='*.kt' karaoke-web/src/main/kotlin
```

**Ожидаемо**: две записи — `player:access:denied` (WARN) и `player:access:demo` (INFO);
в записи есть `songId=` и `reason=`, и **нет** самого токена и содержимого аудио (FR-012).

Ручная проверка на локальном стенде:

```bash
docker logs karaoke-web 2>&1 | grep 'player:access:'
# Ожидаемо: записи об отказе с songId= и reason=, без значения токена
```

---

## Проверка 4. Полный набор проверок проекта

```bash
cd /home/nsa/Karaoke/.worktrees/impl358

# Бэкенд
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test :karaoke-app:test

# Фронт (публичный — затронут)
cd karaoke-public && npm run lint && npm run build && npm run format:check && cd ..

# Governance
bash tools/check-knowledge-structure.sh
python3 tools/check-doc-references.py
bash tools/check-feature-doc.sh docs/features/*.md
bash tools/check-subagent-isolation.sh
```

---

## Проверка 5. Отсутствие секретов в изменениях

```bash
cd /home/nsa/Karaoke/.worktrees/impl358
git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'
# MUST быть пусто
```

---

## Что проверить после развёртывания на боевой контур (отдельное решение владельца)

1. Развёртывание `karaoke-web` должно сопровождаться **перезапуском** контейнера: токены живут
   в памяти процесса, поэтому ранее выданные (в т.ч. по жесту) переживут перезапуск максимум
   30 минут (Edge Case спеки). Перезапуск `karaoke-web` на `nsa-i9` — по согласию владельца.
2. Анонимный контрольный обход: три запроса в `POST /api/public/events` → `meta` пустой;
   запрос полного стема → 404/демо-фрагмент.
3. Заглянуть в журнал: `player:access:denied` / `player:access:demo` — записи есть, токенов нет.
4. Проверить, что премиум-пользователь по-прежнему слышит полную песню (живой премиум),
   а слушатель в эфире — полную песню без действий, а посетитель без права — демо-фрагмент.
