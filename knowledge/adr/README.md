# Architecture Decision Records (ADR)

> **Статус**: в разработке. Каркас развёрнут в рамках
> livedocs-миграции (Pass 340-385). Спецификация
> `322-knowledge-scaffold` в репозитории отсутствует и в истории
> git не появлялась (проверено в Pass 473). Наполнение ведётся в
> следующих спецификациях.

ADR фиксируют **почему** мы сделали архитектурный выбор, а не **что**
(«что» — в `knowledge/domains/`, «как» — в `specs/NNN-*/`).

Append-only: нельзя редактировать или удалять ADR после принятия.
Только `superseded_by` / `supersedes`.

## Нумерация (Karaoke-override)

Используется существующая Karaoke-схема:

- `0001-…` … `0008-…` — глобальные решения проекта.
- `local-0001-…` … `local-NNNN-…` — локальные решения внутри отдельного
  контейнера (backend/frontend/MLT). Префикс `local-` сохраняется при
  переносе из `livedocs/architecture/decisions/`.

Шаблон `ADR-XXXX` из generic-bootstrap **не применяется** в Karaoke —
см. `knowledge/README.md` § Karaoke-overrides, п.3.

## Шаблон

См. [`knowledge/templates/adr.md`](../templates/adr.md).

## Миграция из `livedocs/architecture/decisions/`

13 файлов перенесены в спецификации 331 с сохранением ID и slug-имени:

```
0001-raw-jdbc.md
0002-mlt-instead-of-ffmpeg.md
0003-livedocs-markdown-yaml-mermaid.md
0004-karaoke-app-admin-only.md
0005-self-hosted-ml.md
0006-processbuilder-redirect-errorstream.md
0008-tracker-openproject-migration.md
local-0001-karaoke-properties-defaults.md
local-0002-save-exception-handling.md
local-0003-shared-minio-image-cache.md
local-0004-lazy-eager-load-webvue3-pagination.md
local-0005-structured-logging-karaoke-app.md
local-0006-logging-and-error-handling-karaoke-web.md
local-0007-zakroma-album-id-in-stream-dto.md
```

Дополнительные ADR в `knowledge/adr/` (созданы после миграции; реестр
дополнен в Pass 471 — до этого три ADR не были перечислены):
- `0007-adopt-knowledge-as-ssot.md` — ADR о принятии knowledge/ как
  SSoT (создан в спеке 322). Получил номер 0007, чтобы не конфликтовать
  с существующим `0001-raw-jdbc.md`.
- `0009-get-vs-post-large-payload.md` — GET → POST для больших CSV-payload
  в membership-endpoint (414 Request-URI Too Large).
- `0010-lm-studio-instead-of-ollama.md` — LM Studio вместо Ollama как
  LLM-движок; LangChain4j удалён. Замещает ADR-0005 в части LLM.
- `0011-merge-docs-into-knowledge.md` — слияние слоя `docs/` в `knowledge/`:
  продуктовая ветка `knowledge/product/` (22 per-feature документа профиля B),
  реестр аудита #336 (510 BR-кодов), запрет каталога `docs/` guard'ом.
  **Заменяет ADR-0007 в части** — п. 2 (не переносить `docs/`), п. 4
  (сосуществование `docs/` и `knowledge/` до окончания миграции) и следствия 5
  (судьба `docs/` — отдельная спецификация). Остальные решения ADR-0007
  (Karaoke-overrides, нумерация ADR, `knowledge/` как SSoT) остаются в силе.
- `local-0009-thread-local-connection-lifecycle.md` — жизненный цикл
  JDBC-соединения потока (ThreadLocal): одноразовый поток закрывает соединение
  в `finally` (`startOneShotThread`), переиспользуемый — не закрывает
  (аудит #336, OpenProject #368); третий тип — поток, завершаемый пулом
  (`Dispatchers.IO`, вытесненный поток Tomcat): правило закрытия не определено,
  канал остаётся в БД, компенсирующая мера — `max_connections=300`
  (BR-627, OpenProject #410).
- `local-0008-effective-hidden-album-types.md` — transient auto-reset
  `effectiveHiddenAlbumTypes` для `?albumId=`.
- `local-0014-heap-memory-guardrails-karaoke-app.md` — превентивные меры против
  OOM в `karaoke-app` (спека 676 / KARAOKE-245, BR-630): предел памяти
  контейнера (`mem_limit: 8g`) и максимум heap (`-Xmx6g`), дамп состояния heap
  при OOM на томе и политика хранения дампов, источник данных о загрузке heap
  (`Runtime`), маппинг severity алерта → уровень журнала (CRITICAL → ERROR);
  отвергнутая альтернатива «поднять своп 64 → 128 ГБ» и решение владельца
  «своп не поднимаем». Уточняет область ADR `local-0013` (канал доставки для
  `karaoke-app` — журнал/SLF4J); сам `local-0013` не переписывается (ADR
  append-only).
- `local-0015-admin-ip-allowlist-automation.md` — автоматизация allow-list
  админ-машины (спека 677 / KARAOKE-337, BR-631): таймер раз в 10 минут,
  детект причины недоступности (прод недоступен целиком → адрес не добавляется),
  определение внешнего адреса с резервным источником, хирургическая правка
  allow-list прод-хоста с бэкапом, двойной проверкой синтаксиса, контрольной
  проверкой и откатом, ограничение частоты (одна правка за 30 минут), журнал +
  Telegram-уведомления (Д-2) и PR через Forgejo API (Д-1: сливает владелец).

- `local-0016-command-guard-enforcement.md` — механизм enforcement для правил
  AI-агента (карта KARAOKE-339): слой запрета — host-плагин DSH `agent-command-guard`
  (перехват `tools/pre-execute` до исполнения, режимы `deny`/`ask`, умолчание
  `ask`), область — только репозиторий Karaoke; закрытие класса «санкционированная
  команда без согласия» через окно согласия
  `~/.dsh/policies/consent/<session-id>.json` (пишет агент по слову владельца,
  действует до конца сессии, не покрывает прямой `docker restart` и
  `docker compose`); след — журнал `~/.dsh/ledger/agent-commands.ndjson` (12 полей,
  срок без ограничения) и независимый сборщик; публичный record — задача Plane с
  меткой `type:governance-violation` (заводит агент, только для `deny`);
  процессные нарушения — сверка `tools/check-tracker-workflow.sh` перед
  `close-issue` без блокировки закрытия; деградация агента и `auditd` отвергнуты;
  зафиксировано ограничение «запрет по тексту обходим» и критерий измеримости
  «ноль срабатываний в наблюдаемом канале + ноль необъяснённых изменений среды».

Текущий формат — `* Status:` / `* Date:` / etc. — сохраняется как есть;
полный переход на YAML frontmatter (как в шаблоне) — опционален и
делается отдельным PR.
