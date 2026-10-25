// BR-404 (аудит #336, пакет PKG-S3-32, OpenProject #404).
//
// Клиентская валидация значения настройки по её типу.
//
// ДЕФЕКТ. `POST /api/properties/setproperty` разбирает значение по типу свойства
// на сервере: `KaraokeProperties.setFromString` делает `stringValue.toLongOrNull()` /
// `toIntOrNull()` / `toDoubleOrNull()` и бросает `KaraokePropertyValueException`
// (HTTP 500), а `parseBooleanStrict` принимает только «true/false/1/0/yes/no/on/off».
// До правки фронт отправлял строку как есть, а отказ ловил только
// `console.error` — админ видел «Да» → диалог закрылся → считал, что значение
// применено, хотя в `Karaoke.properties` оно не записано.
//
// РЕШЕНИЕ. Значение проверяется на клиенте ДО отправки, по `item.type` (это имя
// типа значения по умолчанию: `Int`, `Long`, `Double`, `Boolean`, `String`).
// Правила разбора повторяют серверные, чтобы клиент не отвергал то, что сервер принял.
//
// Модуль отдельный: правило одно на все свойства, его можно закрыть тестом.

/**
 * Допустимые значения boolean — те же, что принимает `parseBooleanStrict`.
 *
 * @type {string[]}
 */
export const BOOLEAN_PROPERTY_VALUES = ['true', 'false', '1', '0', 'yes', 'no', 'on', 'off']

/**
 * Проверяет значение настройки по её типу.
 *
 * @param {string} type — тип свойства (`Int` | `Long` | `Double` | `Boolean` | ...).
 * @param {*} value — значение (строка из input или уже типизированное).
 * @returns {{valid: boolean, message: string}} результат проверки.
 */
export function validatePropertyValue(type, value) {
  const text = value === undefined || value === null ? '' : String(value).trim()
  switch (type) {
    case 'Int':
    case 'Long': {
      if (text === '') {
        return { valid: false, message: 'Значение должно быть целым числом' }
      }
      if (!/^-?\d+$/.test(text)) {
        return { valid: false, message: 'Значение должно быть целым числом' }
      }
      return { valid: true, message: '' }
    }
    case 'Double': {
      if (text === '') {
        return { valid: false, message: 'Значение должно быть числом' }
      }
      if (!/^-?\d+([.,]\d+)?$/.test(text)) {
        return { valid: false, message: 'Значение должно быть числом' }
      }
      return { valid: true, message: '' }
    }
    case 'Boolean': {
      if (!BOOLEAN_PROPERTY_VALUES.includes(text.toLowerCase())) {
        return {
          valid: false,
          message: 'Значение должно быть одним из: true, false, 1, 0, yes, no, on, off',
        }
      }
      return { valid: true, message: '' }
    }
    default:
      // Строковые (и неизвестные) свойства сервер принимает как есть.
      return { valid: true, message: '' }
  }
}
