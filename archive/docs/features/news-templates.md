# Шаблоны автоматических новостей сайта

> **Status**: active (specs/128-news-publish-templates, specs/139-fix-censored-dictionary)
> **Feature Key**: news-templates
> **Last Updated**: 2026-08-04

## Что делает

Третья вкладка/платформа «Новости сайта» в существующем компоненте
`PublishTemplatesView.vue` (наравне с ВК/Telegram), позволяющая
администратору редактировать заголовок и тело автоматических новостей
двух категорий (`air`, `premium`) с поддержкой плейсхолдеров, превью и
сброса к дефолту. Шаблоны (4 строковых ключа) хранятся в
`tbl_public_settings` (Postgres — единый источник истины, доступный и с
admin, и с прода), читаются прямым JDBC-запросом в момент создания
auto-новости (`SongReleaseAnnouncementService`), что заменяет текущие
хардкод-формулировки.

## Зачем

Снять зависимость поведения auto-новостей от разработчика: сейчас чтобы
поменять формулировку «вышла в эфир»/«появилась в коллекции» нужно
править Kotlin-код и пересобирать/деплоить `karaoke-app`. С этой фичей
администратор правит шаблон в webvue3 → следующая auto-новость уже
содержит новый текст (FR-008, SC-001).

## Как работает

### Архитектура

```text
webvue3/src/views/PublishTemplatesView.vue        # вкладка «Новости сайта»
  └─ webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue  # UI редактор

GET  /api/news/templates?target=local|remote      # → список 4 ключей + placeholders
POST /api/news/templates  (UPSERT)                # → сохранить ключ+value
POST /api/news/templates/preview                  # → рендер на тестовой песне
GET  /api/news/templates/defaults                 # → 4 заводских значения

karaoke-app/.../controllers/NewsTemplateController.kt  # 4 endpoints
karaoke-app/.../services/NewsTemplateService.kt        # render/template/PLACEHOLDERS

SongReleaseAnnouncementService.kt
  ├─ detectAndAnnounceAvailability (premium)  → NewsTemplateService.template + render
  └─ checkOnAirWindow          (air)        → NewsTemplateService.template + render
                                               ↓
                                          News.createAutoAnnouncement (kill-switch в News.kt:393)
```

### Хранение

| Ключ `tbl_public_settings` | Категория | Поле | Дефолт из кода |
|---|---|---|---|
| `newsTemplateAirTitle` | `air` | `title` | `"Новая песня: {author} — {songName}{albumYearSuffix}"` |
| `newsTemplateAirBody` | `air` | `body` | `"Песня «{songName}» ({bodyDetails}) вышла в эфир."` |
| `newsTemplatePremiumTitle` | `premium` | `title` | `"Новая песня: {author} — {songName}{albumYearSuffix}"` |
| `newsTemplatePremiumBody` | `premium` | `body` | `"Песня «{songName}» ({bodyDetails}) появилась в коллекции."` |

Значения читаются через прямой JDBC к `tbl_public_settings` при каждом
создании auto-новости (без статического кеша — иначе не выполнить
FR-008/SC-005 «без перезапуска»).

### Плейсхолдеры

14 штук (полный список — `NewsTemplateService.PLACEHOLDERS`):

- 12 granular (как у ВК/Telegram): `{author}`, `{songName}`,
  `{songNameCensored}`, `{year}`, `{album}`, `{link}`, `{id}`,
  `{newsBody}`, `{descriptionHeader}`, `{descriptionFooter}`,
  `{description}`, `{descriptionWithTimecodes}`.
- 2 составных (byte-идентичны хелперам из
  `SongReleaseAnnouncementService`): `{albumYearSuffix}`,
  `{bodyDetails}` — инкапсулируют старую логику формирования
  суффикса/тела. Дефолты используют только составные (FR-010).
- `{demoVideo}` НЕ включён — для `tbl_news` не имеет смысла
  (видео в текст новости не вставляется).

### UPSERT без миграции

`NewsTemplateController.save` использует
`INSERT ... ON CONFLICT (key) DO UPDATE` (R2 research.md). Это значит:
- первый `POST /api/news/templates` для несуществующего ключа
  создаёт строку в `tbl_public_settings` (INSERT);
- последующие — обновляют существующую (UPDATE);
- seed-миграция НЕ нужна.

## Инварианты / правила

1. **Byte-идентичность дефолтов** (FR-010, SC-002).
   `NewsTemplateService.DEFAULT_*` повторяют прежние хардкод-строки
   `SongReleaseAnnouncementService` один-в-один. До первого сохранения
   администратором рендер читает отсутствующий ключ → `template()`
   возвращает `defaultFor(key)` → результат неотличим от прежнего
   поведения.

2. **Kill-switch сохраняет приоритет** (FR plan.md «Non-Goals»).
   `News.createAutoAnnouncement` (`News.kt:337`) вызывает
   `isNewsAutoPublishKillSwitchActive` ДО записи в БД. Если
   `newsAutoPublishKillSwitch = "true"` в `tbl_public_settings` —
   возвращается `null`, новость не создаётся, рендер шаблона
   вызывается впустую (микро-оптимизация: в горячем пути
   `Song.loadFromDbById` уже произошёл, JDBC за шаблоном — лишний
   запрос; оставлено как есть ради простоты и KISS).

3. **Изменения БЕЗ перезапуска** (FR-008, SC-005). Чтение шаблона
   идёт прямым JDBC при каждом создании auto-новости (~1 SELECT на
   новость), без статического кеша. Изменение `tbl_public_settings`
   через `target=remote` применяется к следующему тику
   `SongReleaseAnnouncementScheduler`.

