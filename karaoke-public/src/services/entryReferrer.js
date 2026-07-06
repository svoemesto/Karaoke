// Внешний источник захода на сайт (страница в интернете, с которой пришли по прямой ссылке).
// Снимается ОДИН раз при загрузке SPA из document.referrer. Смысл имеет только кросс-доменный
// referer: свой домен и внутренние SPA-переходы (document.referrer не меняется без полной
// перезагрузки) — не источник. Значение отдаётся ровно один раз (consumeEntryReferrer) и
// очищается, чтобы его нёс только первый запрос просмотра после реального внешнего захода, а не
// все последующие внутренние переходы. Вынесен в отдельный модуль (как clientId.js), чтобы им мог
// пользоваться api.js без цикла импортов.

let entryReferrer = ''
try {
  const ref = document.referrer || ''
  if (ref) {
    // Оставляем только чужой origin — заход по внешней ссылке.
    const refOrigin = new URL(ref).origin
    if (refOrigin && refOrigin !== window.location.origin) {
      entryReferrer = ref
    }
  }
} catch {
  entryReferrer = ''
}

// Возвращает внешний referer один раз, затем очищает (последующие вызовы → '').
export function consumeEntryReferrer() {
  const r = entryReferrer
  entryReferrer = ''
  return r
}
