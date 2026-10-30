# Отчёт по спеке 677 — автоматизация allow-list админ-машины (KARAOKE-337)

**Ветка**: `678-admin-ip-allowlist` · **Спека**: `specs/677-admin-ip-allowlist/spec.md` ·
**Контракт**: FR-001…FR-020, SC-001…SC-008 — **не менялись** · **ADR**:
`knowledge/adr/local-0015-admin-ip-allowlist-automation.md`

## 1. Что сделано

| Файл | Строк | Назначение |
|---|---|---|
| `deploy/admin-ip-allowlist.sh` | 952 | скрипт автоматизации: пробы → определение и валидация адреса → хирургическая правка allow-list с бэкапом, двойной проверкой синтаксиса, reload, контрольной проверкой и откатом → журнал → уведомление → PR через Forgejo API |
| `deploy/admin-ip-allowlist.service` | 24 | oneshot-сервис: `ExecStart` абсолютным путём, `User=root`, `EnvironmentFile` для токенов, `StandardOutput=journal` |
| `deploy/admin-ip-allowlist.timer` | 13 | таймер: `OnBootSec=2min`, `OnUnitActiveSec=10min` (Д-3), `Persistent=true` |
| `tools/check-allowlist-guardrails.sh` | 359 | guard BR-631: структура allow-list, уникальность и публичность адресов, окно частоты, отсутствие секретов, период таймера; `--self-test` |
| `knowledge/adr/local-0015-admin-ip-allowlist-automation.md` | 127 | ADR: контекст, решения владельца Д-1…Д-4, решение, отклонённые альтернативы, последствия |
| `knowledge/product/features/admin-ip-allowlist.md` | 145 | per-feature документ (FR-009, шесть секций) |
| правки `knowledge/` | — | `adr/README.md` (индекс), `product/README.md` (индекс), `system/infra/deploy-overview.md` (модель управления allow-list), `guidelines/runbooks/prod-config-delivery.md` §8 (модель + установка юнитов), `system/infra/ci-tools.md` (guard + changelog), `system/infra/audit-336-registry.md` (секция BR-631 + счётчики 552 → 553) |
| `.pre-commit-config.yaml` | +24 | три хука: `allowlist-guardrails-guard`, `allowlist-automation-self-test`, `allowlist-guardrails-self-test` |
| `.forgejo/workflows/lint.yml`, `.github/workflows/lint.yml` | +2 job'а в каждой | `allowlist-guardrails-guard`, `allowlist-automation-self-test` (набор гейтов в копиях совпадает: 42) |

## 2. Ключевые решения реализации

- **Прод не трогаем до решения владельца.** Все проверки — на self-test и `--diagnose`
  (только чтение). Первое применение на проде произойдёт автоматически, когда
  таймер будет установлен владельцем и сработает.
- **Локальный git не используется.** PR собирается через Forgejo API (contents API:
  чтение репозиторной копии → вставка только недостающей `allow`-строки → ветка →
  коммит → `POST /pulls`). Причина: рабочее дерево админ-машины используется другими
  сессиями, `git checkout` из таймера устроил бы гонку (Pass 379 / IX.3).
- **Токены не в argv.** Bot API вызывается через `curl -K` с конфиг-файлом `600`,
  который удаляется сразу; то же для Forgejo API. Guard
  `tools/check-secret-in-argv.sh` — зелёный.
- **Проверка целостности записи.** После подмены содержимым сверяется sha256 живого
  файла с sha256 подготовленного: усечение при прерывании обнаруживается и
  откатывается (в анализе — H2).
- **Проверка точки монтирования.** Перед применением выполняется `findmnt -T` на
  прод-хосте, результат пишется в журнал (`event:mountpoint`). Факт снят лидом
  2026-10-30: `/etc/nginx/nginx.conf` — не точка монтирования (TARGET `/`, SOURCE
  `/dev/vda2`, ext4), поэтому требование FR-007 о сохранении inode для этого файла
  ничего не защищает. Если владелец разрешит атомарный rename, переключение — одна
  правка в одном месте.
- **Дрейф прод↔репозиторий** (например добавленный вручную адрес) фиксируется в
  журнале и сообщается владельцу, но применение не блокируется: прод — оперативная
  истина (Д-1). В PR посторонние расхождения не выносятся.

## 3. Проверки (фактический вывод)

