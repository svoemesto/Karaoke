<template>
  <div class="news-templates-editor">
    <div v-if="loading" class="text-center my-3">
      <div class="spinner-border text-primary" role="status" />
      <p>Загрузка шаблонов новостей…</p>
    </div>

    <div v-if="error" class="alert alert-danger">{{ error }}</div>

    <div v-if="!loading && !error">
      <!-- target=local|remote -->
      <div class="mb-3 d-flex align-items-center gap-3">
        <label class="form-label small fw-bold mb-0">Целевая БД:</label>
        <select v-model="target" class="form-select form-select-sm" style="max-width: 200px">
          <option value="local">local (admin)</option>
          <option value="remote">remote (prod)</option>
        </select>
        <small class="text-muted">
          Шаблоны хранятся в <code>tbl_public_settings</code> и читаются напрямую из выбранной БД.
          Для применения на проде выбирайте <strong>remote</strong>.
        </small>
      </div>

      <!-- Компактная подсказка плейсхолдеров -->
      <div class="mb-3">
        <a
          class="ph-toggle small text-decoration-none"
          href="#"
          @click.prevent="placeholdersExpanded = !placeholdersExpanded"
        >
          {{ placeholdersExpanded ? '▾' : '▸' }} Доступные плейсхолдеры ({{ placeholders.length }})
        </a>
        <div v-show="placeholdersExpanded" class="ph-list small mt-1">
          <span v-for="ph in placeholders" :key="ph.name" class="ph-chip" :title="ph.description">
            <code>{{ '{' + ph.name + '}' }}</code>
            <span class="ph-desc">{{ ph.description.split(' — ')[1] || ph.description }}</span>
          </span>
        </div>
      </div>

      <!-- Вкладки типов (air / premium) -->
      <ul class="nav nav-pills mb-3">
        <li class="nav-item">
          <a
            class="nav-link"
            :class="{ active: currentType === 'air' }"
            href="#"
            @click.prevent="currentType = 'air'"
            >В эфире (air)</a
          >
        </li>
        <li class="nav-item">
          <a
            class="nav-link"
            :class="{ active: currentType === 'premium' }"
            href="#"
            @click.prevent="currentType = 'premium'"
            >В коллекции (premium)</a
          >
        </li>
      </ul>

      <div class="card">
        <div class="card-header d-flex justify-content-between align-items-center">
          <strong>{{ currentType === 'air' ? 'В эфире (air)' : 'В коллекции (premium)' }}</strong>
          <div>
            <button class="btn btn-sm btn-outline-secondary me-2" @click="resetToDefault('title')">
              Сбросить title к дефолту
            </button>
            <button class="btn btn-sm btn-outline-secondary" @click="resetToDefault('body')">
              Сбросить body к дефолту
            </button>
          </div>
        </div>
        <div class="card-body">
          <!-- TITLE -->
          <div class="mb-3">
            <label class="form-label small fw-bold">
              Заголовок (title)
              <span class="text-muted fw-normal">
                · макс. {{ titleMaxLength }} символов
                <span v-if="titleTruncated" class="text-warning ms-1">(усечено)</span>
              </span>
            </label>
            <div class="editor-wrap">
              <textarea
                v-model="titleValue"
                class="form-control template-textarea"
                rows="3"
                :placeholder="'Шаблон заголовка (title) для типа ' + currentType"
                @input="onInput('title')"
              />
              <div
                class="template-highlight"
                aria-hidden="true"
                v-html="highlightPlaceholders(titleValue)"
              />
            </div>
            <div v-if="getBraceWarning(titleValue)" class="text-warning small mt-1">
              ⚠ Несбалансированные скобки в плейсхолдерах — неизвестные останутся как literal.
            </div>
          </div>

          <!-- BODY -->
          <div class="mb-3">
            <label class="form-label small fw-bold">
              Тело (body)
              <span class="text-muted fw-normal">· TEXT без лимита</span>
            </label>
            <div class="editor-wrap">
              <textarea
                v-model="bodyValue"
                class="form-control template-textarea"
                rows="6"
                :placeholder="'Шаблон тела (body) для типа ' + currentType"
                @input="onInput('body')"
              />
              <div
                class="template-highlight"
                aria-hidden="true"
                v-html="highlightPlaceholders(bodyValue)"
              />
            </div>
            <div v-if="getBraceWarning(bodyValue)" class="text-warning small mt-1">
              ⚠ Несбалансированные скобки в плейсхолдерах — неизвестные останутся как literal.
            </div>
          </div>

          <!-- PREVIEW -->
          <div class="mt-3">
            <label class="form-label small fw-bold">Превью на тестовой песне (id):</label>
            <div class="input-group input-group-sm mb-2" style="max-width: 400px">
              <input
                v-model.number="previewSongId"
                type="number"
                class="form-control"
                placeholder="ID песни"
              />
              <button
                class="btn btn-outline-primary"
                :disabled="!previewSongId || previewLoading"
                @click="previewTemplate"
              >
                {{ previewLoading ? '…' : 'Превью' }}
              </button>
            </div>
            <div v-if="previewError" class="alert alert-danger py-1 small">
              {{ previewError }}
            </div>
            <div v-if="previewTitle !== null || previewBody !== null" class="preview-box">
              <div class="mb-2">
                <div class="d-flex justify-content-between align-items-center mb-1">
                  <span class="small fw-bold">Title (отрендеренный):</span>
                  <span class="small text-muted">
                    {{ previewTitleLength }} / {{ titleMaxLength }}
                    <span v-if="previewTitleTruncated" class="text-warning ms-1">(усечено)</span>
                  </span>
                </div>
                <pre class="preview-text">{{ previewTitle }}</pre>
              </div>
              <div>
                <div class="d-flex justify-content-between align-items-center mb-1">
                  <span class="small fw-bold">Body (отрендеренный):</span>
                  <span class="small text-muted">{{ previewBodyLength }} символов</span>
                </div>
                <pre class="preview-text">{{ previewBody }}</pre>
              </div>
            </div>
          </div>

          <!-- SAVE -->
          <div class="mt-3 d-flex align-items-center gap-2">
            <button class="btn btn-primary" :disabled="saving" @click="saveTemplate">
              {{ saving ? 'Сохранение…' : 'Сохранить оба поля' }}
            </button>
            <span v-if="savedMessage" class="text-success small">{{ savedMessage }}</span>
            <span v-if="saveError" class="text-danger small">{{ saveError }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { promisedXMLHttpRequest } from '../../lib/utils'

/**
 * Редактор шаблонов автоматических новостей сайта (title + body).
 *
 * Третья вкладка в PublishTemplatesView.vue (наравне с VK/Telegram), с парой полей на тип
 * (title + body отдельно — отражает структуру tbl_news). Превью возвращает пару title+body,
 * сброс к дефолту — per-field (FR-003, FR-013).
 *
 * @see docs/features/news-templates.md
 */
export default {
  name: 'NewsTemplatesEditor',
  data() {
    return {
      loading: true,
      error: '',
      target: 'remote',
      currentType: 'air',
      templates: [],
      placeholders: [],
      defaults: {},
      titleValue: '',
      bodyValue: '',
      titleMaxLength: 500,
      titleTruncated: false,
      placeholdersExpanded: false,
      previewSongId: null,
      previewLoading: false,
      previewError: '',
      previewTitle: null,
      previewBody: null,
      previewTitleLength: 0,
      previewTitleTruncated: false,
      previewBodyLength: 0,
      saving: false,
      savedMessage: '',
      saveError: '',
    }
  },
  computed: {
    titleKey() {
      return `newsTemplate${this.currentType === 'air' ? 'Air' : 'Premium'}Title`
    },
    bodyKey() {
      return `newsTemplate${this.currentType === 'air' ? 'Air' : 'Premium'}Body`
    },
    titleTpl() {
      return this.templates.find((t) => t.key === this.titleKey)
    },
    bodyTpl() {
      return this.templates.find((t) => t.key === this.bodyKey)
    },
  },
  watch: {
    currentType() {
      this.syncFieldsFromTemplates()
      this.previewTitle = null
      this.previewBody = null
      this.previewError = ''
    },
    target() {
      this.reload()
    },
  },
  mounted() {
    this.reload()
  },
  methods: {
    reload() {
      this.loading = true
      this.error = ''
      Promise.all([
        promisedXMLHttpRequest({
          method: 'GET',
          url: `/api/news/templates?target=${this.target}`,
        }),
        promisedXMLHttpRequest({
          method: 'GET',
          url: '/api/news/templates/defaults',
        }),
      ])
        .then(([listResp, defResp]) => {
          const listData = typeof listResp === 'string' ? JSON.parse(listResp) : listResp
          const defData = typeof defResp === 'string' ? JSON.parse(defResp) : defResp
          this.templates = listData.templates || []
          this.placeholders = listData.placeholders || []
          this.defaults = defData.defaults || {}
          this.loading = false
          this.syncFieldsFromTemplates()
        })
        .catch((err) => {
          this.loading = false
          this.error = err && err.message ? err.message : 'Ошибка загрузки шаблонов новостей.'
        })
    },
    syncFieldsFromTemplates() {
      const tTpl = this.titleTpl
      const bTpl = this.bodyTpl
      // Если value пустое — показываем default (placeholder стиль)
      this.titleValue = tTpl
        ? tTpl.value && tTpl.value.length > 0
          ? tTpl.value
          : tTpl.default
        : ''
      this.bodyValue = bTpl ? (bTpl.value && bTpl.value.length > 0 ? bTpl.value : bTpl.default) : ''
      this.titleMaxLength = 500
      this.titleTruncated = false
    },
    saveTemplate() {
      this.saving = true
      this.savedMessage = ''
      this.saveError = ''
      // Сохраняем title и body последовательно. Если оба уже соответствуют БД — всё равно UPSERT
      // (идемпотентен). Если error — показываем, не теряем уже сохранённое первое поле.
      this.saveOne(this.titleKey, this.titleValue)
        .then(() => this.saveOne(this.bodyKey, this.bodyValue))
        .then(() => {
          this.saving = false
          this.savedMessage = 'Сохранено.'
          setTimeout(() => {
            this.savedMessage = ''
          }, 3000)
          // После сохранения перечитываем, чтобы UI отражал БД
          this.reload()
        })
        .catch((errMsg) => {
          this.saving = false
          this.saveError = errMsg || 'Ошибка сохранения.'
        })
    },
    saveOne(key, value) {
      return new Promise((resolve, reject) => {
        promisedXMLHttpRequest({
          method: 'POST',
          url: '/api/news/templates',
          params: { key, value, target: this.target },
        })
          .then((resp) => {
            const data = typeof resp === 'string' ? JSON.parse(resp) : resp
            if (data.success) {
              resolve(data)
            } else {
              reject(data.error || 'Ошибка валидации.')
            }
          })
          .catch((err) => {
            reject(err && err.message ? err.message : 'Ошибка HTTP.')
          })
      })
    },
    resetToDefault(field) {
      if (field === 'title') {
        this.titleValue = this.defaults[this.titleKey] || ''
      } else if (field === 'body') {
        this.bodyValue = this.defaults[this.bodyKey] || ''
      }
      this.onInput(field)
    },
    previewTemplate() {
      if (!this.previewSongId) return
      this.previewLoading = true
      this.previewError = ''
      this.previewTitle = null
      this.previewBody = null
      promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/news/templates/preview',
        params: {
          titleTemplate: this.titleValue,
          bodyTemplate: this.bodyValue,
          id: this.previewSongId,
          target: this.target,
        },
      })
        .then((resp) => {
          this.previewLoading = false
          const data = typeof resp === 'string' ? JSON.parse(resp) : resp
          if (data.success) {
            this.previewTitle = data.title || ''
            this.previewBody = data.body || ''
            this.previewTitleLength = data.titleLength || 0
            this.previewTitleTruncated = !!data.titleTruncated
            this.previewBodyLength = data.bodyLength || 0
          } else {
            this.previewError = data.error || 'Ошибка preview.'
          }
        })
        .catch((err) => {
          this.previewLoading = false
          this.previewError = err && err.message ? err.message : 'Ошибка preview.'
        })
    },
    onInput(field) {
      // Сбрасываем preview при правке поля
      this.previewTitle = null
      this.previewBody = null
      this.previewError = ''
      // Если title длиннее лимита — помечаем как усечённый
      if (field === 'title') {
        this.titleTruncated = this.titleValue.length > this.titleMaxLength
      }
    },
    highlightPlaceholders(value) {
      const escaped = (value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
      const replaced = escaped.replace(/\{(\w+)\}/g, '<span class="vk-ph">$&</span>')
      return replaced + '\n'
    },
    getBraceWarning(value) {
      const opens = (value.match(/\{/g) || []).length
      const closes = (value.match(/\}/g) || []).length
      return opens !== closes
    },
  },
}
</script>

