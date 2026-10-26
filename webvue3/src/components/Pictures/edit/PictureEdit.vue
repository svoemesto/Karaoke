<template>
  <div :style="styleRoot">
    <div v-if="pictureCurrent">
      <custom-confirm
        v-if="isCustomConfirmVisible"
        :params="customConfirmParams"
        @close="closeCustomConfirm"
      />
      <FileExplorerModal
        v-if="isFileExplorerVisible"
        :start="pictureCurrent.pathToFolder"
        extensions="png"
        @close="closeFileExplorer"
        @getpath="getPathToPictureFile"
      />
      <div class="header">
        <div class="header-picture-id">ID = {{ pictureCurrent.id }}</div>
      </div>
      <div class="body">
        <div class="column-1">
          <div class="label-and-input">
            <div class="label">Имя:</div>
            <input v-model="pictureCurrent.name" class="input-field" />
            <button class="btn-round" :disabled="notChanged('name')" @click="undoField('name')">
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!pictureCurrent.name"
              @click="copyToClipboard(pictureCurrent.name, 'name')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('name')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="picture-full">
            <img
              class="image-full"
              alt="image"
              :src="
                pictureFullBase64
                  ? 'data:image/jpg;base64,' + pictureFullBase64
                  : pictureCurrent.fullUrl
              "
            />
          </div>
          <div class="column2-buttons-group">
            <button
              class="group-button"
              title="Загрузить новую картинку с диска"
              @click="loadNewPicture"
            >
              Загрузить новую картинку с диска
            </button>
            <button
              class="group-button"
              title="Сохранить картинку в базу данных"
              @click="savePictureToDB"
            >
              Сохранить картинку в базу данных
            </button>
            <button
              class="group-button"
              title="Сохранить картинку на диске"
              @click="savePictureToDisk"
            >
              Сохранить картинку на диске
            </button>
          </div>
        </div>
      </div>
      <div class="footer">
        <button
          class="btn-round-save-double"
          :disabled="notChanged()"
          title="Сохранить"
          @click="save"
        >
          <img alt="savePicture" class="icon-save-double" src="../../../assets/svg/icon_save.svg" />
        </button>
        <button class="btn-round-double" title="Удалить картинку" @click="deletePicture">
          <img alt="delete" class="icon-40" src="../../../assets/svg/icon_delete.svg" />
        </button>
      </div>
    </div>
    <div v-else-if="loadError" class="picture-load-error">
      Не удалось загрузить картинку: {{ loadError }}
    </div>
    <div v-else>Не выбрана картинка</div>
  </div>
</template>

<script>
import CustomConfirm from '../../Common/CustomConfirm.vue'
import { escapeHtml } from '../../../utils/escapeHtml.js'
import { copyToClipboardSafely } from '../../../utils/clipboard.js'
import FileExplorerModal from '../../../components/Common/FileExplorer/FileExplorerModal.vue'
import { useToast } from 'bootstrap-vue-next'
import { h } from 'vue'

