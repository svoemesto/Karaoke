# Quickstart: 280 — AssignModal: фильтр по rootId и audioRootId

**Date**: 2026-08-31
**Spec**: [spec.md](spec.md)
**Plan**: [plan.md](plan.md)
**Contracts**: [contracts/contracts.md](contracts/contracts.md)

> Это **руководство по валидации** фичи — какие сценарии прогнать вручную в админке после правки кода, чтобы убедиться, что всё работает. **НЕ** содержит кода реализации (см. `contracts/contracts.md` для UI-контракта) и **НЕ** содержит полного тест-сьюта (его нет в проекте).

## 1. Предусловия (Prerequisites)

### 1.1. Окружение

| Что | Где | Как проверить |
|-----|-----|---------------|
| Admin-стенд | admin-машина | `docker ps \| grep karaoke-webvue3` — контейнер жив |
| База WORKING_DATABASE | admin-машина | `pg_isready -h localhost -p 5432` |
| Браузер | Chrome/Firefox/Safari актуальный | DevTools открыты (для проверки HTTP) |
| Учётка | admin-роль | вход в `/admin` без 403 |

### 1.2. Тестовые данные

В БД должны быть песни с разными `root_id` / `audio_parent_id`, чтобы фильтр имело смысл прогонять.

**Быстрая проверка наличия данных** (SQL через admin-tools или DBeaver):

```sql
SELECT root_id, COUNT(*) FROM song WHERE root_id IS NOT NULL AND root_id > 0 GROUP BY root_id ORDER BY COUNT(*) DESC LIMIT 5;
SELECT audio_parent_id, COUNT(*) FROM song WHERE audio_parent_id IS NOT NULL AND audio_parent_id > 0 GROUP BY audio_parent_id ORDER BY COUNT(*) DESC LIMIT 5;
```

Если `COUNT(*) > 1` хотя бы в одной строке — данные для проверки есть.

### 1.3. Сборка после правки

> **Обязательный порядок** (см. AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода»).

```bash
# 1. Backend compile (НЕ требуется — фича только во фронте)
# ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel

# 2. Линтеры
cd webvue3 && npm run lint
./tools/check-eslint-baseline.sh webvue3

# 3. Frontend Vite (webvue3)
cd webvue3 && npm run build && npm run format:check

# 4. Docker-образ (NON-NEGOTIABLE — Vite-build на хосте ≠ Docker-образ)
cd deploy && bash do.sh build_webvue3

# 5. Перезапуск контейнера webvue3
cd deploy && bash do.sh start_webvue3   # точный скрипт — см. deploy/do.sh help
```

После `start_webvue3` подождать ~5с и проверить `http://localhost:<port>/admin` — админка доступна.

## 2. Сценарии ручной валидации

### SC-1. Базовый фильтр по `root ID`

**Цель**: подтвердить, что ввод `root ID` действительно фильтрует результаты (US1 спеки, SC-001).

1. Открыть админку → «Задания редактора» → в таблице песен нажать «Назначить на разметку» (или аналогичную кнопку, открывающую `AssignModal`).
2. В модалке «Назначить песню на разметку» убедиться, что НОВЫЕ поля «root ID:» и «A-root ID:» присутствуют в строке фильтра справа от «Название песни».
3. В поле «root ID» ввести значение из SQL выше (например, `42`).
4. Нажать «Найти».
5. **Ожидаемо**: список результатов содержит только песни с `root_id == 42`.
6. В DevTools → Network → найти запрос `POST /api/songsdigests` → проверить, что в Payload присутствует `filterRootId=42`.
7. В DevTools → Application → Vuex (или Pinia DevTools) → store `SongEditor` → проверить, что `searchCandidateSongs` был вызван с payload `{ rootId: "42", ... }`.

**PASS-критерии**:
- [ ] В UI список сократился до песен с этим `root_id`.
- [ ] В HTTP-запросе есть `filterRootId=42`.
- [ ] Другие песни (с другим `root_id`) в результатах отсутствуют.

