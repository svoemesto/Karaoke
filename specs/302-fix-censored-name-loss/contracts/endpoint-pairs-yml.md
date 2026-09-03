# Config Contract: `tools/endpoint-pairs.yml`

**Spec**: [spec.md](../spec.md) | **Research**: [research.md](../research.md) → Decision 4

## Назначение

Список пар «Vue-компонент редактирования ↔ backend-эндпоинт update»,
которые проверяет `tools/check-endpoint-field-coverage.sh` (FR-007).
Позволяет расширять аудит без изменения кода чека.

## Формат

```yaml
# tools/endpoint-pairs.yml
#
# Формат: каждая запись — пара UI-компонент + backend-эндпоинт,
# которые должны быть покрыты check-endpoint-field-coverage.sh.
#
# Поля:
#   - component: путь к Vue-компоненту (относительно корня репо).
#   - endpoint: HTTP-эндпоинт (path).
#   - method: HTTP метод (POST/PUT/PATCH — обычно POST).
#   - controller: путь к Kotlin-файлу контроллера.
#   - controller_method: имя метода контроллера.

pairs:
  - component: webvue3/src/components/Songs/edit/SongEdit.vue
    endpoint: /api/song/update
    method: POST
    controller: karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt
    controller_method: songs2Update

  # === Следующие пары добавляются в будущих раундах ===
  # - component: webvue3/src/components/Albums/edit/AlbumEdit.vue
  #   endpoint: /albums/updatealbum
  #   method: POST
  #   controller: karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt
  #   controller_method: albums2Update
  #
  # - component: webvue3/src/components/Authors/edit/AuthorEdit.vue
  #   endpoint: /authors/updateauthor
  #   method: POST
  #   controller: karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt
  #   controller_method: authors2Update
```

## Схема (псевдо-Schema)

```yaml
type: object
required: [pairs]
properties:
  pairs:
    type: array
    items:
      type: object
      required: [component, endpoint, method, controller, controller_method]
      properties:
        component:
          type: string
          description: "Путь к Vue-компоненту (относительно корня репо)."
        endpoint:
          type: string
          description: "HTTP endpoint path, начинается с /."
          pattern: "^/"
        method:
          type: string
          enum: [POST, PUT, PATCH]
        controller:
          type: string
          description: "Путь к Kotlin-файлу контроллера (относительно корня репо)."
        controller_method:
          type: string
          description: "Имя метода контроллера (Kotlin function name)."
```

## Семантика

**`tools/check-endpoint-field-coverage.sh`** для каждой пары:

1. Извлечь все `v-model="<model>.<key>"` из `component` (где `<model>` —
   переменная в `data()` или computed, обычно `song`/`author`/`album`).
   Парсинг: `grep -oE 'v-model="[a-zA-Z]+\.[a-zA-Z]+"' <component>`.
2. Извлечь все `@RequestParam` ИЛИ `Map<String, String> all` из
   `controller_method` в `controller`. Парсинг: контекстный grep от
   имени метода до следующего `@PostMapping` / `@ResponseBody`.
3. Применить global whitelist `tools/check-endpoint-field-coverage.whitelist.yml`.
4. Для каждого `<key>` из (1) проверить, что он есть в (2) ИЛИ в whitelist.
5. Если все ключи покрыты → OK для этой пары.
6. Итог: exit 0 если все пары OK, exit 1 если хоть одна нет.

## MVP (эта спека)

Содержит ОДНУ пару: `SongEdit.vue ↔ /api/song/update`. Этого
достаточно для FR-005/006 + FR-007/008 (общий чек работает на
любом непустом списке).

**Acceptance Scenario #2 (FR-007)**: чек корректно ловит баг
`songNameCensored` в этой паре (до фикса — exit 1 с
`MISSING: songNameCensored`).

## Расширение

Добавление новой пары:
1. Создать `<Component>.vue` с `v-model="<model>.<field>"`.
2. Добавить `@RequestParam` / `Map<String, String> all` в эндпоинт.
3. Добавить запись в `tools/endpoint-pairs.yml`.
4. (Опционально) Добавить whitelist entries, если используется
   non-standard setter.
5. Запустить `tools/check-endpoint-field-coverage.sh` → должен
   выдать exit 0.

Если компонент использует несколько `<model>` (например, `song` и `coAuthorAuthorIdToAdd`),
парсер должен извлекать оба — это уточняется при реализации.

## Edge Cases

- **Компонент не существует** (`component: foo/bar/Baz.vue` не найден):
  парсер выводит WARN и skip пары (не error).
- **Эндпоинт не существует** (`/api/foo/update` не найден в контроллере):
  парсер выводит WARN и skip пары.
- **YAML-файл не валиден**: парсер выводит ERROR с указанием строки
  и exit 2 (не 1, чтобы отличать от «поля не покрыты»).
- **`pairs: []` (пустой массив)**: парсер выводит «INFO: нет пар для
  проверки» и exit 0.

## Связанные файлы

- `tools/check-endpoint-field-coverage.sh` — сам чек (FR-007).
- `tools/check-endpoint-field-coverage.whitelist.yml` — глобальный
  whitelist (FR-008).
- `tools/check-songedit-field-coverage.sh` — частный случай
  (одна пара, без yml-конфига).
- `tools/check-songedit-field-coverage.whitelist.yml` — whitelist
  для SongEdit-чека.
