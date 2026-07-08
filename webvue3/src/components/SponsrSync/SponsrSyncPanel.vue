<template>
  <div class="sps-wrapper">
    <div class="sps-toolbar">
      <label class="sps-toolbar-item">
        БД:
        <select v-model="target" @change="reload">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <button class="sps-btn" @click="reload">Обновить статус</button>
    </div>

    <div v-if="status" class="sps-status">
      <div>URL страницы подписчиков (sponsrSubscribersUrl):
        <b :class="status.subscribersUrlConfigured ? 'sps-ok' : 'sps-warn'">
          {{ status.subscribersUrlConfigured ? 'настроен' : 'НЕ настроен' }}
        </b>
        <span class="sps-hint">(настраивается в разделе «Настройки» — свойство sponsrSubscribersUrl)</span>
      </div>
      <div>Сохранённая сессия Sponsr:
        <b :class="status.sessionSaved ? 'sps-ok' : 'sps-warn'">{{ status.sessionSaved ? 'есть' : 'нет' }}</b>
        <span class="sps-hint">(создаётся вручную вызовом createNewSponsrAuthContext() на admin-машине)</span>
      </div>
      <div>Окно продления: {{ status.syncWindowDays }} дн. <span class="sps-hint">(свойство sponsrSyncWindowDays)</span></div>
    </div>

    <div class="sps-section">
      <div class="sps-section-title">Ручной импорт списка подписчиков</div>
      <div class="sps-hint">По одному email или sponsr_uid на строке (или через запятую).</div>
      <textarea class="sps-textarea" v-model="identifiersText" rows="6" placeholder="user1@example.com&#10;user2@example.com"></textarea>
      <button class="sps-btn" :disabled="isLoading || !identifiersText.trim()" @click="doImport">Импортировать</button>
    </div>

    <div class="sps-section">
      <div class="sps-section-title">Экспериментальный скрейпинг кабинета Sponsr</div>
      <div class="sps-hint">Работает только при настроенном URL и сохранённой сессии (см. статус выше).</div>
      <button class="sps-btn" :disabled="isLoading" @click="doRun">Синхронизировать сейчас</button>
    </div>

    <div v-if="isLoading" class="sps-loading">Выполняется...</div>
    <div v-if="lastResult" class="sps-result" :class="{ 'sps-result-fail': !lastResult.ok }">
      <div>Найдено идентификаторов: {{ lastResult.foundIdentifiers }}, сопоставлено пользователей: {{ lastResult.matchedUsers }}</div>
      <ul>
        <li v-for="(m, i) in lastResult.messages" :key="i">{{ m }}</li>
      </ul>
    </div>
  </div>
</template>

<script>
export default {
  name: "SponsrSyncPanel",
  data() {
    return { identifiersText: '' }
  },
  computed: {
    status() { return this.$store.getters.getSponsrSyncStatus },
    isLoading() { return this.$store.getters.getSponsrSyncIsLoading },
    lastResult() { return this.$store.getters.getSponsrSyncLastResult },
    target: {
      get() { return this.$store.getters.getSponsrSyncTarget },
      set(v) { this.$store.dispatch('setSponsrSyncTarget', v) }
    },
  },
  mounted() {
    this.reload();
  },
  methods: {
    reload() {
      this.$store.dispatch('loadSponsrSyncStatus');
    },
    async doImport() {
      await this.$store.dispatch('importSponsrList', this.identifiersText);
    },
    async doRun() {
      await this.$store.dispatch('runSponsrSync');
    }
  }
}
</script>

<style scoped>
.sps-wrapper { width: 100%; max-width: 800px; margin: 10px auto; font-family: Avenir, Helvetica, Arial, sans-serif; font-size: small; }
.sps-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.sps-status { border: thin dashed darkgray; border-radius: 8px; padding: 10px; margin-bottom: 14px; line-height: 1.8; }
.sps-ok { color: darkgreen; }
.sps-warn { color: darkred; }
.sps-hint { color: gray; font-size: x-small; margin-left: 6px; }
.sps-section { margin-bottom: 16px; padding: 10px; border: thin dashed darkgray; border-radius: 8px; }
.sps-section-title { font-weight: bold; margin-bottom: 6px; }
.sps-textarea { width: 100%; font-family: monospace; padding: 6px; border-radius: 4px; border: 1px solid black; margin-bottom: 8px; }
.sps-btn { border: solid 1px black; border-radius: 6px; padding: 4px 12px; background-color: antiquewhite; cursor: pointer; }
.sps-btn:hover { background-color: lightpink; }
.sps-btn[disabled] { background-color: lightgray; cursor: default; }
.sps-loading { color: gray; }
.sps-result { border: thin solid darkgreen; border-radius: 8px; padding: 10px; background: #eaffea; }
.sps-result-fail { border-color: darkred; background: #ffeaea; }
.sps-result ul { margin: 6px 0 0; padding-left: 18px; }
</style>