/**
 * Форма редактирования picture.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
export default {
  name: 'PictureEdit',
  components: {
    CustomConfirm,
    FileExplorerModal,
  },
  data() {
    return {
      isCustomConfirmVisible: false,
      isFileExplorerVisible: false,
      customConfirmParams: undefined,
      pictureAutoSave: true,
      pictureAutoSaveDelayMs: 1000,
      pictureSaveTimer: undefined,
      pictureFullBase64: '',
      createToast: () => {},
      // BR-396: ошибка загрузки картинки. Раньше `.then` без `.catch` давал unhandled
      // rejection, а форма оставалась пустой без объяснения.
      loadError: '',
      // BR-395: контроллер отмены автосейва — при уходе со страницы запрос прерывается.
      saveAbortController: undefined,
    }
  },
  computed: {
    styleRoot() {
      return {
        padding: 0,
        margin: 0,
        width: 'auto',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: 'Avenir, Helvetica, Arial, sans-serif',
        // backgroundColor: 'lightyellow'
      }
    },
    pictureCurrent() {
      return this.$store.getters.getPictureCurrent
    },
    pictureSnapshot() {
      return this.$store.getters.getPictureSnapshot
    },
    pictureDiff() {
      return this.$store.getters.getPictureDiff
    },
  },
  watch: {
    pictureDiff: {
      async handler() {
        if (this.pictureDiff.length !== 0 && this.pictureAutoSave) {
          clearTimeout(this.pictureSaveTimer)
          this.pictureSaveTimer = setTimeout(this.save, this.pictureAutoSaveDelayMs)
        }
      },
    },
  },
  async mounted() {
    const { create } = useToast()
    this.createToast = create
    // BR-396: отказ загрузки картинки виден пользователю (раньше — unhandled rejection).
    try {
      const data = await this.$store.getters.getPictureValuePromise
      const image = JSON.parse(data)
      await this.$store.dispatch('setPictureCurrent', image)
      await this.$store.dispatch('setPictureSnapshot', image)
      this.pictureFullBase64 = ''
    } catch (error) {
      this.loadError = error && error.message ? error.message : 'ошибка запроса'
    }
    try {
      this.pictureAutoSave = await this.propAutoSave()
      this.pictureAutoSaveDelayMs = Number(await this.propAutoSaveDelayMs())
    } catch (error) {
      // Настройки автосейва не критичны: остаёмся на значениях по умолчанию.
      this.pictureAutoSave = false
    }
  },
  // BR-395: таймер автосейва и запрос в полёте отменяются при уходе со страницы.
  // Иначе отложенный `save()` выполнялся уже после закрытия — и для ДРУГОЙ картинки
  // (`pictureCurrentId` — глобальный state).
  beforeUnmount() {
    clearTimeout(this.pictureSaveTimer)
    this.pictureSaveTimer = undefined
    if (this.saveAbortController) {
      this.saveAbortController.abort()
      this.saveAbortController = undefined
    }
  },
  methods: {
    async getPathToPictureFile(path) {
      this.pictureFullBase64 = await this.$store.getters.loadPictureFromDiskBase64(path)
    },
    async propAutoSave() {
      const propValue = await this.$store.getters.getPropValue('autoSave')
      return propValue === 'true'
    },
    async propAutoSaveDelayMs() {
      return await this.$store.getters.getPropValue('autoSaveDelayMs')
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false
    },
    closeFileExplorer() {
      this.isFileExplorerVisible = false
    },
    undoField(name) {
      return this.$store.dispatch('setPictureCurrentField', {
        name: name,
        value: this.pictureSnapshot[name],
      })
    },
    async copyToClipboard(value, fieldName) {
      // Единый хелпер с запасным путём (Clipboard API может быть недоступен или
      // отклонён). Тост показываем в обоих случаях: при отказе копирования в нём
      // сам текст — пользователь скопирует его вручную.
      const copied = await copyToClipboardSafely(value)
      this.showCopyToClipboardToast(fieldName, value, !copied)
    },
    async pasteFromClipboard(name) {
      await navigator.clipboard.readText().then((data) => {
        return this.$store.dispatch('setPictureCurrentField', { name: name, value: data })
      })
    },
    showCopyToClipboardToast(fieldName, fieldValue, notCopied = false) {
      // Use a shorter name for this.$createElement
      // const h = this.$createElement

      // Функция для преобразования текста с \n в массив VNodes с <br>
      const createTextWithLineBreaks = (text) => {
        if (typeof text !== 'string') {
          return [String(text)]
        }

        const lines = text.split('\n')
        const vnodes = []

        lines.forEach((line, index) => {
          // Добавляем текст строки
          vnodes.push(line)
          // Если это не последняя строка, добавляем <br>
          if (index < lines.length - 1) {
            vnodes.push(h('br'))
          }
        })

        return vnodes
      }

      // Создаем сообщение с возможными переносами строк
      const vNodesMsg = h('div', [
        h('div', { style: { display: 'flex', flexDirection: 'row', flexWrap: 'wrap' } }, [
          h(
            'div',
            {
              style: {
                fontFamily: 'sans-serif',
                fontSize: 'small',
                textAlign: 'left',
                paddingRight: '5px',
              },
            },
            [`Значение поля `],
          ),
          h(
            'div',
            {
              style: {
                fontFamily: 'monospace',
                fontSize: 'small',
                textAlign: 'left',
                fontWeight: 'bold',
                paddingRight: '5px',
                color: 'darkred',
              },
            },
            [fieldName],
          ),
          h('div', { style: { fontFamily: 'sans-serif', fontSize: 'small', textAlign: 'left' } }, [
            notCopied
              ? ` НЕ скопировано (буфер обмена недоступен) — скопируйте вручную:`
              : ` скопировано в буфер обмена:`,
          ]),
        ]),
        h('br'),
        h(
          'div',
          { style: { fontFamily: 'monospace', fontSize: 'x-small', textAlign: 'left' } },
          createTextWithLineBreaks(fieldValue),
        ),
      ])

      this.createToast({
        slots: { default: () => [vNodesMsg] },
        title: 'COPY',
        autoHideDelay: 3000,
        bodyClass: 'toast-body-copytoclipboard',
        headerClass: 'toast-header-copytoclipboard',
        appendToast: false,
        position: 'top-start',
        // modelValue: true
      })
    },
    notChanged(name) {
      if (name) {
        return this.pictureCurrent[name] === this.pictureSnapshot[name]
      } else {
        return this.pictureDiff.length === 0
      }
    },
    // BR-396: сохранение ждёт ответа; отказ показывается пользователю (тост),
    // а не теряется в консоли.
    async save() {
      clearTimeout(this.pictureSaveTimer)
      this.pictureSaveTimer = undefined
      let diffs = {}
      for (let diff of this.pictureDiff) {
        diffs[diff.name] = diff.new
      }
      if (Object.keys(diffs).length === 0) return
      const controller = new AbortController()
      this.saveAbortController = controller
      try {
        await this.$store.dispatch('savePicture', { diffs, signal: controller.signal })
      } catch (error) {
        if (error && error.name === 'AbortError') return
        this.showErrorToast('Не удалось сохранить картинку', error)
      } finally {
        if (this.saveAbortController === controller) this.saveAbortController = undefined
      }
    },
    deletePicture() {
      this.customConfirmParams = {
        allowHtml: true,
        header: 'Подтвердите удаление картинки',
        body: `Удалить картинку <strong>«${escapeHtml(this.pictureCurrent.name)}»</strong>?`,
        timeout: 10,
        callback: this.doDeletePicture,
      }
      this.isCustomConfirmVisible = true
    },
    // BR-396: удаление ждёт ответа сервера. Модалка закрывается ТОЛЬКО после успеха;
    // при отказе пользователь видит сообщение, а картинка остаётся в таблице.
    async doDeletePicture() {
      try {
        await this.$store.dispatch('deletePictureCurrent')
        this.$emit('close')
      } catch (error) {
        this.showErrorToast('Не удалось удалить картинку', error)
      }
    },
    loadNewPicture() {
      this.isFileExplorerVisible = true
    },
    savePictureToDB() {
      this.pictureCurrent.full = this.pictureFullBase64
    },
    // BR-396: сохранение на диске — с обработкой отказа (раньше кнопка «молчала»).
    async savePictureToDisk() {
      try {
        await this.$store.dispatch('savePictureCurrentToDisk')
      } catch (error) {
        this.showErrorToast('Не удалось сохранить картинку на диске', error)
      }
    },
    // BR-396: единый показ ошибки операции с картинкой.
    showErrorToast(title, error) {
      const detail = error && error.message ? error.message : 'ошибка запроса'
      this.createToast({
        slots: { default: () => [`${title}: ${detail}`] },
        title: title,
        autoHideDelay: 5000,
        bodyClass: 'toast-body-copytoclipboard',
        headerClass: 'toast-header-copytoclipboard',
        appendToast: false,
        position: 'top-start',
      })
    },
  },
}
</script>

<style scoped>
.header {
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 5px 0;
  background-color: transparent;
}
.body {
  margin: 0;
  display: flex;
  flex-direction: row;
  height: max-content;
  background-color: transparent;
  z-index: 100;
}
.footer {
  display: flex;
  flex-direction: row;
  justify-content: center;
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 5px 0;
  background-color: transparent;
}
.header-picture-id {
  text-align: center;
  font-size: 12pt;
  margin: 0 auto;
}
.column-1 {
  width: max-content;
  height: max-content;
  /*background-color: white;*/
  margin: 5px 5px 5px 0;
  display: flex;
  flex-direction: column;
  /*align-items: flex-end;*/
}
.column-2 {
  width: max-content;
  height: max-content;
  margin: 5px 5px 5px 5px;
}
.label-and-input {
  display: flex;
}
.label {
  font-size: small;
  text-align: right;
  width: 110px;
  padding-right: 2px;
  padding-top: 2px;
}

