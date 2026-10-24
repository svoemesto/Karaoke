# Безопасный рендер страниц karaoke-web (новости и статистика)

> **Status**: active
> **Feature Key**: web-render-safety
> **Last Updated**: 2026-10-24
> **Spec**: [specs/378-pkg-s2-15/report.md](../../specs/378-pkg-s2-15/report.md)
> Per-feature документ по [Constitution VI FR-009](../../.specify/memory/constitution.md).

## Что делает

Гарантирует, что Thymeleaf-шаблоны `main.html` и `statbysong.html` рендерятся без обращения
к отсутствующим полям DTO, а ссылка новости попадает в `href` только после проверки схемы.

## Зачем

Аудит #336 (BR-500, BR-504, порция P-39).

- `statbysong.html` обращался к `stat.cntBoosty`, которого нет в `StatBySongDto`: SpEL бросал
  `EL1008E`, и вся страница `/statbysong` отдавала `500` (колонка Boosty заменена в DTO на
  `cntMax`/`cntSponsr`).
- `main.html` подставлял `tbl_news.link` в `href` «как есть»: Thymeleaf экранирует
  HTML-сущности, но не проверяет схему URL — `javascript:` исполнялся по клику. Значение
  пишется неаутентифицированным `POST /api/news/create|update` в `karaoke-app`.

## Как работает

1. **Схема ссылки** — `util/SafeLink.isSafe(link)`: разрешены только `http://`/`https://`;
   пустые значения, относительные пути, `javascript:`, `data:`, `vbscript:`, управляющие
   символы и пробелы внутри URL отклоняются.
2. **Модель главной** — `MainController.main` прогоняет `latestNews` через `SafeLink`;
   небезопасная ссылка обнуляется (`link = ""`), в журнал — предупреждение с заголовком.
3. **Шаблон** — `main.html` рендерит `<a th:href>` только при непустом `link`; иначе заголовок
   выводится `<span>` без `href`. `data-link` пустой, поэтому `window.open` по клику не
   срабатывает (в JS добавлена проверка `if (l)`).
4. **Статистика** — из `statbysong.html` удалены заголовок и ячейка `cntBoosty`; страница
   снова рендерится.

## Инварианты

- В разметке главной не может появиться `href`/`data-link` с небезопасной схемой.
- Новость без безопасной ссылки видна как текст (заголовок не теряется).
- Ни один шаблон не обращается к полям, которых нет в DTO (рендер-тест падает первым).

## Известные ловушки

- Валидация схемы на стороне записи (`karaoke-app` `NewsController.link`) не сделана —
  вне пакета; защита стоит на рендере. Кроме того, `/api/news/*` в `karaoke-app` не требует
  аутентификации (отдельная находка, не входит в пакет).
- Проверка на рендере опирается на то, что модель уже прошла `SafeLink`; другие контроллеры,
  которые положат `news.link` в модель, должны сделать то же.
- `ThymeleafTemplatesTest` рендерит шаблоны на test-классpath; при переименовании view
  (`return "main"`) тест нужно обновить вместе с контроллером.

## Ссылки

- [Отчёт пакета PKG-S2-15](../../specs/378-pkg-s2-15/report.md)
- [Карточка аудита P-39](../../specs/336-code-audit/findings/P-39.md)
- [SafeLink.kt](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/util/SafeLink.kt)
- [MainController.kt](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt)
- [main.html](../../karaoke-web/src/main/resources/templates/main.html)
- [statbysong.html](../../karaoke-web/src/main/resources/templates/statbysong.html)
- [thymeleaf-templates.md](../../knowledge/domains/karaoke-web/components/thymeleaf-templates.md)
- [ThymeleafTemplatesTest.kt](../../karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/templates/ThymeleafTemplatesTest.kt)

## Changelog

- **2026-10-24** (BR-500/BR-504, OpenProject #378): удалена колонка `cntBoosty`, ссылка новости
  проверяется по схеме до рендера, добавлен рендер-тест шаблонов.
