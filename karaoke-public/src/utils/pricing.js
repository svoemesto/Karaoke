// BR-326 (аудит #336, пакет PKG-S3-27, OpenProject #399): цены — из API, а не из текста.
//
// ЧТО БЫЛО. На публичной странице «О проекте» цены были захардкожены в тексте
// («449 ₽/мес», «49 ₽»), тогда как реальные тарифы живут в БД (`tbl_price_tariffs`) и правятся
// админом; страница `/premium` берёт их из API. После изменения цены тарифа `/about`
// продолжал показывать старую цену — на одном сайте два разных числа о деньгах.
//
// ЧТО СТАЛО. Страница тянет публичные тарифы (`GET /api/public/account/subscription/tariffs`)
// и подставляет `priceRub`. Если тарифы недоступны — блок с ценами не показывается вовсе
// (вместо того чтобы показывать заведомо неверное число).
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * Выбрать тариф по умолчанию: `isDefault` либо первый в списке.
 *
 * @param {Array<{id: number, priceRub?: number, isDefault?: boolean}>} tariffs
 * @returns {object|null}
 */
export function pickDefaultTariff(tariffs) {
  if (!Array.isArray(tariffs) || !tariffs.length) return null
  const def = tariffs.find((t) => t && t.isDefault)
  return def || tariffs[0]
}

/**
 * Число с пробелами-разделителями: 449 → "449", 1200 → "1 200".
 *
 * @param {number|string} value
 * @returns {string} '' — если значение нечисловое
 */
export function formatRub(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return ''
  return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ' ')
}

/**
 * Тексты цен для блока CTA.
 *
 * @param {Array} siteTariffs — тарифы scope=SITE
 * @param {Array} songTariffs — тарифы scope=SONG
 * @returns {{site: string, song: string, hasPrices: boolean}}
 */
export function priceTexts(siteTariffs, songTariffs) {
  const site = pickDefaultTariff(siteTariffs)
  const song = pickDefaultTariff(songTariffs)
  const siteText = site ? `${formatRub(site.priceRub)} ₽/мес` : ''
  const songText = song ? `${formatRub(song.priceRub)} ₽` : ''
  return {
    site: siteText,
    song: songText,
    hasPrices: !!(siteText || songText),
  }
}
