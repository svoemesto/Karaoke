# Стенд Zot — результаты замера (тикет #275)

**Дата**: 2026-10-11
**Стенд**: `nsa-i9`, контейнер `zot-stand`, образ `ghcr.io/project-zot/zot:v2.1.22`
**Конфигурация**: `.scratch/karaoke/registry/stand/config.json` (черновик из
разведки, §2, принят Zot **без единой правки** — `retention`, `gcDelay`,
`gcInterval`, `syncTimeout`, `failDelay`, `compat` валидны в v2.1.22)
**Провальный вариант конфига сохранён**: `config-blanket-ondemand.json`

## 1. RSS — главный вопрос тикета

Официальных требований к памяти нет ни у Zot, ни у `distribution`. Замерено:

| Состояние | RSS |
|---|---|
| Сразу после старта (пустое хранилище) | **49.9 МиБ** |
| После pull-through `postgres:16` | 65.4 МиБ |
| **Пик во время push образа 5.36 ГБ** | **114.3 МиБ** |
| После полного цикла (push + pull-through) | 69.3 МиБ |

**Вывод**: Zot не буферизует слои в памяти — 5.4 ГБ прошли через него, подняв RSS
до 114 МиБ. В бюджет прод-хоста (3.8 ГБ рядом с `karaoke-web` heap 1200m) влезает
с запасом. **План Б (`registry:3`) не нужен.**

## 2. Сохраняется ли upstream-путь — ДА

`docker pull localhost:5500/library/postgres:16` → в каталоге появился ровно
`library/postgres` с тегом `16`. То же для `library/nginx:stable`.

**Вывод**: при `onDemand` **без** `destination`/`stripPrefix` upstream-путь
сохраняется. `registry-mirrors` на клиенте не промахнётся:
`docker pull postgres:16` попадёт в `/v2/library/postgres/...`.

## 3. НАЙДЕНА ЛОВУШКА: `onDemand` без `content` ломает push

С конфигом из черновика (`onDemand: true`, **без** `content`) push **нашего**
образа падает:

```
unknown: unexpected status from HEAD request to
http://localhost:5500/v2/karaoke/karaoke-app/manifests/sha256:…: 503 Service Unavailable
```

Причина в логах Zot:

```
failed to get remote manifest … reference="registry-1.docker.io/karaoke/karaoke-app@sha256:…"
error="failed to request manifest head …: unauthorized"
on-demand image sync failed … "unauthorized access. check credentials"
```

**Механизм**: `docker push` перед заливкой манифеста делает `HEAD` на манифест.
Zot с `onDemand` на всё (`**`) трактует **любой** промах как «сходить наверх» —
включая push нового приватного образа. Идёт на `docker.io/karaoke/karaoke-app`,
получает `unauthorized`, возвращает **503** — и push обрывается. Четыре слоя при
этом уже залиты, то есть registry остаётся с мусором от несостоявшегося push.

**Лечение** (проверено): задать `extensions.sync.registries[].content` —
allow-list публичных namespace:

```json
"content": [{ "prefix": "library/**" }]
```

После этого:
- полный push нового образа с новыми слоями (`karaoke/nginx-own:1`) — **успешно**, 2.3 с;
- push `karaoke/karaoke-app:1` (5.4 ГБ) — **успешно**, манифест залит;
- pull-through `library/nginx:stable` — **по-прежнему работает**, 12.5 с.

**Следствие для спеки**: `content`-allow-list обязателен, а не опционален. Это
ещё и поверхность безопасности: список определяет, что Zot вправе тянуть из
интернета по запросу клиента.

**Следствие для черновика разведки**: формулировка «`onDemand: true` без
`pollInterval`/`content` — sync is enabled in on demand mode for any image»
описывает ровно ту конфигурацию, которая ломает push. Черновик §2 требует правки.

## 4. Авторизация — регистр полностью закрыт

| Запрос | Код |
|---|---|
| `/v2/` без кредов | 401 |
| `/v2/_catalog` без кредов | 401 |
| `/v2/` с неверным паролем | 401 |
| `/v2/` с верным паролем | 200 |

Пароль стенда сгенерирован bcrypt'ом **локально** (`python3 -c "import crypt"`
→ `$2b$12$…`), без внешнего образа `httpd:2` — то есть рекомендация разведки
тянуть `httpd:2` для `htpasswd` не обязательна.

## 5. Мелочи, полезные спеке

- **UI и search выключены без конфига**: в логах `skip enabling the ui route as
  the config prerequisites are not met` и то же для search. Касается тикета #277.
- **`hashKey is not set in config, generating a random one`** — предупреждение Zot.
  При включённом UI сессии будут сбрасываться при каждом рестарте. Если UI
  включаем (#277) — задать ключ.
- **Сосуществование namespace подтверждено**: в одном каталоге лежат
  `karaoke/*` (наш push) и `library/*` (кэш pull-through).
- **Health-эндпоинт** `/v2/_zot_health` вернул 404 — путь другой, для мониторинга
  уточнить отдельно.

## Артефакты стенда

| Файл | Что |
|---|---|
| `config.json` | рабочий конфиг (с allow-list `library/**`) |
| `config-blanket-ondemand.json` | провальный конфиг (без `content`) |
| `htpasswd` | bcrypt, 2 пользователя |
| `rss-pull.txt`, `rss-push.txt` | сырые семплы RSS |
| `data/` | хранилище стенда (более не нужно, удаляется) |
