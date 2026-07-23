<template>
  <transition name="modal-fade">
    <div class="ae-modal-backdrop">
      <div class="ae-area">
        <div class="ae-modal-header">AI-редактор текста</div>

        <div class="ae-modal-body">
          <div class="ae-toolbar">
            <button
              class="ae-action-button"
              type="button"
              :disabled="isLoading"
              @click="runCorrection('spelling')"
            >
              {{ isLoading && activeMode === 'spelling' ? 'Исправляем...' : 'Исправить орфографию' }}
            </button>
            <button
              class="ae-action-button"
              type="button"
              :disabled="isLoading"
              @click="runCorrection('punctuation')"
            >
              {{ isLoading && activeMode === 'punctuation' ? 'Исправляем...' : 'Исправить пунктуацию' }}
            </button>
          </div>

          <div v-if="errorMessage" class="ae-error">{{ errorMessage }}</div>

          <div class="ae-body">
            <div class="ae-column">
              <div class="ae-column-title">Исходный текст</div>
              <textarea v-model="originalText" class="ae-textarea" />
            </div>

            <div class="ae-column">
              <div class="ae-column-title">Результат корректора</div>
              <div ref="resultBox" class="ae-textarea ae-result" contenteditable="true" @input="onResultInput" />
            </div>
          </div>

          <div class="ae-legend">
            <span class="ae-mark ae-added">текст</span>
            <span>— добавлено или исправлено LLM относительно исходного текста</span>
          </div>

          <div class="ae-footer">
            <button class="ae-btn ae-btn-apply" type="button" :disabled="!correctedText" @click="apply">
              Применить изменения
            </button>
            <button class="ae-btn ae-btn-close" type="button" @click="close">Закрыть без применения</button>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
// Токенизация текста для word-level diff: слова (кириллица/латиница/цифры), одиночный перевод
// строки, пробельные "не-\n" последовательности и всё остальное (пунктуация) — отдельными
// токенами. Такой гранулярности достаточно и для орфографии (меняются словесные токены), и для
// пунктуации (меняются токены-символы), без разных путей кода под каждый режим.
const TOKEN_RE = /[0-9A-Za-zА-Яа-яЁё]+|\n|[^\S\n]+|[^\s0-9A-Za-zА-Яа-яЁё]+/g

function tokenize(text) {
  return text.match(TOKEN_RE) || []
}

// Обычный LCS-diff по токенам. Токены исходного текста, отсутствующие в результате, просто
// пропускаются (не подсвечиваются) — правая панель показывает ИТОГОВЫЙ текст, где отличия от
// исходного помечены цветом, а не полный дифф с зачёркиваниями.
function diffTokens(a, b) {
  const n = a.length
  const m = b.length
  const dp = new Array(n + 1)
  for (let i = 0; i <= n; i++) dp[i] = new Array(m + 1).fill(0)
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }

  const result = []
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      result.push({ type: 'equal', text: b[j] })
      i++
      j++
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      i++
    } else {
      result.push({ type: 'added', text: b[j] })
      j++
    }
  }
  while (j < m) {
    result.push({ type: 'added', text: b[j] })
    j++
  }
  return result
}

