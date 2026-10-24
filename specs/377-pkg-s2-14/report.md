# Отчёт по пакету PKG-S2-14 (OpenProject #377, аудит #336)

- **Ветка**: `599-pkg-s2-14` от `origin/master` `58f338fe`.
- **Worktree**: `/home/nsa/Karaoke/.worktrees/waveD-s2-14` (главная копия и
  соседние worktree не изменялись; временные каталоги удалены).
- **Объём**: 13 записей S2 — BR-295, BR-302, BR-303, BR-312, BR-313, BR-319,
  BR-327, BR-328, BR-329, BR-330, BR-339, BR-340, BR-350.
- **Коммиты**:
  - `e97a640c` — BR-295/312/313/328 (share-отзыв, XSS метки, листенеры, END);
  - `0de4cabd` — BR-302/303/327/329/330 (плеер, автосейв, WaveSurfer, флаги);
  - `705955c5` — BR-339/319/340/350 (таймаут XHR, чат, капча, статус оплаты);
  - `585f7ed9` — этот отчёт + `knowledge/` + per-feature doc;
  - поверх — служебные `style`/`docs` коммиты (prettier-форматирование и правки
    этого отчёта; точные хэши — в `git log 599-pkg-s2-14`).
- **Пометка `SKIP_UNTIL_BR312`**: в `specs/336-code-audit/backlog.md` её нет;
  реальный маркер — константа `SKIP_UNTIL_BR312` в двух тестах BR-523
  (`karaoke-public/src/composables/__tests__/formatText-escape.test.js`,
  `webvue3/src/components/SongEditor/__tests__/useReviewModalFormat.test.js`) и
  guard `tools/check-marker-escape-guard.sh`, который требует: код экранирует →
  тест включён. Поэтому BR-312 сделан первым, а его тесты включены
  (`SKIP_UNTIL_BR312 → false`) в том же коммите. Других зависимостей от BR-312
  в пакете нет.

## 1. Что было / что стало

