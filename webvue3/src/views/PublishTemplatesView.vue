<template>
  <div class="container-fluid publish-templates-view">
    <h2 class="mb-2">Шаблоны публикаций</h2>
    <p class="text-muted small mb-3">
      Шаблоны caption/постов для автопубликации в группе ВКонтакте и в Telegram-канале
      (specs/121-vk-news-auto-publish, specs/113-telegram-demo-publish), а также шаблоны
      автоматических новостей сайта (title+body) — specs/128-news-publish-templates. Шаблоны
      содержат плейсхолдеры в фигурных скобках. Неизвестные остаются как literal-текст. Изменения
      применяются без перезапуска.
    </p>

    <div v-if="loading" class="text-center my-4">
      <div class="spinner-border text-primary" role="status" />
      <p>Загрузка шаблонов…</p>
    </div>

    <div v-if="error" class="alert alert-danger">{{ error }}</div>

    <div v-if="!loading && !error">
      <!-- Платформа: ВК / Telegram / Новости сайта -->
      <ul class="nav nav-tabs mb-3">
        <li class="nav-item">
          <a
            class="nav-link"
            :class="{ active: platform === 'vk' }"
            href="#"
            @click.prevent="platform = 'vk'"
            >ВКонтакте</a
          >
        </li>
        <li class="nav-item">
          <a
            class="nav-link"
            :class="{ active: platform === 'telegram' }"
            href="#"
            @click.prevent="platform = 'telegram'"
            >Telegram</a
          >
        </li>
        <li class="nav-item">
          <a
            class="nav-link"
            :class="{ active: platform === 'news' }"
            href="#"
            @click.prevent="platform = 'news'"
            >Новости сайта</a
          >
        </li>
      </ul>

      <!-- Компактная подсказка плейсхолдеров -->
      <div class="mb-3">
        <a
          class="ph-toggle small text-decoration-none"
          href="#"
          @click.prevent="placeholdersExpanded = !placeholdersExpanded"
        >
          {{ placeholdersExpanded ? '▾' : '▸' }} Доступные плейсхолдеры ({{
            currentPlaceholders.length
          }})
        </a>
        <div v-show="placeholdersExpanded" class="ph-list small mt-1">
          <span
            v-for="ph in currentPlaceholders"
            :key="ph.name"
            class="ph-chip"
            :title="ph.description"
          >
            <code>{{ '{' + ph.name + '}' }}</code>
            <span class="ph-desc">{{ ph.description.split(' — ')[1] || ph.description }}</span>
          </span>
        </div>
      </div>

      <!-- Вкладки типов (air / premium) — только для VK/Telegram, у News свой UI -->
      <ul v-if="platform !== 'news'" class="nav nav-pills mb-3">
        <li v-for="tpl in currentTemplates" :key="tpl.type" class="nav-item">
          <a
            class="nav-link"
            :class="{ active: activeTab === tpl.type }"
            href="#"
            @click.prevent="activeTab = tpl.type"
            >{{ tpl.type === 'air' ? 'В эфире (air)' : 'Премиум (premium)' }}</a
          >
        </li>
      </ul>

      <!-- Новости сайта — отдельный компонент (title+body пара вместо одного caption) -->
      <NewsTemplatesEditor v-if="platform === 'news'" />

      <template v-if="platform !== 'news'">
        <div
          v-for="tpl in currentTemplates"
          v-show="activeTab === tpl.type"
          :key="tpl.type"
          class="card"
        >
          <div class="card-header d-flex justify-content-between align-items-center">
            <div>
              <strong>{{ tpl.type === 'air' ? 'В эфире (air)' : 'Премиум (premium)' }}</strong>
              <span class="text-muted small ms-2">{{ tpl.description }}</span>
            </div>
            <button class="btn btn-sm btn-outline-secondary" @click="resetToDefault(tpl)">
              Сбросить к дефолту
            </button>
          </div>
          <div class="card-body">
            <!-- Редактор с подсветкой плейсхолдеров -->
            <div class="editor-wrap">
              <textarea
                v-model="tpl.value"
                class="form-control template-textarea"
                rows="6"
                :placeholder="'Шаблон типа ' + tpl.type"
                @scroll="syncScroll(tpl)"
                @input="onTemplateInput(tpl)"
              />
              <div
                :ref="'hl-' + platform + '-' + tpl.type"
                class="template-highlight"
                aria-hidden="true"
                v-html="highlightPlaceholders(tpl.value)"
              />
            </div>
            <div v-if="getBraceWarning(tpl.value)" class="text-warning small mt-1">
              ⚠ Несбалансированные скобки в плейсхолдерах — неизвестные останутся как literal.
            </div>

            <!-- Preview -->
            <div class="mt-3">
              <label class="form-label small fw-bold">Превью на тестовой песне (id):</label>
              <div class="input-group input-group-sm mb-2" style="max-width: 400px">
                <input
                  v-model.number="tpl.previewSongId"
                  type="number"
                  class="form-control"
                  placeholder="ID песни"
                />
                <button
                  class="btn btn-outline-primary"
                  :disabled="!tpl.previewSongId || tpl.previewLoading"
                  @click="previewTemplate(tpl)"
                >
                  {{ tpl.previewLoading ? '…' : 'Превью' }}
                </button>
              </div>
              <div v-if="tpl.previewError" class="alert alert-danger py-1 small">
                {{ tpl.previewError }}
              </div>
              <div v-if="tpl.previewText !== null" class="preview-box">
                <div class="d-flex justify-content-between align-items-center mb-1">
                  <span class="small text-muted">
                    Длина: {{ tpl.previewLength }} / {{ tpl.previewMaxLength }}
                    <span v-if="tpl.previewTruncated" class="text-warning"
                      >(усечено до лимита)</span
                    >
                  </span>
                </div>
                <pre class="preview-text">{{ tpl.previewText }}</pre>
              </div>
            </div>

            <!-- Сохранение -->
            <button
              class="btn btn-primary mt-3"
              :disabled="saving === tpl.key"
              @click="saveTemplate(tpl)"
            >
              {{ saving === tpl.key ? 'Сохранение…' : 'Сохранить' }}
            </button>
            <span v-if="savedMessage[tpl.key]" class="text-success small ms-2">{{
              savedMessage[tpl.key]
            }}</span>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import { promisedXMLHttpRequest } from '../lib/utils'
