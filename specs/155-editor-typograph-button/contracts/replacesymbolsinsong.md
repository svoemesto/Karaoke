# Contract: `POST /api/replacesymbolsinsong`

**Статус**: Уже реализован и задеплоен, эндпоинт эксплуатируется без изменений. Эта фича его не
модифицирует — документ фиксирует существующий контракт для обоих новых вызывающих (`webvue3`
online editor, `karaoke-public` online editor), чтобы реализация в обоих местах была консистентна.

**Источник**:
- Controller: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:5129`
- Бизнес-логика: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:1460`
  (`fun replaceSymbolsInSong(sourceText: String): String`)
- Существующий вызывающий код (эталон поведения): `SubsEdit.vue:4287` (`doReplaceText()`) →
  `webvue3/src/components/Songs/store.js:575` (`getReplacedSymbolsInText`)

## Request

```
POST /api/replacesymbolsinsong
Content-Type: application/x-www-form-urlencoded

txt=<исходный текст песни (голоса)>
```

- `txt` — обязательный, произвольная строка (в т.ч. пустая или без кириллицы).
- Авторизация не требуется — путь не подпадает под `/api/private/**` → `permitAll()` в
  `SecurityConfig.kt`.

## Response

```
200 OK
Content-Type: text/plain (сырая строка, НЕ JSON)

<исправленный текст>
```

**Важно для вызывающего кода**: тело ответа — обычная строка, а не JSON-документ. Клиент
**не должен** вызывать `JSON.parse()` на ответе (в `karaoke-public` есть `services/api.js#apiPost()`,
который это делает, — для этого эндпоинта он не подходит, см. `research.md` §2). Использовать
низкоуровневый `promisedXMLHttpRequest`, который отдаёт `xhr.response` как есть.

## Что делает замена (для справки, реализация не меняется)

`replaceSymbolsInSong()` применяет фиксированный набор правил (Ё-словарь, кавычки-«ёлочки»,
нормализация тире/дефисов, пробелов вокруг запятой/двоеточия, авто-переносы строк по заглавным
буквам, удаление строк из одних только "аккордовых" символов, транслитерация похожих латинских
букв в кириллицу при наличии русских букв в тексте). **Значимо для интеграции**: правила могут
менять количество и содержимое строк текста (не только отдельные символы внутри строки) — поэтому
после вызова обязательна пересинхронизация маркеров с новым текстом (см. `research.md` §3), а не
точечное обновление символов на месте.

## Ошибки

- Сетевая ошибка / не-2xx статус → `promisedXMLHttpRequest` реджектит промис. Вызывающий код
  MUST поймать ошибку, не менять `sourceText`, показать пользователю индикатор ошибки (см.
  `research.md` §5).

## Потребители (после этой фичи)

| Потребитель | Файл | Существовал до фичи? |
|---|---|---|
| Классический редактор (SubsEdit) | `webvue3/src/components/Songs/edit/SubsEdit.vue` | Да (эталон) |
| Онлайн-редактор, админка | `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` | Нет — добавляется этой фичей |
| Онлайн-редактор, публичный сайт | `karaoke-public/src/views/EditorWorkView.vue` | Нет — добавляется этой фичей |