<style scoped>
.ph-toggle {
  color: #6c757d;
}
.ph-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  padding: 8px 10px;
  background: #f8f9fa;
  border-radius: 4px;
  border: 1px solid #e9ecef;
}
.ph-chip {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  white-space: nowrap;
}
.ph-chip code {
  background-color: #fff3cd;
  border-radius: 3px;
  padding: 0 4px;
  font-weight: 600;
  color: #664d03;
}
.ph-desc {
  color: #6c757d;
  font-size: 11px;
}
.template-textarea {
  font-family: monospace;
  font-size: 13px;
  background: transparent;
  color: transparent;
  caret-color: #212529;
  position: relative;
  z-index: 2;
  text-align: left;
}
.template-highlight {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 0.375rem 0.75rem;
  font-family: monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-wrap: break-word;
  overflow: auto;
  pointer-events: none;
  z-index: 1;
  color: #212529;
  text-align: left;
}
.editor-wrap {
  position: relative;
  display: inline-block;
  width: 100%;
}
.template-highlight :deep(.vk-ph) {
  background-color: #fff3cd;
  border-radius: 3px;
  padding: 0 2px;
  font-weight: 600;
}
.preview-box {
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  padding: 8px;
}
.preview-text {
  white-space: pre-wrap;
  word-wrap: break-word;
  margin: 0;
  font-family: inherit;
  font-size: 13px;
  text-align: left;
}
</style>