```
bash deploy/admin-ip-allowlist.sh --self-test          → SELF-TEST: OK (все сценарии пройдены)
bash tools/check-allowlist-guardrails.sh                → --- итог: PASS=16 FAIL=0 ---
bash tools/check-allowlist-guardrails.sh --self-test    → ✅ self-test: правила ловят битое состояние и пропускают корректное
bash tools/check-ci-workflow-sync.sh                     → OK: наборы гейтовых команд совпадают (42 гейт(ов) в каждой копии)
bash tools/check-secret-in-argv.sh                      → ✅ OK: секреты не передаются через argv
python3 tools/lint-knowledge.py                         → All documentation passes architectural style and structural checks.
bash tools/check-knowledge-structure.sh                 → OK: knowledge/ structure valid (9/9 checks passed)
bash tools/check-feature-doc.sh knowledge/product/features/admin-ip-allowlist.md → OK
python3 tools/check-audit-registry.py                   → OK: реестр соответствует knowledge/ (512 кодов в таблице § 3, 553 зарегистрировано всего)
python3 tools/check-doc-references.py                   → OK: битых ссылок, несуществующих путей и эндпоинтов нет.
python3 tools/check-spec-issue-link.py                  → OK: 58/58 modern spec(s) have OpenProject Tracking section
bash tools/spec-knowledge-preflight.sh                   → OK: Knowledge-first pre-flight пройден — 6 ссылок на knowledge/
```

### Правки по решениям владельца Д-5…Д-7 (2026-10-30)

Слово владельца: спека приведена в соответствие, контракты и реализация — переделаны.

* **FR-007** (Д-6): «подмена содержимым (файл — bind-mount) либо атомарным переименованием
  (обычный файл) — по факту проверки точки монтирования». Edge case «CI не прошёл / PR не
  создан» (Д-5): правка на проде **остаётся**, владелец уведомляется, репозиторий догоняет
  позже. Edge case «прод-конфиг отличается от ожидаемого» (Д-7): расхождение фиксируется и
  сообщается, правка выполняется, если прошёл FR-020. В таблицу «Решения владельца» добавлены
  строки **Д-5, Д-6, Д-7**.
* **Код**: `check_mount_point` (findmnt -T) определяет способ записи — `mv` (атомарное
  переименование) для обычного файла, `cat` (содержимое, inode сохраняется, со сверкой sha256)
  для bind-mount; результат проверки пишется в журнал (`event:mountpoint`). Откат при `mv` —
  атомарный (`cp` бэкапа + `mv` на место, сам бэкап остаётся для разбора), при `cat` — прежний
  путь со сверкой sha256. Права и владелец нового файла наследуются от прежнего.
* **Self-test** пополнился двумя сценариями: применение через атомарное переименование
  (проверяется смена inode и ровно две добавленные строки `allow`) и откат после него.

### Self-test автоматизации: сценарии

1. **«IP сменился»** (SC-007) — посторонний адрес добавлен ровно одной строкой в
   каждый stream-блок; проверка diff'а (FR-020) подтверждает, что изменение состоит
   только из добавленных `allow`-строк; синтаксис принят валидатором.
2. **Откат при ошибке** (SC-005) — после подмены «битым» содержимым откат из бэкапа
   восстановил исходный файл **побайтно** (`cmp`).
3. **Прод недоступен** (SC-004) — HTTPS-проба не отвечает → причина не в allow-list,
   правка не выполняется.
4. **Ограничение частоты** (SC-006) — вторая правка в пределах окна отклонена
   (`skip:reason=rate_limit`).
5. **Состав PR** (FR-014) — в содержимое PR попадает только добавленная `allow`-строка.
6. **Валидация адреса** (FR-004) — отклонены `127.0.0.1`, `10.1.2.3`, `192.168.1.1`,
   `172.16.0.1`, `169.254.1.1`, `224.0.0.1`, `255.255.255.255`, `0.0.0.0`, IPv6 и
   не-IP; публичный IPv4 принят.
7. **Применение через атомарное переименование** (Д-6) — файл не точка монтирования → запись
   через `mv`: inode изменился (5 → 14), в целевом файле ровно две добавленные строки `allow`.
8. **Откат после атомарного переименования** (SC-005) — исходное содержимое возвращено
   целиком.

### Проверка на настоящем nginx (FR-018)

Сценарий 1 прогнан с **настоящим** nginx в одноразовом контейнере
(`docker run --rm`, `DOCKER_CONFIG` от корня репозитория, порты не поднимались,
контейнер удалён сразу):

