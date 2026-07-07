<template>
  <div class="km-page">
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/account" class="km-back">← Личный кабинет</RouterLink>
          <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
        </div>
      </div>
    </header>

    <div class="km-content">
      <h1 class="km-title">🎤 Редактор караоке</h1>
      <p class="ke-intro">Здесь появляются песни, которые вам назначили на разметку. Разметьте слоги под
        музыку и отправьте на проверку — после одобрения песня станет доступна в онлайн-плеере.</p>

      <div v-if="loading" class="ke-empty">Загрузка…</div>
      <div v-else-if="tasks.length === 0" class="ke-empty">
        Вам пока не назначено ни одной песни.
      </div>

      <div v-else class="ke-list">
        <RouterLink
          v-for="t in tasks"
          :key="t.id"
          :to="`/account/editor/${t.id}`"
          class="ke-card"
        >
          <div class="ke-card-main">
            <div class="ke-card-song">{{ t.songName || 'Без названия' }}</div>
            <div class="ke-card-author">{{ t.author }}<span v-if="t.album"> · {{ t.album }}</span><span v-if="t.year"> · {{ t.year }}</span></div>
            <div v-if="t.status === 'rejected' && t.reviewComment" class="ke-card-comment">
              💬 {{ t.reviewComment }}
            </div>
          </div>
          <div class="ke-card-side">
            <span class="ke-badge" :class="`ke-badge-${t.status}`">{{ statusLabel(t.status) }}</span>
            <span class="ke-card-arrow">→</span>
          </div>
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script>
import { fetchTasks } from '../services/songEditorApi'
import { useAuth } from '../composables/useAuth'
import { STATUS_LABELS } from '../composables/editorStatus'

export default {
  name: 'EditorTasksView',
  setup() {
    const { token, fetchMe } = useAuth()
    return { token, fetchMe }
  },
  data() {
    return { tasks: [], loading: true }
  },
  async mounted() {
    await this.fetchMe()
    if (!this.token) {
      this.$router.push({ path: '/login', query: { redirect: '/account/editor' } })
      return
    }
    await this.load()
  },
  methods: {
    statusLabel(s) {
      return STATUS_LABELS[s] || s
    },
    async load() {
      this.loading = true
      try {
        const { status, body } = await fetchTasks()
        this.tasks = (status === 200 && Array.isArray(body)) ? body : []
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.km-page { min-height: 100vh; background: var(--km-bg); color: var(--km-text); }
.km-header { background: var(--km-header); border-bottom: 1px solid var(--km-border); padding: 0.5rem 1rem; }
.km-header-inner { max-width: 700px; margin: 0 auto; display: flex; align-items: center; justify-content: space-between; }
.km-header-left { display: flex; align-items: center; gap: 0.75rem; }
.km-back { color: var(--km-accent); text-decoration: none; font-size: 0.85rem; white-space: nowrap; }
.km-back:hover { text-decoration: underline; }
.km-logo { height: 36px; width: auto; }
.km-content { max-width: 640px; margin: 0 auto; padding: 2rem 1rem; }
.km-title { font-size: 1.4rem; margin: 0 0 0.5rem; }
.ke-intro { color: var(--km-text2); font-size: 0.9rem; margin: 0 0 1.5rem; line-height: 1.5; }
.ke-empty { color: var(--km-text2); text-align: center; padding: 3rem 1rem; }
.ke-list { display: flex; flex-direction: column; gap: 0.75rem; }
.ke-card {
  display: flex; align-items: center; justify-content: space-between; gap: 1rem;
  background: var(--km-card); border: 1px solid var(--km-border); border-radius: 14px;
  padding: 1rem 1.25rem; text-decoration: none; color: var(--km-text);
  transition: background 0.15s, transform 0.15s;
}
.ke-card:hover { background: var(--km-hover); transform: translateY(-1px); }
.ke-card-main { min-width: 0; }
.ke-card-song { font-weight: 600; font-size: 1.02rem; }
.ke-card-author { color: var(--km-text2); font-size: 0.85rem; margin-top: 0.15rem; }
.ke-card-comment { color: #d98a2b; font-size: 0.82rem; margin-top: 0.4rem; }
.ke-card-side { display: flex; align-items: center; gap: 0.75rem; flex-shrink: 0; }
.ke-card-arrow { color: var(--km-accent); font-size: 1.1rem; }
.ke-badge { font-size: 0.72rem; font-weight: 700; border-radius: 20px; padding: 0.22rem 0.7rem; white-space: nowrap; }
.ke-badge-assigned { background: #e2e6ea; color: #5a6570; }
.ke-badge-in_progress { background: #dbeafe; color: #1e5fbf; }
.ke-badge-submitted { background: #fef3c7; color: #92700a; }
.ke-badge-approved { background: #d1f5d8; color: #24803a; }
.ke-badge-rejected { background: #ffe0cc; color: #b8500f; }
</style>