.picture-load-error {
  padding: 10px;
  color: darkred;
  font-size: small;
}
.input-field {
  display: block;
  padding-bottom: 3px;
  width: 310px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.input-field:hover {
  background-color: lightyellow;
}
.input-field:focus {
  background-color: cyan;
}
.btn-round {
  border: solid 1px black;
  border-radius: 25%;
  width: 24px;
  height: 24px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round:hover {
  background-color: lightpink;
}
.btn-round:focus {
  background-color: darksalmon;
}
.btn-round[disabled] {
  background-color: lightgray;
}
.btn-round-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round-double:hover {
  background-color: lightpink;
}
.btn-round-double:focus {
  background-color: darksalmon;
}
.btn-round-double[disabled] {
  background-color: lightgray;
}
.btn-round-save-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin-left: 2px;
  background-color: red;
}
.btn-round-save-double:hover {
  background-color: darkred;
}
.btn-round-save-double:focus {
  background-color: greenyellow;
}
.btn-round-save-double[disabled] {
  background-color: lightgray;
}
.icon-undo {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -10px;
}
.icon-copy {
  width: 24px;
  height: 24px;
  margin-left: -6px;
  margin-top: -10px;
}
.icon-paste {
  width: 24px;
  height: 24px;
  margin-left: -6px;
  margin-top: -10px;
}
.picture-full {
  background-color: black;
  width: auto;
  height: 400px;
}
.image-full {
  width: auto;
  height: 400px;
}
.column2-buttons-group {
  font-size: small;
  display: flex;
  flex-direction: column;
}
.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: auto;
}
</style>
