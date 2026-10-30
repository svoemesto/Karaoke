# Quickstart: проверка автоматизации allow-list (спека 677, KARAOKE-337)

> Phase 1 output. Сценарии проверки — от изолированных (без прода) до стендовых.
> Команды и ожидаемые результаты; реализация деталей — в `tasks.md`.

## §1. Изолированная самопроверка скрипта (без прод-хоста)

```bash
cd /home/nsa/Karaoke
bash deploy/admin-ip-allowlist.sh --self-test
```

**Ожидается**: код 0; в выводе — сценарий «адрес сменился»: посторонний адрес добавлен в оба
stream-блока тестового конфига, проверка синтаксиса выполнена, сценарий отката при ошибке
восстановил исходный файл побайтно (FR-018, SC-005, SC-007). Прод-хост в сценарии **не
участвует** — ssh не вызывается.

## §2. Диагностика без правок (FR-017)

```bash
bash deploy/admin-ip-allowlist.sh --diagnose
```

**Ожидается**: код 0; напечатаны результаты трёх проб, определённый внешний адрес, текущий
allow-list с прод-хоста и план изменения («добавить `allow <ip>;` в 2 блока»). **Ничего не
изменено** — ни на проде, ни в репозитории.

## §3. Гейты репозитория (обязательно перед сдачей)

```bash
python3 tools/lint-knowledge.py
bash tools/check-knowledge-structure.sh
bash tools/check-feature-doc.sh knowledge/product/features/admin-ip-allowlist.md
python3 tools/check-ssot-impact.py
python3 tools/check-spec-issue-link.py
bash tools/spec-knowledge-preflight.sh <<'KNOWLEDGE'
knowledge/guidelines/runbooks/prod-config-delivery.md — канон выкладки и новая модель управления allow-list
knowledge/system/infra/deploy-overview.md — прод-хост и allow-list
knowledge/system/infra/ci-tools.md — реестр guard'ов (куда вписывается новый guard)
knowledge/adr/0004-karaoke-app-admin-only.md — почему прод доступен только с админ-машины
knowledge/domains/integration/domain.md — Telegram-бот проекта (Д-2)
knowledge/guidelines/runbooks/forgejo-access.md — доступ к Forgejo API для PR
KNOWLEDGE
bash tools/check-allowlist-guardrails.sh          # новый guard (BR-631)
bash tools/check-allowlist-guardrails.sh --self-test
bash tools/check-ci-workflow-sync.sh               # 41 гейт в каждой копии workflow
bash tools/check-secret-in-argv.sh                 # секреты не в argv (FR-016)
pre-commit run --all-files                         # без SKIP
```

**Ожидается**: все команды — код 0; `check-ci-workflow-sync.sh` показывает одинаковый набор
гейтов в обеих копиях workflow.

## §4. Юниты systemd — проверка синтаксиса и содержимого

```bash
systemd-analyze verify deploy/admin-ip-allowlist.timer deploy/admin-ip-allowlist.service || true
grep -nE "OnUnitActiveSec|OnBootSec|ExecStart|User=" deploy/admin-ip-allowlist.timer deploy/admin-ip-allowlist.service
```

**Ожидается**: `OnUnitActiveSec=10min` (Д-3), `OnBootSec` — небольшой (например `2min`),
`ExecStart` — абсолютный путь к скрипту, `User=root` (правка `/etc/nginx/nginx.conf` требует
root). `systemd-analyze verify` может ругаться на отсутствие файла по `ExecStart` до
установки — это ожидаемо, код возврата игнорируется.

## §5. Стенд: проверка на настоящем nginx (требует согласия владельца)

На админ-машине `nginx` не установлен, поэтому сценарий с реальным `nginx -t` выполняется на
локальном контейнере — **только по согласию владельца** (AGENTS.md: контейнеры на админ-машине
не перезапускаются без согласия, Pass 380). Конфигурация тестового nginx готовится в
репозитории (`deploy/prod-single-host/nginx.conf` — эталон структуры).

```bash
# 1. Подготовить изолированный каталог с копией конфигурации и подставить посторонний адрес
#    (это делает сам self-test; для ручной проверки с настоящим nginx — по инструкции
#    владельца из report.md).
# 2. Запустить тестовый nginx с подготовленным конфигом и проверить синтаксис:
nginx -t -c <подготовленный-конфиг>
# 3. Убедиться, что скрипт добавляет адрес только в allow-list и откатывается при ошибке:
bash deploy/admin-ip-allowlist.sh --self-test
```

**Ожидается**: `nginx -t` проходит; self-test зелёный; при подсовывании ошибки исходный
файл восстановлен побайтно (SC-005).

## §6. Установка юнитов на админ-машине (действие владельца)

Юниты лежат в репозитории; **автоматизация их не устанавливает** — установка и включение —
действие владельца (как с `deploy/heap-dump-retention.{service,timer}`):

```bash
cd /home/nsa/Karaoke
sudo cp deploy/admin-ip-allowlist.timer deploy/admin-ip-allowlist.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now admin-ip-allowlist.timer
systemctl list-timers | grep admin-ip-allowlist
```

**Ожидается**: таймер в списке, `NEXT` — не позднее +10 минут; первый запуск сервиса виден в
`journalctl -u admin-ip-allowlist.service`.

**Токены Telegram и Forgejo** задаются владельцем в окружении сервиса (например
`EnvironmentFile=/etc/admin-ip-allowlist.env`, права `600`), значения — токен бота проекта и
chat_id канала проекта, плюс `ALLOWLIST_FJ_TOKEN`/`ALLOWLIST_FJ_URL` из
`/home/nsa/.config/karaoke/forgejo.env`. В репозиторий они не попадают (FR-016).

## §7. Проверка на проде — только по слову владельца

Сценарий «прод недоступен с админ-машины из-за адреса» **не воспроизводится искусственно**:
он требует смены внешнего адреса. Ручная проверка применяемости (решение Д-4: текущую дыру
`87.58.202.244` вручную **не** закрываем) — по инструкции владельца из `report.md`.

## §8. Проверка FR-005 и FR-010 без прода

- **FR-005** (адрес уже в allow-list → без действий): текущий внешний адрес `185.26.28.199`
  уже присутствует в allow-list прод-хоста ⇒ обычный цикл завершается действием
  `skip:reason=already_allowed`, без правки и без reload (FR-005: «присутствие означает
  отсутствие любых действий, включая перезагрузку»).
- **FR-010** (одна правка за 30 минут): проверяется в self-test — вторая правка в пределах
  окна отклоняется с `skip:reason=rate_limit`.