```
ALLOWLIST_SELFTEST_DIR=… ALLOWLIST_SELFTEST_VALIDATOR='docker exec allowlist-nginx-test nginx -t -c {file}' \
  bash deploy/admin-ip-allowlist.sh --self-test
→ OK: проверка синтаксиса тестового конфига настоящим nginx
→ SELF-TEST: OK (все сценарии пройдены)
```

Проверка репозиторной копии тем же nginx: `nginx: the configuration file
/etc/nginx/nginx.conf syntax is ok`.

Две ошибки, найденные именно этим прогоном и исправленные: в тестовом конфиге не
был объявлен `minio_backend` и отсутствовала секция `events` — без них настоящий
nginx конфиг отклонял.

## 4. Что осталось за владельцем

1. **Установить юниты** (файлы готовы; установка — действие владельца):

   ```bash
   cd /home/nsa/Karaoke
   sudo cp deploy/admin-ip-allowlist.timer deploy/admin-ip-allowlist.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now admin-ip-allowlist.timer
   systemctl list-timers | grep admin-ip-allowlist
   ```

2. **Задать токены** — `/etc/admin-ip-allowlist.env` (права `600`):
   `ALLOWLIST_TELEGRAM_BOT_TOKEN`, `ALLOWLIST_TELEGRAM_CHAT_ID`, `ALLOWLIST_FJ_TOKEN`,
   `ALLOWLIST_FJ_URL`. Значения — токен бота проекта, chat_id канала проекта и
   токен Forgejo (в репозиторий они не попадают).

3. **Проверить состояние до включения**: `bash deploy/admin-ip-allowlist.sh --diagnose`
   (ничего не меняет).

4. **Ответы на три вопроса** (заданы лиду, спека не правилась):
   * H3 — порядок «прод, затем PR» (реализовано по Д-1) или «PR → CI → прод»;
   * H2 — атомарная замена (`rename`) вместо сохранения inode;
   * M3 — «ожидаемый» конфиг при расхождении прод↔репозиторий (сейчас: фиксируем
     дрейф, сообщаем, правим).

5. **FR-019** (access-журнал stream-блоков) — вне объёма: требует согласия на
   изменение прода.

## 5. Ограничения соблюдены

Прод (`188.127.240.124`) не тронут — ни одной команды, даже read-only смены состояния.
systemd-юниты не устанавливались. Контейнеры проекта (`karaoke-app` и прочие) не
трогались; локальный nginx — только одноразовый `docker run --rm`, удалён сразу.
`docker restart` не использовался. Секреты не печатались и не попали в репозиторий,
argv и журнал. Рабочее дерево админ-машины автоматизацией не используется.

**О чужой сессии в этом checkout.** Параллельно в `/home/nsa/Karaoke` работает другая
сессия и держит изменения Kotlin-файлов (`karaoke-app/.../KaraokeAppApplication.kt`,
`Song.kt`, `karaoke-web/.../KaraokeWebApplication.kt`, новый `KaraokeRuntimeRole.kt`,
`knowledge/domains/publishing/components/publishing-services.md`) — это нарушает IX.3
(должны быть в отдельном worktree). Из-за этого:
* `pre-commit run --all-files` красный (хуки `ktlint`/`prettier` падают и переписывают
  **чужие** файлы) — прогонялись гейты только по моим файлам;
* `python3 tools/check-ssot-impact.py` возвращает 1: правило
  `karaoke-app/**/model/Song.kt → knowledge/domains/catalog/components/dictionaries.md`
  требует правки `dictionaries.md` — это **чужое** изменение, не моё (мои файлы:
  `deploy/`, `tools/`, `knowledge/`, workflow'и, `.pre-commit-config.yaml`).

Чужие файлы не редактировались, не откатывались и не коммитились.

## 6. Метрики успеха

* **SC-001** (восстановление ≤ 15 минут): цикл таймера 10 минут + применение правки
  (секунды) — укладывается; проверяется в эксплуатации по журналу
  (`event:decision` → `event:edit:step=applied` → `event:control`).
* **SC-008** (90 дней без ручных правок): считается по журналу — каждая ручная правка
  видна как расхождение (`event:drift`) без записи `event:edit:step=applied`.
* **SC-002, SC-003, SC-004, SC-005, SC-006, SC-007** — покрыты self-test'ом и
  контрактами (см. §3).

## 7. Связанные артефакты

`specs/677-admin-ip-allowlist/` — `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/{automation-cli,allow-list-surgery,journal-format}.md`, `quickstart.md`,
`tasks.md` (62 задачи, все выполнены), `analysis.md` (12 находок, три вопроса
владельцу), `report.md` (этот файл).