| BR | Было | Стало | Регрессионный тест |
|---|---|---|---|
| BR-295 | `revoke()` безусловно обнулял `currentLink`/`shareUrl` и вызывал `clearSavedUrl()` по любому `onload`: при 401 (истёкший JWT) и 5xx ссылка на сервере оставалась `active=true`, а единственная копия URL стиралась | `utils/shareRevoke.js:isRevokeConfirmed(status, body)` — локальные изменения только при `200 + {revoked:true}`; иначе ошибка и сохранённый URL остаются | `shareRevokeGuard.test.js` (4) |
| BR-302 | Проверки после `await` читали общее поле `this._activeAbortController`; старая `init()` видела чужой (не отменённый) сигнал и перезаписывала state нового трека | Локальный `controller` в `init()`, `controller.signal` в `fetch` и `_loadAudio(signal)`, после каждого `await` — `isStaleLoad(active, mine)` | `loadGuard.test.js` (9) |
| BR-303 | Терминальный `release` share-сессии на любом `visibilitychange → hidden` (переключение вкладки, свёрнутое окно, блокировка экрана) — сессия рушилась у живой ссылки | Листенер убран; `resolveShareReleaseReason()` — `'closed'` только для `pagehide`/`beforeunload`; слот освобождает TTL lease | `loadGuard.test.js` (BR-303-кейсы) |
| BR-312 | Метка `COMMENT|` из `~comment:...~` интерполировалась в HTML без экранирования и уходила в `v-html` (ReviewModal админки, публичное превью) — stored XSS | Экранирование (`& < > " '`) в обеих копиях `formatText` + в `webvue3/composables/useKaraokeEditor.js` + в инлайн-шаблоне `COMMENT` в `SubsEdit.vue`; оба теста BR-523 включены | `formatText-escape.test.js`, `useReviewModalFormat.test.js` + `tools/check-marker-escape-guard.sh` |
| BR-313 | Composable навешивал `pagehide` + два `visibilitychange` на каждый старт стрима; `cleanup()` снимал только AbortController, `removeEventListener` не было — утечка листенеров и замыкания с 388–2500 песнями | `utils/listenerBag.js`; `cleanup()` снимает подписки и `showTimeout`; store освобождает предыдущий экземпляр и чистит после завершения | `listenerBag.test.js` (4) |
| BR-319 | Доступ к чату решался в `mounted()` по кэшу `km_auth_user`; при устаревшем кэше `loading` висел вечно, при обратном — 403 превращался в пустой тред | `await fetchMe()` до решения, `utils/chatAccess.js:chatAccessState`, `watch(isPremium)` догружает/останавливает, 403 — отдельное состояние | `chatAccess.test.js` (8) |
| BR-327 | Автосейв (3 с) отменялся в `beforeUnmount`; `beforeunload`/`beforeRouteLeave` отсутствовали — правка в окне 0–3 с терялась | `beforeRouteLeave` при взведённом таймере делает `await saveNow()` (`utils/autosave.js`), `beforeUnmount` — отправляет без ожидания | `autosaveFlush.test.js` (4) |
| BR-328 | `ensureEndMarker(markers, 0)` создавал END в 0 или переносил существующий END в начало трека; `submit` проверял только `hasAnyMarkers` | `ensureEndMarker` не трогает маркеры при `duration <= 0`; `submit()` блокируется и объясняет причину | `ensureEndMarker.test.js` (5) |
| BR-329 | `initWaveSurfer()` не уничтожал предыдущий WaveSurfer; повторный `load()` из `recall()` создавал второй экземпляр | `utils/wavesurferTeardown.js`; `destroyWaveform` перед созданием и в `beforeUnmount`; `begin/endWaveformLoad`; `load()` = guard + `_loadTask()` | `wavesurferTeardown.test.js` (4) |
| BR-330 | `saveNow`/`submit`/`recall` обрабатывали только статус; при reject `saveState`/`submitting`/`recalling` залипали, исключение уходило в unhandled rejection | `try/catch/finally`, тексты ошибок по `utils/xhrOutcome.js`; отправка не уходит при несохранённом черновике | `xhrOutcome.test.js` (7) |
| BR-339 | `xhr.timeout` не задавался; reject из `PlaylistsView.reload()`/`PlaylistEditView.load()` уходил в `mounted`, `loading` оставался `true` навсегда | Таймаут 15 с (120 с для `authUpload`) + `reject('timeout')`; `finally` снимает `loading`; ошибка с кнопкой «Повторить» на обоих экранах | `authApiTimeout.test.js` (6) |
| BR-340 | Наличие `<script>` с URL капчи считалось готовностью: `onReady()` звался без `window.smartCaptcha`, виджет не рендерился, гейт требовал невыполнимое | `utils/captchaReady.js`: `load → wait (таймаут 10 с) → ready / unavailable`, упавший тег удаляется; клиентский гейт только при отрисованном виджете | `captchaReady.test.js` (9) |
| BR-350 | `poll()` не различал ошибку проверки и «подписки нет»: после 6 попыток «Подписка не найдена» при 401/500/сети, хотя оплата могла пройти | `utils/subscriptionPoll.js`: `checking / processing / terminal / not-found / check-error / unauthorized`; «не найдена» только после успешного 200; кнопка повтора и предупреждение не оплачивать повторно | `subscriptionPoll.test.js` (14) |

## 2. «До/после» — показательные прогоны

### 2.1 BR-328 — новый тест на коде базового коммита (настоящий «до»)

```
$ mkdir -p .tmp/karaoke-public/src/composables/__tests__
$ git show 58f338fe:karaoke-public/src/composables/useKaraokeEditor.js \
    > .tmp/karaoke-public/src/composables/useKaraokeEditor.js
$ cp karaoke-public/src/composables/__tests__/ensureEndMarker.test.js .tmp/.../__tests__/
$ cd .tmp/karaoke-public && node --test src/composables/__tests__/ensureEndMarker.test.js
ℹ pass 2
ℹ fail 3
  AssertionError: END в 0 сдвинул бы терминальный маркер в начало трека:
    [{"uid":"m1","time":0,"label":"END", ...},{"uid":"s1","time":1,"label":"раз", ...}]
  AssertionError: END остаётся там, где его поставил редактор
  AssertionError: duration=NaN не должен создавать END
```

После правки (в рабочем worktree):

```
$ cd karaoke-public && node --test src/composables/__tests__/ensureEndMarker.test.js
ℹ pass 5
ℹ fail 0
```

