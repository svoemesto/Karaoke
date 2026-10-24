// Нормализация полей маркера редактора SubsEdit (аудит #336, PKG-S2-18).
//
// BR-449: поле «Time:» связано с `currentMarker.time`; любой ввод превращал
// число в строку (`"1,5"`, `""`), и она уходила в POST /api/song/savesourcetextmarkers
// как есть. Бэкенд декодирует `sourceMarkers` строгим `Json` в `List<SourceMarker>`
// с `time: Double` и отвечает `{ok:false,error:"bad_markers"}` — то есть отклоняет
// СОХРАНЕНИЕ ЦЕЛОГО ГОЛОСА. Поэтому приводим время к числу до отправки.
//
// BR-467: у chord-метки старой формы аккорд лежит в `label`, а поля `chord` нет
// (эмпирика: песня 12 — 116 таких меток). Ветка `case 'chord'` читала
// `marker.chord.split('|')` и падала с TypeError на `undefined`, обрывая
// создание регионов. Поэтому значение берём из `chord || label`.

/**
 * Привести значение поля «Time:» к числу.
 *
 * Принимает число или строку; строку — с запятой или точкой в качестве
 * десятичного разделителя (русская раскладка). Возвращает `null`, если
 * значение не число: такой маркер нельзя отправлять на бэкенд, иначе он
 * отклонит весь запрос.
 *
 * @param {*} value — значение из input/маркера
 * @returns {Number|null} конечное число либо null
 */
export function parseMarkerTime(value) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value !== 'string') return null
  const normalized = value.trim().replace(',', '.')
  if (normalized === '') return null
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : null
}

/**
 * Значение аккорда для отрисовки региона chord-маркера.
 *
 * В существующих данных аккорд хранится в `label` (метки, созданные
 * `addMarker`/`doChordsAdd`), а в `chord` — у части меток, сохранённых
 * старыми версиями. Пустое или нестроковое значение даёт пустую строку:
 * вместо исключения регион рисуется без подписи, а остальные маркеры
 * продолжают обрабатываться.
 *
 * @param {Object} marker — маркер редактора
 * @returns {String} аккорд либо ''
 */
export function resolveChordLabel(marker) {
  if (!marker) return ''
  const raw = marker.chord || marker.label
  return typeof raw === 'string' ? raw : ''
}
