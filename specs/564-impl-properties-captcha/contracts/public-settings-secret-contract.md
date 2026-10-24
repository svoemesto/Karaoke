# Контракт: защищённый канал доступа к публичным настройкам

> **Спека**: [`../../559-close-public-properties-captcha/spec.md`](../../559-close-public-properties-captcha/spec.md)
> **Research**: [`../research.md`](../research.md) (D1–D6, D11)
> **Задачи**: OpenProject **#359** (BR-019) и **#362** (BR-518), аудит #336.

## Назначение

Единственный легитимный путь изменения значения в таблице `tbl_public_settings` из
внешнего контура — **административный контур**: общий секрет в заголовке + сетевой фильтр
по адресу админской машины на уровне nginx. Отдельного незащищённого пути не существует
(решение владельца, п. 5).

## Транспорт

| Что | Значение |
|---|---|
| Заголовок | `X-Internal-Secret` (тот же контракт, что у `POST /changerecords` и `internal/stemjobs`) |
| Значение | общий server-to-server секрет; **новый секрет не заводится** — используется уже настроенный `SYNC_INTERNAL_SECRET` |
| Свойство в приложении | `publicsettings.internal-secret` (`${SYNC_INTERNAL_SECRET:}`) |
| Сетевой рубеж | nginx `location /api/properties/setproperty` — `allow` для адресов админской машины, `deny all` |

## Проверка (fail-safe)

```
internalSecret.isNotBlank() && request.getHeader("X-Internal-Secret") == internalSecret
```

Сравнение выполняется **только** при непустом настроенном секрете. При пустом секрете
доступ закрыт **всегда**, в том числе для запроса с присутствующим, но пустым заголовком
(ловушка образца `InternalStemJobController.authorized()`, research D2).

## Коды ответа

| Ситуация | HTTP | Тело | Запись в журнале |
|---|---|---|---|
| Запись без заголовка | `403` | `false` | `settings:security:denied reason=missing_secret` |
| Запись с неверным секретом | `403` | `false` | `settings:security:denied reason=invalid_secret` |
| Секрет не настроен (пусто) | `403` | `false` | `settings:security:denied reason=secret_not_configured` |
| Запись секретного ключа | `403` | `false` | `settings:security:denied reason=secret_key_write` |
| Запись разрешена | `200` | `true` (прежний формат) | — |
| Ошибка БД при записи | `200` | `false` (прежний формат) | текст ошибки — **только** в журнал, не наружу |
| Анонимное чтение непубличного ключа | `200` | `""` | `settings:security:read-denied reason=key_not_public` |
| Чтение секретного ключа (любым способом) | `200` | `""` | `settings:security:secret-read-blocked reason=secret_key` |

Формат успешного ответа записи не меняется (`Boolean` → `true`/`false`), формат чтения —
`String` (пустая строка = «нет значения / не публичный ключ»), формат дайджеста —
`{"properties": [{key, value, description}]}`.

## Границы выдачи

| Множество | Ключи | Анонимно | По защищённому каналу |
|---|---|---|---|
| `SECRET_KEYS` | `yandexSmartCaptchaServerKey` | нет | **нет** (значение серверного ключа живёт в env) |
| `PUBLIC_KEYS` | `yandexSmartCaptchaClientKey` | да | да |
| операционные | `newsAutoPublishKillSwitch`, `newsTemplate*`, прочие | нет (пустая строка) | да |

## Что НЕ входит в контракт

- Административный сценарий `webvue3` («Настройки сайта»): `POST /api/publicsettings/digest|update`
  в **karaoke-app** (выбор базы `target=local|remote`). Этот путь не проходит через
  `/api/properties/**` и правкой не затронут (FR-005).
- Ключи `KaraokeProperties` (файловый слой, ~150 параметров) — другой слой, другой эндпоинт
  (`/api/properties/setproperty` в karaoke-app пишет в файл, не в таблицу).
- Внешний сервис проверки капчи — его маршрут и таймауты не меняются.
