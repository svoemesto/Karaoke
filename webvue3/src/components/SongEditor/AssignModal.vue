<template>
  <div class="se-overlay" @click.self="$emit('close')">
    <div class="se-modal">
      <h3 class="se-modal-title">Назначить песню на разметку</h3>

      <label class="se-field">
        <span>Пользователь сайта</span>
        <select v-model="assigneeId">
          <option :value="0" disabled>— выберите —</option>
          <option v-for="u in siteUsers" :key="u.id" :value="u.id">
            {{ u.displayName || u.email }} ({{ u.email }})
          </option>
        </select>
      </label>

      <label class="se-field">
        <span>ID песни</span>
        <input type="number" v-model.number="songId" placeholder="Например, 12345" />
      </label>

      <label class="se-field">
        <span>Голос (обычно 0)</span>
        <input type="number" v-model.number="voice" min="0" />
      </label>

      <p v-if="message" class="se-msg" :class="{ 'se-msg-err': isError }">{{ message }}</p>

      <div class="se-modal-btns">
        <button class="se-btn" @click="$emit('close')">Отмена</button>
        <button class="se-btn se-btn-primary" :disabled="!canSubmit || busy" @click="doAssign">
          {{ busy ? 'Назначаем…' : 'Назначить' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "AssignModal",
  emits: ['close', 'assigned'],
  data() {
    return { assigneeId: 0, songId: null, voice: 0, busy: false, message: '', isError: false }
  },
  computed: {
    siteUsers() { return this.$store.getters.getEditorSiteUsers || [] },
    canSubmit() { return this.assigneeId > 0 && this.songId > 0 },
  },
  mounted() {
    this.$store.dispatch('loadEditorSiteUsers');
  },
  methods: {
    async doAssign() {
      this.busy = true;
      this.message = '';
      try {
        const res = await this.$store.dispatch('assignSong', { songId: this.songId, assigneeId: this.assigneeId, voice: this.voice });
        if (res && res.ok) {
          this.$emit('assigned');
        } else {
          this.isError = true;
          this.message = this.errorText(res && res.error);
        }
      } catch (e) {
        this.isError = true;
        this.message = 'Ошибка запроса';
      } finally {
        this.busy = false;
      }
    },
    errorText(code) {
      return {
        song_not_found: 'Песня с таким ID не найдена',
        user_not_found: 'Пользователь не найден',
        already_assigned: 'Эта песня уже назначена этому пользователю',
      }[code] || 'Не удалось назначить';
    }
  }
}
</script>

<style scoped>
.se-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.45);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
}
.se-modal {
  background: #fff; border-radius: 12px; padding: 1.5rem; width: 420px; max-width: 92vw;
  display: flex; flex-direction: column; gap: 0.8rem; font-family: Avenir, Helvetica, Arial, sans-serif;
}
.se-modal-title { margin: 0 0 0.5rem; font-size: 1.1rem; }
.se-field { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.85rem; }
.se-field select, .se-field input {
  padding: 0.45rem 0.6rem; border: 1px solid #ccc; border-radius: 8px; font-size: 0.9rem;
}
.se-msg { font-size: 0.85rem; color: #2a7a3a; margin: 0; }
.se-msg-err { color: #c0392b; }
.se-modal-btns { display: flex; justify-content: flex-end; gap: 0.6rem; margin-top: 0.5rem; }
.se-btn {
  border: 1px solid #bbb; border-radius: 8px; padding: 0.45rem 1rem; background: antiquewhite;
  cursor: pointer; font-size: 0.9rem;
}
.se-btn:hover { background: lightpink; }
.se-btn-primary { background: #3b82f6; color: #fff; border: none; }
.se-btn-primary:hover { opacity: 0.9; background: #3b82f6; }
.se-btn:disabled { opacity: 0.5; cursor: default; }
</style>