### SC-2. Базовый фильтр по `A-root ID`

**Цель**: подтвердить фильтр по `audio_parent_id` (US1, SC-001).

1. В той же модалке (или открыть заново) ввести в «A-root ID» значение из SQL (например, `17`).
2. Очистить «root ID» (кнопка «✕» справа от поля).
3. Нажать «Найти».
4. **Ожидаемо**: результаты содержат только песни с `audio_parent_id == 17`.
5. В Network → проверить `filterAudioParentId=17`.

**PASS-критерии**:
- [ ] Список сужен до песен с этим `audio_parent_id`.
- [ ] HTTP-запрос содержит `filterAudioParentId=17`.
- [ ] Поле «root ID» пустое, фильтрация по нему НЕ применяется.

### SC-3. AND-комбинация всех фильтров

**Цель**: подтвердить, что новые числовые фильтры комбинируются AND с текстовыми (US1, SC-003).

1. Ввести: «Автор» = часть реального автора (например, `Петров`), «root ID» = конкретное значение, оставить «A-root ID» пустым.
2. Нажать «Найти».
3. **Ожидаемо**: результат = пересечение (песни автора Петрова И с этим root_id).
4. В Network → должны быть `filterAuthor=Петров&filterRootId=<значение>` (без `filterAudioParentId`).

**PASS-критерии**:
- [ ] HTTP-запрос содержит ОБА параметра: `filterAuthor` и `filterRootId`.
- [ ] Количество результатов ≤ количества результатов при поиске только по автору (или только по root_id).

### SC-4. Очистка поля через «✕»

**Цель**: подтвердить изолированный сброс одного поля (US2 спеки, SC-002).

1. Заполнить все 5 полей фильтра (Автор, Альбом, Название, root ID, A-root ID).
2. Нажать «✕» рядом с «root ID».
3. **Ожидаемо**: только `rootIdQuery` очистилось, остальные 4 поля сохранили значения.
4. Нажать «Найти».
5. В Network → `filterRootId` ОТСУТСТВУЕТ, остальные параметры есть.

**PASS-критерии**:
- [ ] После клика «✕» поле «root ID» пусто.
- [ ] Остальные поля не изменились.
- [ ] HTTP-запрос без `filterRootId`, с остальными параметрами.

### SC-5. Пустые числовые поля = отсутствие фильтрации

**Цель**: регрессионная проверка — поведение для тех, кто НЕ пользуется новыми полями (US3, SC-004).

1. Открыть модалку.
2. Оставить «root ID» и «A-root ID» пустыми.
3. Ввести только «Автор» (например, `Петров`).
4. Нажать «Найти».
5. **Ожидаемо**: результат совпадает с поведением ДО фичи — поиск только по автору, фильтры по `root_id` / `audio_parent_id` НЕ применяются.

**PASS-критерии**:
- [ ] HTTP-запрос НЕ содержит `filterRootId` и `filterAudioParentId`.
- [ ] Количество результатов совпадает с количеством ДО фичи (сравнить с предыдущей версией, если есть).

### SC-6. Невалидный ввод

**Цель**: подтвердить, что нечисловой ввод НЕ ломает UI и НЕ отправляется как фильтр (US3 edge case, SC-005).

1. Ввести в «root ID»: `abc`.
2. Нажать «Найти».
3. **Ожидаемо**: HTTP-запрос НЕ содержит `filterRootId` (или содержит пустое значение — поведение как для пустого поля).
4. UI не показывает HTTP 400 / сообщение об ошибке от бэкенда.

**PASS-критерии**:
- [ ] Нет HTTP 400 в Network.
- [ ] В списке результатов — все песни по фильтру «Автор» (без фильтрации по root_id).
- [ ] Никакого красного сообщения об ошибке в модалке.

### SC-7. Сохранение состояния фильтра между переключениями (regression)

**Цель**: убедиться, что фильтр сбрасывается при повторном открытии модалки (FR-011 спеки, Assumption A-5).

