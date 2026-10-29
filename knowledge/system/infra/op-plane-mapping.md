# Таблица соответствия: OpenProject → Plane

> **Домен**: system (infrastructure)
> **Компонента**: статическая таблица номеров задач двух трекеров проекта
> Karaoke; правило ссылок после миграции.

## Назначение

Таблица отвечает на один вопрос: **какая задача Plane соответствует старому
номеру OpenProject**. Номера двух трекеров не совпадают (Plane нумерует задачи
внутри проекта с 1), поэтому без таблицы старые ссылки `OpenProject #403` в
спеках и отчётах не разрешаются.

Файл [`op-plane-mapping.csv`](op-plane-mapping.csv) — копия артефакта миграции
(`mapping/op_to_plane.csv` карты `tracker-migration`), 297 строк, sha256
`506894481ab3125cf4da342a9478e8b1bbd435cb13faf9dfa9895e152b833e24`
(публикуемая копия с переводами строк LF).

## Как читать

CSV с заголовком, разделитель — запятая, кодировка UTF-8:

| Колонка | Смысл |
|---|---|
| `op_id` | номер work package в OpenProject |
| `plane_sequence_id` | номер задачи в Plane (`sequence_id`) |
| `plane_id` | uuid задачи в Plane (API-адрес) |
| `plane_ref` | готовая ссылка вида `KARAOKE-23` |
| `subject` | тема задачи на момент переноса |
| `op_status` | статус в OpenProject (`Closed`, `Rejected`, `New`, `In progress`) |
| `op_type` | тип в OpenProject (`Task`, `Milestone`) |
| `comments` | сколько комментариев перенесено |
| `attachments` | сколько вложений перенесено |
| `parent_op_id` | номер родителя в OpenProject (для 39 sub-issues) |

Пример строки:

```text
37,23,0312e8b1-e0e5-4a8c-9ca8-e84ae5fe6d49,KARAOKE-23,smoke-test-20260902-180512,Closed,Task,1,0,
```

Поиск по номеру:

```bash
grep -E '^403,' knowledge/system/infra/op-plane-mapping.csv
```

В CLI `tools/tracker.sh` старый номер передаётся с префиксом `op:`:

```bash
./tools/tracker.sh get-issue op:403
```

## Правило ссылок

1. **Новые ссылки — только на Plane** (`KARAOKE-<seq>` или номер Plane).
2. **Старые ссылки `OpenProject #NNN` не переписываются**: они фиксируют факт
   эпохи и остаются историческим свидетельством.
3. **Точечная замена** допустима только в спеках, которые сейчас в работе
   (решение `05` карты `tracker-migration`).
4. **OpenProject остаётся** локальным трекером для ручных задач; его номера
   после миграции продолжают расти и в таблице **не** отражаются — таблица
   покрывает только перенесённые 297 задач.

## Границы

- Таблица **статична**: она описывает перенос 2026-10-27 и не обновляется при
  появлении новых задач. Новые задачи в Plane таблицы не требуют — у них сразу
  есть номер Plane.
- Задачи, заведённые в OpenProject после миграции, в Plane отсутствуют
  (синхронизации нет).
- Таблица не содержит текстов задач и комментариев — только номера, темы и
  счётчики; полные данные перенесены в Plane.

## Связанные ADR | Related ADRs

- [`local-0012-plane-tracker-migration.md`](../../adr/local-0012-plane-tracker-migration.md) —
  Plane как трекер, перенос с OpenProject, сосуществование трекеров.

## Связь с другими компонентами

- **[tracker.md](tracker.md)** — состояние трекера Plane и рабочего цикла.
- **[`knowledge/guidelines/runbooks/tracker-setup.md`](../../guidelines/runbooks/tracker-setup.md)** — команды CLI,
  включая форму `op:<NNN>`, и диагностика.

## Changelog

- **2026-10-27**: Initial (T5 карты `tracker-migration`). Опубликована таблица
  `op_id → KARAOKE-<seq>` на 297 строк и правило «новые ссылки — на Plane».
