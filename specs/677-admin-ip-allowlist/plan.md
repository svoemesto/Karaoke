# Implementation Plan: Автоматизация allow-list админ-машины — самовосстановление доступа к прод-каналу при смене внешнего IP

**Branch**: `678-admin-ip-allowlist` | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/677-admin-ip-allowlist/spec.md`

**Note**: шаблон заполнен по `/speckit-plan`; имя ветки — фактическое (`678-admin-ip-allowlist`),
каталог спеки — `specs/677-admin-ip-allowlist/` (номер спеки задан задачей KARAOKE-337).

## Summary

Админ-машина (`nsa-i9`) теряет доступ к прод-каналу `5433`/`9000`, когда её внешний адрес
меняется (пул провайдера плавает ежемесячно; при поднятом VPN прод видит адрес VPS
`87.58.202.244`, которого в allow-list нет). Симптом один: конвейер встаёт, а мониторинг на
той же машине поднимает **ложный** CRITICAL «Прод-сервер недоступен» при живом проде.

Автоматизация: таймер каждые 10 минут проверяет с админ-машины TCP `5433`, TCP `9000` и
HTTPS `https://sm-karaoke.ru/health`. Если HTTPS отвечает 2xx, а прод-канал — нет, причина
в allow-list: система определяет текущий внешний адрес (публичный HTTPS-сервис + резерв,
FR-003), валидирует его (FR-004) и **хирургически** добавляет одну строку `allow <ip>;` в
каждый из двух stream-блоков host-nginx на прод-хосте — с бэкапом, проверкой синтаксиса
тем же nginx'ом, перезагрузкой, контрольной проверкой фактического ответа и откатом при
любом сбое (FR-006…FR-009, FR-020). Применение ограничено одной правкой за 30 минут
(FR-010). Каждая попытка и каждая правка пишутся в журнал (FR-012), владелец получает
уведомление в Telegram (FR-013, Д-2), а после успешного применения формируется PR,
приводящий репозиторную копию конфигурации в соответствие с продом (FR-014, Д-1).

## Technical Context

**Language/Version**: Bash 4+ (скрипт), systemd unit-файлы; JSON-ответы Forgejo API
разбираются `python3` (stdlib `json`)

**Primary Dependencies**: `bash`, `curl`, `ssh`/`scp` (OpenSSH client), `python3`,
`coreutils` (`date`, `awk`, `grep`, `sed`, `base64`), `systemd` (таймер)

**Storage**: файлы вне репозитория — журнал (`/var/log/admin-ip-allowlist.log` по умолчанию)
и состояние ограничения частоты (файл рядом с журналом); бэкапы конфигурации — на прод-хосте
(`/etc/nginx/nginx.conf.bak-<stamp>`)

**Testing**: `--self-test` скрипта (изолированный сценарий «адрес сменился» + откат при
ошибке, без обращения к прод-хосту) и `--self-test` нового guard'а; общие гейты репозитория
(pre-commit, CI) — см. quickstart

**Target Platform**: Linux, админ-машина `nsa-i9`, systemd; прод-хост `188.127.240.124`
доступен по ssh, обслуживается host-nginx

**Project Type**: операционная автоматизация (CLI-скрипт + systemd-таймер), не приложение

**Performance Goals**: цикл проверки — раз в 10 минут (Д-3); восстановление доступа — не
позднее 15 минут с момента обнаружения (SC-001); одна применённая правка — единицы секунд
(проба → правка → проверка синтаксиса → reload → контроль)

**Constraints**: не более одной применённой правки за 30 минут (FR-010); изменяется только
allow-list host-nginx (FR-006, FR-015); секреты — только из окружения и не в argv
(FR-016, BR-551/552/605); прод не меняется без согласия владельца; репозиторий не
расходится с продом (FR-014)

**Scale/Scope**: один прод-хоста, два stream-блока, allow-list из единиц адресов, одна
админ-машина, один таймер

## Constitution Check

*GATE: пройден до Phase 0 research. Перепроверен после Phase 1 design.*

