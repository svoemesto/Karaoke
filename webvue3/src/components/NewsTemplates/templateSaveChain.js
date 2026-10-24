// BR-397 (аудит #336, пакет PKG-RACE-TARGET, OpenProject #367).
//
// Сохранение шаблона новости идёт двумя последовательными POST: сначала title, затем body.
// Компонент читал `this.target` и `this.bodyValue` в момент КАЖДОГО запроса, поэтому переключение
// «Целевой БД» между ними уводило title в одну БД, body — в другую, а `reload()` по watcher'у
// успевал подменить `bodyValue` значением из новой БД. Здесь target и оба значения фиксируются
// один раз — на входе в цепочку.
//
// Вынесено из `.vue`, чтобы гонку можно было воспроизвести node-тестом
// (`__tests__/template-save-chain.test.js`): в компоненте цепочка неотделима от реактивности, и
// перемежающийся порядок там не воспроизвести.
/**
 * Сохраняет title и body строго в тот target, который был выбран в момент нажатия «Сохранить оба
 * поля», и ровно те значения, которые админ видел в этот момент.
 *
 * @param {object} params
 * @param {string} params.target — 'local' | 'remote', зафиксированный на всю цепочку.
 * @param {string} params.titleKey — ключ шаблона title.
 * @param {string} params.titleValue — снимок title на момент сохранения.
 * @param {string} params.bodyKey — ключ шаблона body.
 * @param {string} params.bodyValue — снимок body на момент сохранения.
 * @param {function(string, string, string): Promise} params.saveOne — (key, value, target).
 * @returns {Promise} резолвится после второго POST; реджект — ошибка любого из них.
 */
export function saveTemplateChain({ target, titleKey, titleValue, bodyKey, bodyValue, saveOne }) {
  const pinnedTitle = titleValue
  const pinnedBody = bodyValue
  return Promise.resolve(saveOne(titleKey, pinnedTitle, target)).then(() =>
    saveOne(bodyKey, pinnedBody, target),
  )
}

/**
 * Точка входа компонента: снимает target и оба поля с редактора ОДИН раз и передаёт их в
 * закреплённую цепочку. Именно эту функцию вызывает `NewsTemplatesEditor.saveTemplate()`,
 * поэтому тест гоняет тот же код, что и прод.
 *
 * @param {object} params
 * @param {object} params.editor — компонент/его снимок: target, titleKey, titleValue, bodyKey, bodyValue.
 * @param {function(string, string, string): Promise} params.saveOne — (key, value, target).
 * @returns {Promise}
 */
export function saveTemplateFromEditor({ editor, saveOne }) {
  return saveTemplateChain({
    target: editor.target,
    titleKey: editor.titleKey,
    titleValue: editor.titleValue,
    bodyKey: editor.bodyKey,
    bodyValue: editor.bodyValue,
    saveOne,
  })
}