1. Открыть модалку → ввести «root ID» = `42` → нажать «Найти» → получить результаты.
2. Не закрывая модалку, нажать «Отмена».
3. Снова открыть модалку (та же или другая песня — неважно).
4. **Ожидаемо**: «root ID» пустое (новая инициализация компонента).

**PASS-критерии**:
- [ ] Поле «root ID» пусто при повторном открытии.
- [ ] Поведение совпадает с «Автор»/«Альбом»/«Название» (они тоже сбрасываются).

### SC-8. Валидация через DevTools (доп.)

**Цель**: убедиться, что HTML-атрибуты валидации работают.

1. Открыть DevTools → Elements → найти `<input>` «root ID».
2. Проверить атрибуты: `type="text"`, `inputmode="numeric"`, `pattern="[0-9]*"`.

**PASS-критерии**:
- [ ] Все три атрибута присутствуют.
- [ ] `pattern` НЕ валидирует браузерный submit (только hint).

### SC-9. Визуальная консистентность с SongsFilterModal

**Цель**: подтвердить, что метки и стиль новых полей совпадают с `SongsFilterModal.vue`.

1. Открыть `/admin/songs` (или аналогичную страницу с общим фильтром песен) → открыть фильтр → найти поля «root ID:» и «A-root ID:» (SongsFilterModal.vue:85-113).
2. Сравнить визуально с полями в `AssignModal.vue`.

**PASS-критерии**:
- [ ] Метки идентичны по тексту.
- [ ] Шрифт / отступы / форма — консистентны.
- [ ] Кнопки очистки «✕» одинаковые.

## 3. После-валидационные проверки

### 3.1. Линтеры / бейзлайн

```bash
cd /home/nsa/Karaoke/webvue3 && npm run lint
cd /home/nsa/Karaoke && ./tools/check-eslint-baseline.sh webvue3
```

**Ожидаемо**: 0 новых ESLint-нарушений. `webvue3/.eslint-baseline.json` не изменился (или уменьшился, если заодно починили старые).

### 3.2. Docker-сборка

```bash
cd /home/nsa/Karaoke/deploy && bash do.sh build_webvue3
```

**Ожидаемо**: exit 0, в конце `Status: Downloaded newer image` (или пересборка прошла без ошибок).

### 3.3. JSDoc

- [ ] В `store.js` action `searchCandidateSongs` имеет JSDoc-комментарий с описанием новых параметров (`@param` для `rootId`, `audioRootId`) и `@see` ссылкой на `specs/280-assign-modal-root-audio-id/spec.md`.

### 3.4. LiveDoc

- [ ] Создан файл `livedocs/features/280-assign-modal-root-audio-id.md` со статусом `Active`, `slug: 280-assign-modal-root-audio-id`, `related:` (минимум — на `263-editor-task-review-modal.md`, `017-editor-status-bypass.md`, `architecture/L3-components.md`), `FR-014` AGENTS.md.

## 4. Откат (Rollback)

Если SC-1..SC-9 падают — откатить ветку `280-assign-modal-root-audio-id`:

```bash
# На admin-машине
cd ~/Karaoke
git checkout master
git branch -D 280-assign-modal-root-audio-id
cd deploy && bash do.sh build_webvue3 && bash do.sh start_webvue3
```

Никаких миграций БД откатывать не нужно (схема не менялась).

## 5. Чек-лист «готово к PR»

- [ ] SC-1..SC-9 PASS
- [ ] `npm run lint` PASS, baseline не вырос
- [ ] `npm run build && npm run format:check` PASS
- [ ] `bash do.sh build_webvue3` PASS
- [ ] JSDoc на изменённом action — есть
- [ ] LiveDoc создан
- [ ] Никаких секрет-файлов в `git status`
- [ ] Commit-message: `webvue3: 280 AssignModal — фильтр по root ID и A-root ID`
- [ ] PR в `master` через `gh pr create --base master`