### 2.2 BR-339 — новый тест на `authApi.js` базового коммита (настоящий «до»)

```
$ git show 58f338fe:karaoke-public/src/services/authApi.js > .tmp/.../services/authApi.js
$ cd .tmp/karaoke-public && node --test src/services/__tests__/authApiTimeout.test.js
ℹ pass 1
ℹ fail 5
  AssertionError: Expected values to be strictly equal:
    + actual: 'undefined'   - expected: 'number'        (xhr.timeout не задан)
  AssertionError: The expression evaluated to a falsy value (таймаут не выставлен)
```

После правки:

```
$ cd karaoke-public && node --test src/utils/__tests__/authApiTimeout.test.js
ℹ pass 6
ℹ fail 0
```

### 2.3 BR-312 — fixtures guard'а (уязвимый и исправленный снимки)

```
$ bash tools/check-marker-escape-guard.sh
OK: тест karaoke-public/.../formatText-escape.test.js ловит уязвимый код (exit 1)
OK: тест webvue3/.../useReviewModalFormat.test.js ловит уязвимый код (exit 1)
OK: тест karaoke-public/.../formatText-escape.test.js проходит на исправленном коде (exit 0)
OK: тест webvue3/.../useReviewModalFormat.test.js проходит на исправленном коде (exit 0)
OK: karaoke-public/src/composables/useKaraokeEditor.js экранирует метку, тест включён
OK: webvue3/src/components/SongEditor/useReviewModalFormat.js экранирует метку, тест включён
PASS: тест экранирования взведён и соответствует состоянию кода.
```

### 2.4 Остальные записи — мутационная проверка «до»

Для записей, где фикс вынесен в новый чистый модуль
(`shareRevoke`, `listenerBag`, `loadGuard`, `xhrOutcome`, `autosave`,
`wavesurferTeardown`, `chatAccess`, `captchaReady`, `subscriptionPoll`),
«до» воспроизведено возвратом ровно того поведения, которое описано в карточке
(например, `isRevokeConfirmed → return true`, `isStaleLoad` без сверки
идентичности, `captchaScriptState` с «наличие script = ready»,
`subscriptionPollView` без различения `check-error`). Прогон в изолированном
каталоге:

```
BR-295 share revoke:      old-code exit=1  pass 1  fail 3
BR-313 listener bag:      old-code exit=1  pass 0  fail 4
BR-302/303 load guard:    old-code exit=1  pass 5  fail 4
BR-330/339/350 outcome:   old-code exit=1  pass 4  fail 3
BR-327 autosave flush:    old-code exit=1  pass 3  fail 1
BR-329 waveform teardown: old-code exit=1  pass 2  fail 2
BR-319 chat access:       old-code exit=1  pass 6  fail 2
BR-340 captcha ready:     old-code exit=1  pass 5  fail 4
BR-350 subscription poll: old-code exit=1  pass 7  fail 7
```

После правки (рабочий worktree):

```
$ cd karaoke-public && npm run test:unit     # node --test "src/**/__tests__/*.test.js"
ℹ tests 106  ℹ pass 106  ℹ fail 0
$ cd webvue3 && npm run test:unit
ℹ tests 131  ℹ pass 131  ℹ fail 0
```

## 3. Проверки

- `:karaoke-app:test` — 565 тестов, 4 падения в `VkPreviewWarmupClientTest`
  только из-за X11 (`java.awt.AWTError: Can't connect to X11 window server
  using ':0'`): в окружении выставлен `DISPLAY=:0`, а `-Djava.awt.headless=true`
  его не перекрывает. С `env -u DISPLAY` (как в CI, где дисплея нет) прогон
  зелёный — см. §4. Правок Kotlin в пакете нет.
- `:karaoke-web:test`, `ktlintCheck` — см. §4.
- `bash tools/check-knowledge-structure.sh` — `OK: knowledge/ structure valid (9/9)`.
- `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` —
  `All documentation passes architectural style and structural checks.`
