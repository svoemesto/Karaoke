<template>
  <div class="se-overlay" @click.self="$emit('close')">
    <div class="se-modal se-modal-wide">
      <template v-if="a">
        <h3 class="se-modal-title">
          {{ a.songName || 'Песня' }} <span class="se-dim">— {{ a.author }}</span>
        </h3>
        <div class="se-meta">
          <span>Исполнитель задания: <strong>{{ a.assigneeName || a.assigneeEmail }}</strong></span>
          <span>Голосов: {{ voiceCount }}</span>
          <span>Статус: <span class="se-badge" :class="`se-badge-${a.status}`">{{ statusLabel(a.status) }}</span></span>
          <span>ID песни: {{ a.songId }}</span>
        </div>

        <div class="se-player-toggle">
          <button type="button" class="se-btn" @click="showPlayer = !showPlayer">
            {{ showPlayer ? 'Скрыть плеер' : '▶ Прослушать (черновик)' }}
          </button>
        </div>
        <div v-if="showPlayer" class="se-player-wrap">
          <iframe :src="playerSrc" class="se-player-frame" allow="autoplay"></iframe>
        </div>

        <div v-if="voiceCount > 1" class="se-voice-tabs">
          <button
              v-for="i in voiceCount" :key="i" type="button" class="se-voice-tab"
              :class="{ 'se-voice-tab-active': currentVoiceIdx === i - 1 }"
              @click="currentVoiceIdx = i - 1"
          >Голос {{ i }}</button>
        </div>

        <div class="se-cols">
          <div class="se-col">
            <div class="se-col-title">Текст пользователя</div>
            <pre class="se-text">{{ currentSourceText || '(пусто)' }}</pre>
          </div>
          <div class="se-col">
            <div class="se-col-title">Маркеры: {{ markerCount }}</div>
            <div class="se-marker-summary">
              <div>Слоги: {{ markerStats.syllables }}</div>
              <div>Концы строк: {{ markerStats.endofline }}</div>
              <div>Новые строки: {{ markerStats.newline }}</div>
              <div>END: {{ markerStats.end ? 'есть' : 'нет' }}</div>
            </div>
          </div>
        </div>

        <div v-if="a.reviewComment" class="se-prev-comment">Прошлый комментарий: {{ a.reviewComment }}</div>
        <div v-if="isRemoteView" class="se-remote-note">
          Запись открыта из серверной БД — «Одобрить» прочитает черновик оттуда (актуальные правки
          пользователя, если они ещё не подтянуты синхронизацией), но применит их к песне и статусу
          задания всегда в локальной БД.
        </div>

        <label class="se-field">
          <span>Комментарий (при отклонении)</span>
          <textarea v-model="comment" rows="2" placeholder="Что нужно исправить…"></textarea>
        </label>

        <p v-if="message" class="se-msg" :class="{ 'se-msg-err': isError }">{{ message }}</p>

        <div class="se-modal-btns">
          <button class="se-btn" @click="$emit('close')">Закрыть</button>
          <button class="se-btn se-btn-danger" :disabled="busy" @click="doReject">Отклонить</button>
          <button class="se-btn se-btn-primary" :disabled="busy" @click="doApprove">Одобрить и применить</button>
        </div>
      </template>
      <div v-else class="se-loading">Загрузка…</div>
    </div>
  </div>
</template>

<script>
const STATUS_LABELS = {
  assigned: 'Назначено', in_progress: 'В работе', submitted: 'На проверке',
  approved: 'Одобрено', rejected: 'Отклонено',
};

export default {
  name: "ReviewModal",
  emits: ['close', 'reviewed'],
  data() {
    return { comment: '', busy: false, message: '', isError: false, showPlayer: false, currentVoiceIdx: 0 }
  },
  computed: {
    a() { return this.$store.getters.getAssignmentCurrent },
    // Только для информационного баннера — approve/reject безопасны и корректны в обоих режимах
    // (id совпадает в LOCAL/REMOTE), approve при этом читает черновик оттуда, где target, но
    // применяет его всегда в LOCAL (см. SongEditorController.approve).
    isRemoteView() { return this.$store.getters.getAssignmentsTarget === 'remote' },
    // Превью неодобрённого черновика: /player/:id понимает assignmentId и подставляет edited_markers
    // ВСЕЙ песни (все голоса задания) вместо того, что уже сохранено в tbl_settings (см. ApiController.getSongPlayerData).
    playerSrc() { return this.a ? `/player/${this.a.songId}?assignmentId=${this.a.id}` : '' },
    voiceCount() { return this.a ? Math.max(1, (this.a.draftMarkersPerVoice || []).length) : 0 },
    currentSourceText() { return (this.a && this.a.draftSourceTexts && this.a.draftSourceTexts[this.currentVoiceIdx]) || '' },
    parsedMarkers() {
      return (this.a && this.a.draftMarkersPerVoice && this.a.draftMarkersPerVoice[this.currentVoiceIdx]) || [];
    },
    markerCount() { return this.parsedMarkers.length },
    markerStats() {
      const s = { syllables: 0, endofline: 0, newline: 0, end: false };
      for (const m of this.parsedMarkers) {
        if (m.markertype === 'syllables') s.syllables++;
        else if (m.markertype === 'endofline') s.endofline++;
        else if (m.markertype === 'newline') s.newline++;
        else if (m.markertype === 'setting' && m.label === 'END') s.end = true;
      }
      return s;
    }
  },
  methods: {
    statusLabel(s) { return STATUS_LABELS[s] || s },
    async doApprove() {
      this.busy = true; this.message = '';
      try {
        const res = await this.$store.dispatch('approveAssignment', this.a.id);
        if (res && res.ok) { this.$emit('reviewed'); }
        else { this.isError = true; this.message = 'Не удалось одобрить: ' + (res && res.error || ''); }
      } catch (e) { this.isError = true; this.message = 'Ошибка запроса'; }
      finally { this.busy = false; }
    },
    async doReject() {
      this.busy = true; this.message = '';
      try {
        const res = await this.$store.dispatch('rejectAssignment', { id: this.a.id, comment: this.comment });
        if (res && res.ok) { this.$emit('reviewed'); }
        else { this.isError = true; this.message = 'Не удалось отклонить'; }
      } catch (e) { this.isError = true; this.message = 'Ошибка запроса'; }
      finally { this.busy = false; }
    }
  }
}
</script>