| Принцип | Требование | Как соблюдается | Вердикт |
|---|---|---|---|
| **I** Self-contained | Медиа-пайплайн (ffmpeg, melt/MLT, Demucs, Sheetsage) — без внешних SaaS в рантайме | Пайплайн не затрагивается. Внешние сервисы (определение адреса, Telegram Bot API) использует **операционная автоматизация**, а не рантайм приложения; их выбрал владелец (FR-003, Д-2) | PASS |
| **II** Сырой JDBC | БД — только через сырой JDBC, без JPA | Кода приложения нет | PASS |
| **III** Двух-БД sync | Сущности с sync — в `SyncRegistry.all` | Не затрагивается | PASS |
| **IV** Async-очередь | Длительные операции — через `KaraokeProcess*` | Не затрагивается | PASS |
| **V** Двух-фронтенд | admin и public — разные приложения | Фронтенд не трогаем | PASS |
| **VI** Code Standards | FR-006 KDoc/`@see`; FR-007 ktlint/ESLint; **FR-009 — per-feature документ в том же PR** | Публичные API приложения не меняются (Kotlin-кода нет) → FR-006/FR-007 не применимы; **FR-009 соблюдается**: `knowledge/product/features/admin-ip-allowlist.md` создаётся в том же PR | PASS |
| **VII** Cross-Machine Setup | Локальные AI-конфиги — вне git | Не затрагивается | PASS |
| **VIII** Секреты и git-гигиена | Секреты только через env, не в argv, не в репозитории | Токен Telegram и токен Forgejo — только из окружения; токен Telegram не попадает в argv (`curl -K` с конфиг-файлом `600`); guard BR-551/552/605 проверяет argv; новый guard проверяет скрипт и юниты на секреты | PASS |
| **IX** Knowledge-first | Pre-flight 5 шагов + обновление `knowledge/` в том же PR | Pre-flight выполнен (spec § Knowledge References: 4 grep, 14 документов, 6 ADR); в этом PR: ADR `local-0015`, per-feature документ, правки `deploy-overview.md`, `prod-config-delivery.md`, `ci-tools.md`, строки BR-631 в реестре аудита, Linking Protocol L3→L2→L1 | PASS |

Нарушений, требующих обоснования, нет — Complexity Tracking не заполняется.

## Project Structure

### Documentation (this feature)

```text
specs/677-admin-ip-allowlist/
├── spec.md              # контракт (утверждён владельцем 2026-10-30)
├── plan.md              # этот файл
├── research.md          # Phase 0: 17 решений с обоснованиями
├── data-model.md        # Phase 1: сущности и состояния
├── quickstart.md        # Phase 1: сценарии проверки
├── contracts/           # Phase 1: контракты интерфейсов
│   ├── automation-cli.md
│   ├── allow-list-surgery.md
│   └── journal-format.md
├── checklists/
│   └── requirements.md
├── tasks.md             # Phase 2 (/speckit-tasks) — создаётся следующим шагом
└── report.md            # обязательный артефакт Plane-workflow (после merge)
```

### Source Code (repository root)

```text
deploy/
├── admin-ip-allowlist.sh              # скрипт автоматизации (новый)
├── admin-ip-allowlist.service         # oneshot-сервис (новый)
├── admin-ip-allowlist.timer           # таймер 10 минут (новый)
├── prod-single-host/
│   └── nginx.conf                      # репозиторная копия конфигурации (правка автоматизацией, PR — FR-014)
└── heap-dump-retention.{sh,service,timer}   # образец структуры (не изменяется)

tools/
├── check-allowlist-guardrails.sh       # новый быстрый guard (BR-631)
└── check-ci-workflow-sync.sh           # не изменяется (проверяет равенство копий workflow)

knowledge/
├── adr/local-0015-admin-ip-allowlist-automation.md     # новый ADR
├── product/features/admin-ip-allowlist.md              # новый per-feature документ (FR-009)
├── product/README.md                                    # + строка индекса
├── system/infra/deploy-overview.md                      # модель управления allow-list
├── system/infra/ci-tools.md                             # строка нового guard'а
├── system/infra/audit-336-registry.md                   # строка BR-631
└── guidelines/runbooks/prod-config-delivery.md          # новая модель управления allow-list
```

**Structure Decision**: код автоматизации живёт в `deploy/` рядом с существующими
прод-скриптами (`sync_prod_config.sh`, `heap-dump-retention.sh`) — операционные скрипты
прода в одном месте; guard — в `tools/` рядом с остальными быстрыми guard'ами
(`check-memory-guardrails.sh`); знания — в `knowledge/` по Linking Protocol.

## Порядок реализации (для `/speckit-tasks`)

1. **Foundational** — каркас скрипта (проба, журнал, состояние), юниты, `--diagnose`;
2. **US1** — определение и валидация адреса (FR-003, FR-004, FR-005);
3. **US2** — хирургическая правка allow-list с бэкапом, проверкой синтаксиса, reload,
   контролем и откатом (FR-006…FR-009, FR-020);
4. **US3** — ограничение частоты и журнал (FR-010, FR-012);
5. **US4** — уведомления в Telegram (FR-013, FR-016);
6. **US5** — PR через Forgejo API (FR-014);
7. **US6** — изолированный self-test «адрес сменился» + откат (FR-018, SC-005, SC-007);
8. **US7** — guard BR-631 + подключение в pre-commit и обе копии CI;
9. **US8** — база знаний (ADR, per-feature, правки, реестр BR-631);
10. **Polish** — `report.md`, полный прогон гейтов, финальный коммит (по слову владельца).

## Complexity Tracking

> Нарушений Constitution Check нет — раздел пуст.