- `python3 tools/check-doc-references.py` — 0 проблем (см. §4).
- `bash tools/check-recordhash-consistency.py` — см. §4.
- `bash tools/check-no-mp4-mentions.sh` — см. §4.
- `bash tools/check-feature-doc.sh docs/features/*.md` — `OK` для нового документа.
- `bash tools/check-subagent-isolation.sh` — см. §4.
- `bash tools/check-marker-escape-guard.sh` — `PASS` (см. §2.3).
- `npx prettier --check "src/**/*.{vue,js}"` (karaoke-public и webvue3, бинарь
  из главного checkout'а, read-only) — `All matched files use Prettier code style!`
- `eslint` (karaoke-public/webvue3, конфиги worktree) — 0 ошибок и 0
  предупреждений на изменённых файлах.

## 4. Вывод команд проверок

```
$ env -u DISPLAY GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
    :karaoke-app:test
BUILD SUCCESSFUL in 33s        # 565 тестов, 0 падений

$ ... :karaoke-web:test
BUILD SUCCESSFUL in 44s

$ ... ktlintCheck
> Task :karaoke-app:ktlintCheck        BUILD SUCCESSFUL in 36s

$ python3 tools/check-recordhash-consistency.py
OK recordhash-контракт: NEW-префикс в триггерах, канонические файлы == модели
(+бэкфилл), tbl_songs_sync содержит все колонки INSERT.

$ bash tools/check-no-mp4-mentions.sh
OK: all 139 MP4 mentions are in baseline (grandfathered)

$ bash tools/check-subagent-isolation.sh
Все commits в ветке 599-pkg-s2-14 уникальны (нет race condition)

$ python3 tools/check-doc-references.py
Проверено: ссылок 4263, путей 2462, эндпоинтов 324; whitelist: 51 записей.
(0 проблем)
```

Замечание про `DISPLAY`: без `env -u DISPLAY` прогон `:karaoke-app:test` падает
4 тестами `VkPreviewWarmupClientTest` с
`java.awt.AWTError: Can't connect to X11 window server using ':0'`.
Это состояние окружения машины (`DISPLAY=:0` выставлен, а headless-флаг JVM его
не перекрывает), а не регрессия: тот же тест зелёный при снятом `DISPLAY`, то
есть ровно как в CI. Правок Kotlin в пакете нет.

## 5. За владельцем

1. **BR-327, закрытие вкладки.** `navigator.sendBeacon` не умеет заголовок
   `Authorization`, а save-эндпоинт требует Bearer. Надёжный flush на
   `beforeunload`/закрытии вкладки требует либо токена в теле запроса (правка
   бэкенда `PublicSongEditorController.save`), либо `fetch(..., {keepalive:true})`
   с заголовком — второй вариант поддержан не всеми браузерами одинаково.
   Сейчас: `beforeRouteLeave` ждёт сохранение, `beforeUnmount` отправляет XHR
   «в надежде успеть». Решение — за владельцем.
2. **BR-312, серверная валидация меток.** Экранирование закрывает sink (это
   корректный минимум). Санитизация меток `COMMENT|` при сохранении
   (`markersToSave`/сервер) не делалась: она изменила бы хранимые данные
   существующих черновиков. Решение — за владельцем.
3. **BR-340, политика недоступной капчи.** Сейчас клиентский гейт выключен, а
   решение принимает серверная проверка (fail-closed на бэкенде:
   `captcha_not_configured`/`captcha`). Альтернатива — жёстко блокировать
   отправку на клиенте; это UX-решение владельца.
4. **BR-303, политика «скрытая вкладка».** Сейчас сворачивание вкладки не
   освобождает слот share-сессии (только TTL lease). Если владельцу нужен
   быстрый возврат слота при сворачивании — нужен неразрушающий сигнал
   «hidden» на бэкенде, а не `release`.
5. **BR-302, `playSong()`.** Поколение загрузки в `playSong()` не менялось
   (правка ограничена `init()`/`_loadAudio`): отмена предыдущей загрузки
   по-прежнему через `_abortActive()`, но теперь старая `init()` корректно
   распознаёт себя устаревшей. Переписывание `playSong()` — отдельная задача.

## 6. Границы пакета

- `specs/336-code-audit/registry.csv` не изменялся.
- Деплой, контейнеры, прод, БД не трогались; push/PR не выполнялись.
- Главная копия `/home/nsa/Karaoke` и соседние worktree не изменялись:
  единственное обращение — read-only запуск `prettier`/`eslint` из их
  `node_modules` (своего `node_modules` в worktree нет).