<style scoped>
.se-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.se-modal { background: #fff; border-radius: 12px; padding: 1.5rem; width: 420px; max-width: 92vw; display: flex; flex-direction: column; gap: 0.8rem; font-family: Avenir, Helvetica, Arial, sans-serif; }
.se-modal-wide { width: 760px; }
.se-player-toggle { display: flex; }
.se-player-wrap { width: 100%; height: 440px; border-radius: 8px; overflow: hidden; background: #000; }
.se-player-frame { width: 100%; height: 100%; border: none; display: block; }
.se-modal-title { margin: 0; font-size: 1.15rem; }
.se-dim { color: #888; font-weight: 400; }
.se-meta { display: flex; flex-wrap: wrap; gap: 0.4rem 1.2rem; font-size: 0.82rem; color: #555; }
.se-voice-tabs { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.se-voice-tab {
  border: 1px solid #bbb; border-radius: 20px; padding: 0.3rem 0.9rem; background: #fff;
  cursor: pointer; font-size: 0.8rem;
}
.se-voice-tab:hover { background: #f5f5f5; }
.se-voice-tab-active { background: #24803a; color: #fff; border-color: #24803a; }
.se-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.se-col-title { font-size: 0.72rem; text-transform: uppercase; color: #888; font-weight: 700; margin-bottom: 0.3rem; }
.se-text { background: #f5f5f5; border-radius: 8px; padding: 0.6rem; font-size: 0.82rem; max-height: 220px; overflow: auto; white-space: pre-wrap; margin: 0; }
.se-marker-summary { background: #f5f5f5; border-radius: 8px; padding: 0.6rem; font-size: 0.85rem; display: flex; flex-direction: column; gap: 0.3rem; }
.se-prev-comment { font-size: 0.8rem; color: #a9500f; background: #fff3e8; border-radius: 8px; padding: 0.4rem 0.6rem; }
.se-remote-note { font-size: 0.8rem; color: #8a6d0a; background: #fef8e3; border: 1px solid #f2dd9a; border-radius: 8px; padding: 0.5rem 0.6rem; }
.se-field { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.85rem; }
.se-field textarea { padding: 0.45rem 0.6rem; border: 1px solid #ccc; border-radius: 8px; font-size: 0.9rem; resize: vertical; }
.se-msg { font-size: 0.85rem; color: #2a7a3a; margin: 0; }
.se-msg-err { color: #c0392b; }
.se-modal-btns { display: flex; justify-content: flex-end; gap: 0.6rem; margin-top: 0.5rem; }
.se-btn { border: 1px solid #bbb; border-radius: 8px; padding: 0.45rem 1rem; background: antiquewhite; cursor: pointer; font-size: 0.9rem; }
.se-btn:hover { background: lightpink; }
.se-btn-primary { background: #24803a; color: #fff; border: none; }
.se-btn-primary:hover { opacity: 0.9; background: #24803a; }
.se-btn-danger { background: #c0392b; color: #fff; border: none; }
.se-btn-danger:hover { opacity: 0.9; background: #c0392b; }
.se-btn:disabled { opacity: 0.5; cursor: default; }
.se-loading { padding: 2rem; text-align: center; color: #888; }
.se-badge { font-size: 0.7rem; font-weight: 700; border-radius: 20px; padding: 0.15rem 0.6rem; }
.se-badge-assigned { background: #e2e6ea; color: #5a6570; }
.se-badge-in_progress { background: #dbeafe; color: #1e5fbf; }
.se-badge-submitted { background: #fef3c7; color: #92700a; }
.se-badge-approved { background: #d1f5d8; color: #24803a; }
.se-badge-rejected { background: #ffe0cc; color: #b8500f; }
</style>