4. **`tbl_public_settings` НЕ участвует в LOCAL↔SERVER sync**
   (R1 research.md). Это settings key/value, не `KaraokeDbTable`,
   нет `recordhash`-триггеров. Расхождение значений — ручная
   ответственность администратора через `target=local|remote` (по
   образцу `News.isNewsAutoPublishKillSwitchActive`,
   `CaptchaConfigService`).

5. **`title` усекается до 500 символов** (FR-010a).
   `NewsTemplateService.render(template, song, ..., truncate=true)`
   обрезает результат до `NEWS_TITLE_MAX_LENGTH=500` с суффиксом `…`.
   `body` (`tbl_news.body` = TEXT) усечению НЕ подлежит — рендерится
   с `truncate=false`.

6. **Лимит 4 ключей** (FR-012). `POST /api/news/templates` с любым
   другим `key` → `{success: false, error: "unknown key: ..."}`. Не
   позволяет администратору случайно создать мусорную строку.

7. **Превью не создаёт запись** (FR-009). `POST
   /api/news/templates/preview` рендерит, но не вызывает
   `News.createAutoAnnouncement`. `count(*) FROM tbl_news WHERE
   source='auto' AND created_at > now() - interval '1 minute'` не
   должен меняться после превью.

## Известные ловушки

- **`/api/vk/templates/*` и `/api/news/templates/*` — разные паттерны**
  (R3). VK использует `KaraokeProperties`-файл (через
  `KaraokeProperties.set`/`getString`), News использует `tbl_public_settings`
  через прямой JDBC + UPSERT. Не пытаться делегировать News на
  generic `PublicSettingsController.update` — тот делает только UPDATE,
  упадёт на первом сохранении нового ключа.

- **`target=remote` обязателен для прода**. По умолчанию в UI
  `NewsTemplatesEditor` стоит `target=remote`, но если кто-то
  переключит на `local` — сохранение пойдёт в admin-БД, а прода
  продолжит использовать дефолт из кода. В UI есть явная подсказка.

- **`Song.loadFromDbById` в preview всегда идёт в WORKING_DATABASE**
  (`Constants.kt:204`), даже если `target=remote`. Логика: превью —
  операция админа на admin-машине, целесообразно подгружать тестовую
  песню из admin-БД (где актуальная версия после sync). Если
  потребуется превью на проде — переключить `WORKING_DATABASE` на
  `Connection.remote()` через комментарий-константу в `Constants.kt`.

- **KDoc с `/*` ломает парсер** (AGENTS.md Q&A). Не использовать
  нотацию `/api/.../...` в KDoc — `/*` открывает nested-комментарий,
  компилятор падает с «Unclosed comment». В KDoc NewsTemplateController
  написано `/api/vk/templates/...` (без `*`).

- **BOM-эмодзи и не-ASCII в шаблонах работают корректно** через
  Postgres TEXT и UTF-8 Spring HTTP. Прецедент — уже используется
  в ВК/Telegram-шаблонах.

- **`{songNameCensored}` игнорировал переданный `database` до specs/139-fix-censored-dictionary**
  (исправлено). `NewsTemplateService.render()` рендерит `songNameCensored` через
  `song.songName.censored()`, а эта функция раньше не принимала параметр `database` вовсе — всегда
  читала словарь «Censored» через глобал `com.svoemesto.karaokeapp.WORKING_DATABASE`, даже когда
  вызывающий код (`SongReleaseAnnouncementScheduler`, единственная точка кода, создающая
  auto-новость — см. диаграмму выше) явно передавал свой, правильный `com.svoemesto.karaokeweb.WORKING_DATABASE`
  на уровень выше. На проде (внутри JVM `karaoke-web`) чужой `karaoke-app`-глобал резолвится в
  постороннее соединение молча, без исключения (`docs/invariants.md`, «Ловушки karaoke-web») —
  словарь читался пустым, `{songNameCensored}` не цензурировал ничего, без единого следа в логах.
  Теперь `render()`/`buildReplacements()` принимают `database: KaraokeConnection` и прокидывают его
  в `.censored(database)`; `SongReleaseAnnouncementService` передаёт уже имеющийся у себя `database`
  на этот уровень. Тот же параметр добавлен в `VkTemplateService`/`TelegramTemplateService` для
  консистентности (эти пути не были затронуты багом — рендерятся только внутри `karaoke-app` на
  admin-машине, где глобал и так корректен). См. `specs/139-fix-censored-dictionary/research.md` R1/R2.

## Ссылки

- Спецификация: [specs/128-news-publish-templates/spec.md](../../specs/128-news-publish-templates/spec.md)
- Research: [specs/128-news-publish-templates/research.md](../../specs/128-news-publish-templates/research.md)
- Data model: [specs/128-news-publish-templates/data-model.md](../../specs/128-news-publish-templates/data-model.md)
- API контракт: [specs/128-news-publish-templates/contracts/api.md](../../specs/128-news-publish-templates/contracts/api.md)
- Quickstart: [specs/128-news-publish-templates/quickstart.md](../../specs/128-news-publish-templates/quickstart.md)
- Tasks: [specs/128-news-publish-templates/tasks.md](../../specs/128-news-publish-templates/tasks.md)
- Смежная фича VK: [vk-news-auto-publish.md](./vk-news-auto-publish.md)
- Смежная фича Telegram: [telegram-auto-publish.md](./telegram-auto-publish.md)
- Kill-switch (читается перед рендером): [News.kt:393](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt)
- Двух-БД sync: [dual-db-sync.md](./dual-db-sync.md)
- Фикс `{songNameCensored}`: [specs/139-fix-censored-dictionary/spec.md](../../specs/139-fix-censored-dictionary/spec.md)
