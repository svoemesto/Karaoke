# Постановка задачи для продолжения работы (Pass M-23 → другому агенту)

> **Как использовать**: скопируйте **весь блок «ПРОМТ НИЖЕ»** в первую реплику
> новому AI-агенту (Claude Code, Cursor, Aider, Cody, другой opencode сессии).
> Агент прочитает указанные файлы и подхватит работу с того же места.

---

## ПРОМТ НИЖЕ (скопировать)

```
Контекст: продолжаю работу над фичей M-23 «Спецзаказные» в репо Karaoke/svoemesto,
которая сейчас в процессе на другой машине. В master уже влито всё, что описано
ниже — нужно доделать оставшиеся пункты.

## Что уже сделано (в master, коммит d93497b)

1. `@JsonProperty("isSpecialOrder")` на boolean-полях DTO `AuthorDTO` (karaoke-app)
   и `AuthorTilePublicDto` (karaoke-web) — фикс главной причины бага.
2. Прямой SQL UPDATE в `ApiController.apisUpdateAuthor` (вместо `save()`/`getDiff()`).
3. Плашка «Спецзаказные» как обычный тайл в `ZakromaView` (karaoke-public),
   доступ через `?specialBucket=true`.
4. Колонка + boolean-поле в `AuthorsTable.vue` (webvue3 — админка).
5. Пометка «по спецзаказу» в `AuthorPlaylistView.vue` (публичный).
6. Миграция `deploy/karaoke-db/27_author_special_order.sql` — НЕ применена
   автоматически (агент не имеет доступа к LOCAL/PROD psql). СПРОСИ у меня
   на старте, применена ли она.
7. Урок про Jackson `is`-boolean-фикс в `AGENTS.md` (v1.4.0, Q&A секция).

## Чего НЕ сделано (твой backlog)

Из фидбэка пользователя остались 4 проблемы:
- Плашка «отвратительно выглядит» — нужна переделка визуала.
- Группировка «плоская» — нужно «Автор → Альбом → Песни», не плоский список.
- Spinner «вечный» в ZakromaView — нужен правильный бэкенд эндпоинт
  `/api/public/zakroma?specialBucket=true` одним запросом (не N+1
  через `/api/public/player/readiness`, он чанкирован по 20 песен).
- «Отдельная страница» — желательно `/special-bucket` со ссылкой
  из навигации (главная или футер).

## Обязательно прочитай перед началом (порядок важен)

1. .specify/memory/constitution.md — NON-NEGOTIABLE принципы (JSON `is`-boolean,
   Stripe-like object-design, Git workflow 0XX-name, RSS 2026-07+, Hooks).
2. AGENTS.md — общие правила (читай именно ДО задачи, там Q&A про всё).
3. docs/onboarding.md — setup машины.
4. DEVELOPMENT.md — архитектура, команды.
5. CONTRIBUTING.md — стиль кода (Kotlin/Vue/SQL/Docker).
6. docs/architecture-notes.md — changelog (Pass 1-24).
7. docs/features/dual-db-sync.md — sync LOCAL↔SERVER нюансы.
8. docs/features/stats.md — SQL-формулы счётчиков главной.
9. docs/strategy/growth.md — воронка и roadmap (M-23 в roadmap).
10. Спецификация текущей фичи (если есть): specs/008-special-orders/spec.md —
    обычно лежит рядом.

Также прочти karaoke-app/.../Author.kt, AuthorDTO.kt,
karaoke-web/.../PublicApiController.kt, karaoke-web/.../PublicPlayerController.kt,
karaoke-public/.../views/ZakromaView.vue, webvue3/.../AuthorsTable.vue,
store/modules/zakroma.js и composables/usePlayerReadiness.js — там вся логика.

## Главный принцип (повторять не забывать)

**Все boolean-поля `isXxx` в DTO требуют `@get:JsonProperty("isXxx")`.**
Без этого Jackson отбросит префикс и фронт получит пустые данные.
См. Q&A в AGENTS.md v1.4.0 и PR #49 (исторический случай).

Ещё: boolean-апдейты через админку — НЕ `save()`/`getDiff()` (recordhash-триггер
ломает sync LOCAL↔SERVER), только прямой SQL UPDATE через `@RequestParam`.

## Порядок работы (предлагаю)

1. Создать ветку `012-special-bucket-page` от master.
2. Спросить меня: применена ли миграция 27 на LOCAL/PROD? Если нет —
   дать инструкцию по применению (если миграция не применена, поля
   `is_special_order` в БД будут null).
3. Бэкенд `/api/public/zakroma?specialBucket=true` — одним запросом список
   спецзаказных авторов + их песни с правильной группировкой.
4. Создать `SpecialBucketView.vue` + маршрут `/special-bucket` + ссылку
   в навигации (главная или футер).
5. Удалить старую логику (`virtualSpecialZak`, `displayedZakroma`,
   `isSpecialBucketSelected`) из `ZakromaView.vue`.
6. Удалить `loadSpecialBucket` из `store/modules/zakroma.js`.
7. Запустить `./gradlew ktlintCheck`, `npm run lint:check`, prettier,
   `bash tools/check-feature-doc.sh docs/features/*.md`. Запушить в PR
   с лейблом `feature`. Не мёржить — это сделаю я сам.
8. Создать docs/features/special-orders.md (per-feature документ
   ОБЯЗАТЕЛЕН, FR-009) если ещё не создан.

## Чего НЕ делать в этом раунде

- Не мёржить ничего самому (только PR создать).
- Не применять никакие миграции автоматически (только я).
- Не трогать `karaoke-app`, если явно не попрошу (только на admin-машине).
- Не редактировать `deploy/.env`, `deploy/do.env` (секреты).
- Не ломать обратной совместимости (всё аддитивное, opt-in).

## Как со мной общаться

- Абсолютный язык — **русский** (см. AGENTS.md v1.4.0 правило № 1).
- Комментарии и KDoc/JSDoc — на русском.
- Прежде чем удалять/переписывать большие куски — спроси.
- Перед коммитом — `./gradlew ktlintCheck && npm run lint:check && pre-commit`.
- Если не уверен — уточни через `question` с `<options>`.

Начни с чтения всех 10 пунктов и спроси, какую конкретно из 4 нерешённых задач
делать первой. См. также my journal `~/.config/opencode/journal/2026-07-24-session-m23-special-orders.md`
если доступен (там всё от старой сессии — обсуждение и причины решений).
```

---

## Почему промт такой

1. **Контекст** — что в master, что нет → агенту не нужно догадываться.
2. **Конкретные файлы** — агенту сразу понятно, куда смотреть.
3. **Главный принцип поверх файлов** — Jackson `is`-boolean, без `JsonProperty`
   данные не доходят до фронта → повторяется даже если агент не прочитает AGENTS.md.
4. **Порядок работы** — миграция первым делом (часто блокер), потом эндпоинт
   (бэкенд), потом фронт.
5. **Ограничения** — что НЕ делать (особенно «не мёржить, не применять миграции»).
6. **Язык** — обязательно русский (NON-NEGOTIABLE в AGENTS.md).

## Нюансы

- Если новый агент — Claude Code, ему нужен локальный `CLAUDE.md` (см.
  `docs/claude-code-setup.md`). Скопировать шаблон
  `docs/CLAUDE.md.template` в `CLAUDE.md` (локально) и подставить своё имя/пути.
- Если Cursor — настроить `.cursorrules` (см. `docs/onboarding.md`).
- Если Aider/Cody — у них свои форматы конфигов.
- Если новый opencode на той же машине, но без истории сессии — этот же
  промт работает, конфиг уже на месте.
- Журнал `~/.config/opencode/journal/2026-07-24-session-m23-special-orders.md`
  содержит полное обсуждение и причины решений — очень полезен, но
  **не обязателен** (промт выше самодостаточен).
