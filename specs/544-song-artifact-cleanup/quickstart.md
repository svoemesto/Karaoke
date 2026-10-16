# Quickstart: проверка автоуборки артефактов песни

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Issue**: [#321](http://localhost:8080/work_packages/321)

Руководство для проверки владельцем в production-like окружении (admin-машина,
контейнер `karaoke-app`). Тестов в CI нет (Constitution § «Рабочий процесс»),
поэтому приёмка — по сценариям ниже, с фактами с диска.

---

## Предусловия

```bash
# 1. Сборка (единственная доступная агенту часть; перезапуск — только владелец)
cd /home/nsa/Karaoke
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-app:test --parallel

# 2. Перезапуск контейнера — ВЛАДЕЛЕЦ, после остановки очереди заданий
#    (AGENTS.md Pass 380: сначала «Стоп» в шапке админки, дождаться пустой очереди)
cd /home/nsa/Karaoke/deploy && bash do.sh start_app

# 3. Флаги проверки (по умолчанию уборка выключена, сухой прогон включён)
curl -s http://localhost:8898/api/properties | grep -E "cleanup(Enabled|DryRun)"
```

Ожидаемо: `cleanupEnabled=false`, `cleanupDryRun=true`.

**Замер «до»** (сохранить для сравнения после чистки):

```bash
df -h /sm-karaoke/done1 /sm-karaoke/done2 /sm-karaoke/system | tail -4
python3 /home/nsa/Karaoke/.scratch/karaoke/305-disk-artifacts/analyze.py > /tmp/before.txt
```

---

## Сценарий 1 — демо-ролики не копятся (US1, SC-001, SC-007)

1. Выбрать тестовую песню и поставить публикацию в Telegram (и/или ВК) из админки.
2. Дождаться завершения шага публикации.
3. Проверить:

```bash
ls "/sm-karaoke/done2/<Author>/<Album>/done_files/" | grep -i demo   # ожидаемо: пусто
```

4. Повторить публикацию той же песни: система должна **сама** отрендерить демо заново
   и снова удалить его. Ручных шагов — ноль.

**Ожидаемо**: после каждой публикации файла `* [demo].mp4` на диске нет; публикация
состоялась (сообщение в TG/VK на месте).

**Провал**: файл остался → ошибка в `PremiumAutoPublishScheduler.closeIfBothChannelsDone`;
файла нет, но публикации нет → удаление сработало раньше публикации (регресс R1).

---

## Сценарий 2 — локальные mp3-стемы удаляются, здоровье не воссоздаёт (US2, SC-002, SC-006)

1. Включить уборку: `cleanupEnabled=true`, `cleanupDryRun=false`.
2. Взять тестовую песню и выполнить шаг «минусовка/вокал в mp3» (или пересоздать её
   стемы, если они уже есть).
3. Проверить:

```bash
ls "/sm-karaoke/done2/<Author>/<Album>/hdemucs_mmi/" | grep -E "\.mp3$"   # ожидаемо: пусто
curl -s -X POST http://localhost:8898/api/song/gethealthreportlist \
  -H 'Content-Type: application/json' -d '{"songIds":[<songId>]}' | python3 -m json.tool | head -40
```

**Ожидаемо**: локальных mp3 нет; объекты в локальном и удалённом MinIO на месте; в
отчёте здоровья **нет** `FILE_VIOLATION` по mp3 и **нет** задачи `FF_MP3_*`.

4. Подождать 24 часа (или прогнать полную проверку здоровья вручную) и убедиться, что
   задачи перекодирования не появились (SC-006).

**Провал**: mp3 вернулись → правка `KaraokeFileType.locations`/`HealthReport` не
применилась; mp3 удалены, но их нет в MinIO → нарушено условие «обе загрузки успешны».

---

## Сценарий 3 — мёртвые артефакты не создаются (US3)

1. Импортировать тестовую песню и провести её через разметку до апрува.
2. Проверить в папке альбома:

```bash
D="/sm-karaoke/done2/<Author>/<Album>"
ls "$D" | grep -E "\.(song|settings|srt)$|\.kdenlive|\.run$" ; ls "$D/done_projects" 2>/dev/null
```

**Ожидаемо**: пусто (кроме `.flac`-исходника и `[key].json`).
3. У песни с аккордами открыть карточку — признак аккордов должен быть **истинным**
   (считается по БД, не по `.srt`).

**Провал**: `.song`/`.srt` появились → писатель не удалён; `hasChords=false` у песни
с аккордами → неверный источник признака (R4).

---

## Сценарий 4 — разовая чистка истории (US4, SC-003, SC-005)

Порядок обязателен: класс `STEMS_MP3_LOCAL` — **после** сценария 2.

```bash
BASE=http://localhost:8898/api/cleanup

# 1. Список классов и порядок
curl -s $BASE/classes | python3 -m json.tool | head -60

# 2. Сухой прогон первого класса
curl -s -X POST $BASE/plan -H 'Content-Type: application/json' \
  -d '{"classId":"DEMO_MP4"}' > /tmp/plan-demo.json
python3 -c "import json;d=json.load(open('/tmp/plan-demo.json'));print(d['fileCount'],d['totalBytes'],d['fingerprint'])"

# 3. Проверка предохранителя: защищённый класс должен быть отклонён
curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/plan \
  -H 'Content-Type: application/json' -d '{"classId":"STEMS_FLAC"}'     # ожидаемо: 400

# 4. Ни один файл не удалён сухим прогоном
ls "/sm-karaoke/done2/<Author>/<Album>/done_files/" | grep -ci demo      # ожидаемо: 1

# 5. Утверждение и запуск класса (после утверждения владельцем!)
PLAN=$(python3 -c "import json;print(json.load(open('/tmp/plan-demo.json'))['planId'])")
FP=$(python3 -c "import json;print(json.load(open('/tmp/plan-demo.json'))['fingerprint'])")
curl -s -X POST $BASE/apply -H 'Content-Type: application/json' \
  -d "{\"planId\":\"$PLAN\",\"fingerprint\":\"$FP\",\"approvedBy\":\"Сергей Новиков\",\"confirm\":true}"

# 6. Статус и журнал
curl -s "$BASE/status?runId=<runId>" | python3 -m json.tool
head -3 /sm-karaoke/system/cleanup-logs/2026-10-16-DEMO_MP4.tsv
```

**Ожидаемо**: сухой прогон показывает ≈17 885 файлов / ≈176,7 ГБ и ничего не удаляет;
`apply` отвечает `202`; статус доходит до `DONE`; журнал содержит каждую строку с
`result=deleted`; `freedBytes` совпадает с замером `df`.

Затем — остальные классы по порядку из `data-model.md` §1 (3 → 4 → 5 → 6 → 7).
После чистки:

```bash
df -h /sm-karaoke/done1 /sm-karaoke/done2 | tail -3     # ожидаемо: −600 ГБ и более
```

---

## Сценарий 5 — гигиена и документация (US5)

```bash
# Битые симлинки и посторонние файлы в каталогах ссылок
find /sm-karaoke/done1 /sm-karaoke/done2 -path "*/symlink_*" -xtype l | head    # ожидаемо: пусто
find /sm-karaoke/done1 /sm-karaoke/done2 -path "*/symlink_*" -name "*.svg" | head  # ожидаемо: пусто

# Проверка здоровья должна их замечать (после правки покрытия)
curl -s -X POST http://localhost:8898/api/song/gethealthreportlist \
  -H 'Content-Type: application/json' -d '{"songIds":[<songId>]}' | grep -i symlink
```

**Ожидаемо**: мусора нет; искусственно созданный посторонний файл в `symlink_*`
попадает в отчёт здоровья.

Документация: открыть `knowledge/domains/catalog/components/song-lifecycle.md` и
`knowledge/domains/rendering/components/mlt-pipeline.md` — путь рендера описан как
Playwright, MP4 не «уходит в MinIO»; `python3 tools/lint-knowledge.py` — зелёный.

---

## Сводка приёмки

| Критерий | Где проверяется | Ожидаемый факт |
|---|---|---|
| SC-001 | сценарий 1 | демо-файлов на диске нет |
| SC-002 | сценарий 2 | локальных mp3 нет, MinIO на месте, +445 ГБ |
| SC-003 | сценарий 4 | занятое место −600 ГБ и более |
| SC-004 | сценарий 4 (шаг 3) | защищённые классы отклоняются |
| SC-005 | сценарий 4 (шаг 4–6) | отчёт → утверждение → журнал |
| SC-006 | сценарий 2 (шаг 4) | ремонт не запускается |
| SC-007 | сценарий 1 (шаг 4) | повторная публикация без ручных шагов |
| SC-008 | сценарий 1, 2 | публикация, плеер, рендер работают |
| SC-009 | сценарий 5 | Knowledge соответствует коду |

**Не входит в приёмку**: прод-сервер (там только хранилище; отдельный прогон —
вне этой поставки), публичный сайт (не менялся).