function escapeHtml(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function buildDiffHtml(oldText, newText) {
  const tokens = diffTokens(tokenize(oldText), tokenize(newText))
  return tokens
    .map((t) => {
      const escaped = escapeHtml(t.text)
      return t.type === 'added' ? `<span class="ae-mark ae-added">${escaped}</span>` : escaped
    })
    .join('')
}

/**
 * AI-редактор текста песни (кнопка в SubsEdit.vue): отправляет текст на коррекцию в LLM
 * (langchain4j + Ollama, тот же стек, что уже используется в проекте для поиска текстов песен)
 * и показывает результат рядом с исходным текстом, подсвечивая цветом внесённые правки.
 * Результат можно доредактировать вручную перед применением.
 *
 * @emits apply(text: string) - перенести текст результата в текст голоса
 * @emits close - закрыть без применения
 */
export default {
  name: 'AiTextEditorModal',
  props: {
    sourceText: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      originalText: this.sourceText,
      correctedText: '',
      originalSnapshot: '',
      isLoading: false,
      activeMode: '',
      errorMessage: '',
    }
  },
  methods: {
    async runCorrection(mode) {
      this.errorMessage = ''
      this.isLoading = true
      this.activeMode = mode
      try {
        const responseText = await this.$store.dispatch('correctText', {
          mode,
          sourceText: this.originalText,
        })
        const result = JSON.parse(responseText)
        if (!result.ok) {
          const messages = {
            empty_source_text: 'Исходный текст пуст — нечего исправлять.',
            bad_mode: 'Некорректный режим коррекции.',
            lm_studio_unavailable:
              'LM Studio недоступна — проверьте, что сервер запущен на хост-машине, и настройку lmStudioUrl/lmStudioModel.',
          }
          this.errorMessage = messages[result.error] || `Ошибка коррекции: ${result.error}`
          return
        }
        this.correctedText = result.text || ''
        this.originalSnapshot = this.originalText
        await this.$nextTick()
        this.renderResult()
      } catch (error) {
        console.error('Ошибка AI-коррекции текста:', error)
        this.errorMessage = 'Ошибка AI-коррекции текста, подробности в консоли.'
      } finally {
        this.isLoading = false
      }
    },
    renderResult() {
      if (!this.$refs.resultBox) return
      this.$refs.resultBox.innerHTML = buildDiffHtml(this.originalSnapshot, this.correctedText)
    },
    onResultInput(event) {
      // Правая панель — contenteditable "неконтролируемый" компонент: innerHTML не биндится
      // реактивно (иначе курсор прыгал бы на каждый ререндер), поэтому при ручной правке текста
      // результата просто синхронизируем plain-text в correctedText для кнопки "Применить".
      this.correctedText = event.target.innerText
    },
    apply() {
      this.$emit('apply', this.correctedText)
    },
    close() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.ae-modal-backdrop {
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

.ae-area {
  background: #ffffff;
  box-shadow: 2px 2px 20px 1px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 50px);
  max-height: calc(100vh - 50px);
}

.ae-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.ae-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
}

.ae-toolbar {
  display: flex;
  flex-direction: row;
  gap: 10px;
  margin-bottom: 10px;
}

.ae-action-button {
  border: solid 1px black;
  border-radius: 6px;
  padding: 8px 16px;
  background-color: antiquewhite;
  font-size: 14px;
}
.ae-action-button:hover {
  background-color: lightpink;
}
.ae-action-button[disabled] {
  background-color: lightgray;
  cursor: not-allowed;
}

.ae-error {
  background-color: #ffe0e0;
  border: solid 1px darkred;
  border-radius: 6px;
  padding: 8px;
  margin-bottom: 10px;
  color: darkred;
}

.ae-body {
  display: flex;
  flex-direction: row;
  gap: 10px;
}

.ae-column {
  display: flex;
  flex-direction: column;
  width: 480px;
}

.ae-column-title {
  font-weight: bold;
  margin-bottom: 5px;
}

.ae-textarea {
  width: 100%;
  height: calc(100vh - 380px);
  min-height: 200px;
  font-family: 'Courier New', Courier, monospace;
  font-size: small;
  overflow: auto;
  resize: none;
  white-space: pre-wrap;
  border: solid 1px darkgray;
  box-sizing: border-box;
  padding: 5px;
}

.ae-result {
  background-color: #fafafa;
}

.ae-legend {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: small;
  color: dimgray;
}

.ae-mark {
  border-radius: 3px;
  padding: 0 2px;
}

.ae-added {
  background-color: #b6f2b6;
}

.ae-footer {
  display: flex;
  flex-direction: row;
  justify-content: center;
  gap: 10px;
  margin-top: 10px;
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 10px 0;
}

.ae-btn {
  border: solid 1px black;
  border-radius: 6px;
  padding: 10px 20px;
  background-color: antiquewhite;
  font-size: 16px;
}
.ae-btn:hover {
  background-color: lightpink;
}
.ae-btn[disabled] {
  background-color: lightgray;
  cursor: not-allowed;
}

.ae-btn-apply {
  font-weight: bold;
}
</style>
