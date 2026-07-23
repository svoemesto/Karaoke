<template>
  <transition name="modal-fade">
    <div class="wd-modal-backdrop">
      <div class="wd-area">
        <div class="wd-modal-header">Whisper: результат распознавания (отладка)</div>

        <div class="wd-modal-body">
          <div class="wd-body">
            <div class="wd-column">
              <div class="wd-column-title">Сырой текст Whisper</div>
              <textarea class="wd-textarea" readonly v-text="whisperText" />
              <button class="wd-group-button" type="button" @click="copyToClipboard(whisperText)">
                Скопировать текст
              </button>
            </div>

            <div class="wd-column">
              <div class="wd-column-title">Слова Whisper (время, уверенность)</div>
              <textarea class="wd-textarea" readonly v-text="wordsText" />
              <button class="wd-group-button" type="button" @click="copyToClipboard(wordsText)">
                Скопировать слова
              </button>
            </div>

            <div class="wd-column">
              <div class="wd-column-title">Сформированные маркеры</div>
              <textarea class="wd-textarea" readonly v-text="markersText" />
              <button class="wd-group-button" type="button" @click="copyToClipboard(markersText)">
                Скопировать маркеры
              </button>
            </div>
          </div>

          <div class="wd-footer">
            <button
              class="wd-btn wd-btn-apply"
              type="button"
              :disabled="markers.length === 0"
              :title="markers.length === 0 ? 'Маркеров нет — нечего применять (текст можно скопировать выше)' : ''"
              @click="apply"
            >
              Применить маркеры к голосу
            </button>
            <button class="wd-btn wd-btn-close" type="button" @click="close">Закрыть без применения</button>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
/**
 * Отладочное окно результата авто-маркеров (Whisper): показывает "сырой" ответ распознавания
 * (текст + слова с таймкодами/уверенностью) и уже сформированные из него маркеры - ДО применения
 * к голосу. Нужно, чтобы можно было оценить качество распознавания/сопоставления прежде чем
 * заменять им ручную разметку (см. SubsEdit.vue doApplyAutoMarkers).
 *
 * @emits apply - пользователь подтвердил применение маркеров к текущему голосу
 * @emits close - закрыть без применения
 */
export default {
  name: 'WhisperDebugModal',
  props: {
    whisperText: {
      type: String,
      default: '',
    },
    whisperWords: {
      type: Array,
      default: () => [],
    },
    markers: {
      type: Array,
      default: () => [],
    },
  },
  computed: {
    wordsText() {
      return this.whisperWords
        .map((w) => `${w.start.toFixed(2)}-${w.end.toFixed(2)}s\t${w.word}\t(${w.confidence.toFixed(2)})`)
        .join('\n')
    },
    markersText() {
      return this.markers.map((m) => `${m.time.toFixed(3)}s\t${m.markertype}\t${m.label}`).join('\n')
    },
  },
  methods: {
    async copyToClipboard(value) {
      await navigator.clipboard.writeText(value)
    },
    apply() {
      this.$emit('apply')
    },
    close() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.wd-modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1060;
}

.wd-area {
  background: #ffffff;
  box-shadow: 2px 2px 20px 1px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 50px);
  max-height: calc(100vh - 50px);
}

.wd-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.wd-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
}

.wd-body {
  display: flex;
  flex-direction: row;
  gap: 10px;
}

.wd-column {
  display: flex;
  flex-direction: column;
  width: 400px;
}

.wd-column-title {
  font-weight: bold;
  margin-bottom: 5px;
}

.wd-textarea {
  width: 100%;
  height: calc(100vh - 320px);
  font-family: 'Courier New', Courier, monospace;
  font-size: small;
  overflow: auto;
  resize: none;
}

.wd-group-button {
  margin-top: 5px;
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  padding: 5px 0;
}
.wd-group-button:hover {
  background-color: lightpink;
}

.wd-footer {
  display: flex;
  flex-direction: row;
  justify-content: center;
  gap: 10px;
  margin-top: 10px;
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 10px 0;
}

.wd-btn {
  border: solid 1px black;
  border-radius: 6px;
  padding: 10px 20px;
  background-color: antiquewhite;
  font-size: 16px;
}
.wd-btn:hover {
  background-color: lightpink;
}
.wd-btn[disabled] {
  background-color: lightgray;
  cursor: not-allowed;
}

.wd-btn-apply {
  font-weight: bold;
}
</style>
