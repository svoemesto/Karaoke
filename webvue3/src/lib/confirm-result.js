// BR-399 (аудит #336, пакет PKG-S2-17, OpenProject #380).
//
// Контракт результата `CustomConfirm`.
//
// ДЕФЕКТ (до правки): `ok()` собирал `ret` только из `params.fields` и НЕ выставлял
// признак подтверждения. Вызывающий код, который проверял `ret.confirmed`
// (`Processes/edit/ProcessEdit.vue:353`), получал `undefined` и молча ничего не
// сохранял: модалка закрывалась, `POST /api/admin/processes/{id}/edit` не уходил,
// а со стороны UI это было неотличимо от успешного сохранения. Форма редактирования
// процесса не работала целиком.
//
// РЕШЕНИЕ: «нажата кнопка Да» — это и есть подтверждение. `ok()` теперь возвращает
// `{ confirmed: true, ...поля формы }`, а отмена — `{ confirmed: false }`; контракт
// зафиксирован здесь и в JSDoc компонента. Это одна точка правки на все подтверждения
// (`ret.confirmed` ожидал только ProcessEdit, остальные колбэки его игнорируют).
//
// Почему не «вызывать doSave безусловно»: тогда любой будущий колбэк с полями формы
// (`params.fields`) потерял бы признак отмены, а `CustomConfirm` умеет и alert-режим
// без колбэка вовсе. Явный флаг оставляет контракт однозначным.

/**
 * Собирает результат подтверждения для колбэка `CustomConfirm`.
 *
 * @param {Object} params — параметры модалки (`fields`, `callback`, ...).
 * @param {boolean} confirmed — нажата кнопка «Да» (true) или модалка закрыта/отменена (false).
 * @returns {Object} объект с `confirmed` и значениями полей формы (если они были).
 */
export function buildConfirmResult(params, confirmed) {
  const ret = { confirmed: !!confirmed }
  const fields = params && params.fields
  if (fields) {
    for (let i = 0; i < fields.length; i++) {
      ret[fields[i].fldName] = fields[i].fldValue
    }
  }
  return ret
}
