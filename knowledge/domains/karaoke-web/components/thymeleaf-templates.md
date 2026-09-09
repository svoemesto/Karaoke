# Component: Thymeleaf templates (karaoke-web)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: 8 Thymeleaf-шаблонов `templates/`.

## Назначение

`karaoke-web/src/main/resources/templates/*.html` — Thymeleaf
HTML-шаблоны для статических страниц (не SPA). Рендерятся
сервер-сайд через `MainController` (см. [main-controller.md](main-controller.md)).

## Каталог (8 шаблонов)

| # | Шаблон | Endpoint | Что |
|---|---|---|---|
| 1 | `main.html` | `GET /` | Главная |
| 2 | `zakroma.html` | `GET /zakroma` | Закрома автора |
| 3 | `song.html` | `GET /song` | Страница песни |
| 4 | `song-removed.html` | — | Страница для удалённых песен |
| 5 | `filter.html` | `GET /filter` | Фильтр |
| 6 | `statbysong.html` | `GET /statbysong` | Счётчики (StatBySong) |
| 7 | `webevents.html` | `GET /webevents` | События |
| 8 | `testpage.html` | `GET /testpage/{id}` | Тестовая страница |

## Архитектура

### Thymeleaf-шаблон (типичный)

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>...</title>
    <link rel="stylesheet" href="/css/...">
</head>
<body>
    <h1 th:text="${title}">Default title</h1>
    <div th:each="item : ${items}">
        <span th:text="${item.name}">Item</span>
    </div>
</body>
</html>
```

### Server-side render

```kotlin
@GetMapping("/")
fun main(model: Model): String {
    model.addAttribute("title", "Karaoke")
    model.addAttribute("items", items)
    return "main"  // → templates/main.html
}
```

## Hot paths

- Каждое открытие `/`, `/zakroma`, `/song` = Thymeleaf render
  (server-side, без SPA).
- SSR-кеширование (если есть) — `Cache-Control: max-age=N` (Pass 343+).

## Связь

- [main-controller.md](main-controller.md) — рендерит эти шаблоны.
- [services-overview.md](services-overview.md) — данные для шаблонов.

## Известные TODO

- [ ] **CSS/JS** — где определены (Pass 343+)?
- [ ] **Thymeleaf version** — какая используется?
- [ ] **i18n** — поддерживается ли (если нет — добавить, см. CLAUDE.md).
- [ ] **Кеширование** — `Cache-Control` headers, ETag.

## Changelog

- **Pass 439** (2026-09-09): Initial. Автор: agent (Karaoke).