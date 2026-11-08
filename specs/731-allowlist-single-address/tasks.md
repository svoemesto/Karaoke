# Tasks: Механизм allow-list прод-хоста — один текущий адрес и адрес, который видит прод

**Feature Branch**: `731-allowlist-single-address`
**Spec**: `specs/731-allowlist-single-address/spec.md`
**Issue ID**: `none`

## Phase 1 — Диагностика и факты

- [x] T001 Разобрать журнал `/var/log/admin-ip-allowlist.log` за 2026-11-08 (циклы 12:19–12:49 UTC) — причина отказа: `sources_disagree`
- [x] T002 Проверить, что расхождение источников — следствие VPN-туннеля: `api.ipify.org` / `ifconfig.me` → `195.178.4.x` (ротация), остальные → `95.31.39.146`
- [x] T003 Узнать адрес, который видит прод на SSH-канале (`SSH_CLIENT`) и на канале 5433/9000 (stream-журнал nginx на проде) — совпадают: `95.31.39.146`
- [x] T004 Разобрать откат: `rollback_change remote "${PROD_CONF}" "${BACKUP_PATH}"` — бэкап последнего файла попадает в первый файл
- [x] T005 Восстановить `/etc/nginx/nginx.conf` на проде после сбоя отката, проверить `nginx -t`
- [x] T006 Привести allow-list на проде к одному текущему адресу (решение владельца 2026-11-08), проверить доступ приложения к БД

## Phase 2 — Код механизма

- [x] T101 `deploy/admin-ip-allowlist.sh`: `detect_prod_seen_address` — адрес, который видит прод (FR-001)
- [x] T102 `current_address` — единая точка определения адреса: прод → внешние сервисы (FR-001, FR-002)
- [x] T103 `confirm_address` переведён на `current_address` — перепроверка из того же источника (FR-003)
- [x] T104 `prepare_new_config` (stream-блоки) — замена списка на один адрес (FR-004)
- [x] T105 `prepare_new_allow_config` (HTTP-локации) — замена списка на один адрес (FR-004)
- [x] T106 `address_allowed` — «ровно один allow и он текущий» (FR-005)
- [x] T107 `verify_diff` — допускается изменение только allow-строк; итог проверяется отдельно (FR-006)
- [x] T108 `apply_change` — запоминает свой бэкап и способ записи (`APPLY_BACKUP_PATH`, `APPLY_WRITE_METHOD`)
- [x] T109 `rollback_applied` — откат каждого файла из его бэкапа (FR-007, FR-008, FR-010)
- [x] T110 `run_cycle` — сброс списка применённых файлов, откат при сбое применения и при провале контрольной проверки (FR-007)
- [x] T111 `run_diagnose` — использует `current_address`

## Phase 3 — Самопроверка

- [x] T201 Сценарий 19: адрес берётся у прод, расхождение внешних сервисов не блокирует (SC-001)
- [x] T202 Сценарий 20: в списке остаётся ровно один текущий адрес; «правильный» файл правки не требует (SC-002)
- [x] T203 Сценарий 21: откат восстанавливает каждый файл из своего бэкапа (SC-003)
- [x] T204 Прогон `bash deploy/admin-ip-allowlist.sh --self-test` — все сценарии (SC-004)

## Phase 4 — Прод и репозиторий

- [x] T301 Проверка на реальных конфигах: `prepare_conf_for_file` + `verify_diff` на репозиторных копиях `deploy/prod-single-host/` со старыми списками
- [x] T302 Обновить репозиторные копии прод-конфигов (`deploy/prod-single-host/nginx.conf`, `80to8897`) — один адрес + комментарий о новом правиле
- [x] T303 Обновить комментарий о правиле в `/etc/nginx/nginx.conf` на проде, `nginx -t` — успешно

## Phase 5 — База знаний

- [x] T401 `knowledge/product/features/admin-ip-allowlist.md` — новое правило, источник адреса, ловушка отката
- [x] T402 `knowledge/guidelines/runbooks/prod-config-delivery.md` — инцидент 2026-11-08 и разбор
- [x] T403 `knowledge/adr/local-0015-admin-ip-allowlist-automation.md` — поправка решения владельца 2026-11-08
- [x] T404 `knowledge/domains/processing/components/async-process-queue.md` — связь allow-list и очереди (спека 731)

## Phase 6 — Выкат

- [x] T501 Коммит + PR (CI зелёный) + merge через `tools/merge-pr.sh`
- [x] T502 Установка скрипта на путь systemd-юнита `/home/nsa/Karaoke/deploy/admin-ip-allowlist.sh`
- [x] T503 Контрольный прогон цикла на прод-хосте: список — один адрес, `nginx -t` ок, ошибок `DB-CONNECT-001` нет
- [x] T504 Отчёт для владельца + предложение механизма без правки конфига при смене IP (§ «Предложение владельцу»)