import NewsTemplatesEditor from '../components/NewsTemplates/NewsTemplatesEditor.vue'

/**
 * Компонент «Шаблоны публикаций»: редактор caption для VK/Telegram + редактор
 * шаблонов авто-новостей сайта (title+body). Третья вкладка «Новости сайта»
 * делегирует работу дочернему компоненту [NewsTemplatesEditor].
 *
 * @see archive/docs/features/news-templates.md
 */
export default {
  name: 'PublishTemplatesView',
  components: { NewsTemplatesEditor },
  data() {
    return {
      loading: true,
      error: '',
      platform: 'vk', // 'vk' | 'telegram' | 'news'
      activeTab: 'air',
      vkTemplates: [],
      telegramTemplates: [],
      vkPlaceholders: [],
      telegramPlaceholders: [],
      vkDefaults: {},
      telegramDefaults: {},
      saving: null,
      savedMessage: {},
      placeholdersExpanded: false,
    }
  },
  computed: {
    currentTemplates() {
      return this.platform === 'vk' ? this.vkTemplates : this.telegramTemplates
    },
    currentPlaceholders() {
      return this.platform === 'vk' ? this.vkPlaceholders : this.telegramPlaceholders
    },
    currentDefaults() {
      return this.platform === 'vk' ? this.vkDefaults : this.telegramDefaults
    },
  },
  mounted() {
    this.loadAll()
  },
  methods: {
    loadAll() {
      this.loading = true
      this.error = ''
      Promise.all([
        promisedXMLHttpRequest({ method: 'GET', url: '/api/vk/templates' }),
        promisedXMLHttpRequest({ method: 'GET', url: '/api/vk/templates/defaults' }),
        promisedXMLHttpRequest({ method: 'GET', url: '/api/telegram/templates' }),
        promisedXMLHttpRequest({ method: 'GET', url: '/api/telegram/templates/defaults' }),
      ])
        .then(([vkResp, vkDefResp, tgResp, tgDefResp]) => {
          this.loading = false
          const vk = this.parseTemplates(vkResp)
          this.vkTemplates = vk.templates
          this.vkPlaceholders = vk.placeholders
          this.vkDefaults = this.parseDefaults(vkDefResp)
          const tg = this.parseTemplates(tgResp)
          this.telegramTemplates = tg.templates
          this.telegramPlaceholders = tg.placeholders
          this.telegramDefaults = this.parseDefaults(tgDefResp)
        })
        .catch((err) => {
          this.loading = false
          this.error = err && err.message ? err.message : 'Ошибка загрузки шаблонов.'
        })
    },
    parseTemplates(response) {
      let data = {}
      try {
        data = typeof response === 'string' ? JSON.parse(response) : response
      } catch (_e) {
        data = {}
      }
      const templates = (data.templates || []).map((t) => ({
        ...t,
        previewSongId: null,
        previewText: null,
        previewError: '',
        previewLoading: false,
        previewLength: 0,
        previewMaxLength: 1024,
        previewTruncated: false,
      }))
      return { templates, placeholders: data.placeholders || [] }
    },
    parseDefaults(response) {
      let data = {}
      try {
        data = typeof response === 'string' ? JSON.parse(response) : response
      } catch (_e) {
        data = {}
      }
      return data.defaults || {}
    },
    saveTemplate(tpl) {
      this.saving = tpl.key
      const url = this.platform === 'vk' ? '/api/vk/templates' : '/api/telegram/templates'
      const params = { key: tpl.key, value: tpl.value }
      const request = { method: 'POST', url, params }
      promisedXMLHttpRequest(request)
        .then((response) => {
          this.saving = null
          let data = {}
          try {
            data = typeof response === 'string' ? JSON.parse(response) : response
          } catch (_e) {
            data = {}
          }
          if (data.success) {
            this.savedMessage = { ...this.savedMessage, [tpl.key]: 'Сохранено.' }
            setTimeout(() => {
              this.savedMessage = { ...this.savedMessage, [tpl.key]: '' }
            }, 3000)
          } else {
            this.error = data.error || 'Ошибка сохранения.'
          }
        })
        .catch((err) => {
          this.saving = null
          this.error = err && err.message ? err.message : 'Ошибка сохранения.'
        })
    },
    resetToDefault(tpl) {
      const def = this.currentDefaults[tpl.key]
      if (def != null) {
        tpl.value = def
        tpl.previewText = null
      }
    },
    previewTemplate(tpl) {
      if (!tpl.previewSongId) return
      tpl.previewLoading = true
      tpl.previewError = ''
      tpl.previewText = null
      const url =
        this.platform === 'vk' ? '/api/vk/templates/preview' : '/api/telegram/templates/preview'
      const params = { value: tpl.value, id: tpl.previewSongId }
      const request = { method: 'POST', url, params }
      promisedXMLHttpRequest(request)
        .then((response) => {
          tpl.previewLoading = false
          let data = {}
          try {
            data = typeof response === 'string' ? JSON.parse(response) : response
          } catch (_e) {
            data = {}
          }
          if (data.success) {
            tpl.previewText = data.preview || ''
            tpl.previewLength = data.length || 0
            tpl.previewMaxLength = data.maxLength || 1024
            tpl.previewTruncated = !!data.truncated
          } else {
            tpl.previewError = data.error || 'Ошибка preview.'
          }
        })
        .catch((err) => {
          tpl.previewLoading = false
          tpl.previewError = err && err.message ? err.message : 'Ошибка preview.'
        })
    },
    onTemplateInput(tpl) {
      tpl.previewText = null
    },
    syncScroll(tpl) {
      const hl = this.$refs['hl-' + this.platform + '-' + tpl.type]
      if (hl && hl[0]) {
        const ta = hl[0].previousElementSibling
        if (ta) {
          hl[0].scrollTop = ta.scrollTop
          hl[0].scrollLeft = ta.scrollLeft
        }
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
