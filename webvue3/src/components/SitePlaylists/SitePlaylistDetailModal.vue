<template>
  <div class="spd-overlay" @click.self="$emit('close')">
    <div class="spd-modal">
      <div class="spd-head">
        <div>
          <div class="spd-title">
            {{ detail ? detail.name : ''
            }}<span v-if="detail && detail.favorites" class="spd-fav"> ★ Избранное</span>
          </div>
          <div v-if="detail" class="spd-owner">
            {{ detail.ownerName }} &lt;{{ detail.ownerEmail }}&gt; (id={{ detail.ownerId }})
          </div>
        </div>
        <button class="spd-close" @click="$emit('close')">×</button>
      </div>

      <div v-if="!detail" class="spd-loading">Загрузка...</div>
      <div v-else>
        <div class="spd-meta">
          Непрерывно: {{ detail.continuous ? 'да' : 'нет' }} · Повтор:
          {{ repeatLabel(detail.repeatMode) }} · Случайно: {{ detail.shuffle ? 'да' : 'нет' }}
        </div>
        <table class="spd-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Песня</th>
              <th>Исполнитель</th>
              <th>Альбом</th>
              <th>Год</th>
              <th>Mute</th>
              <th>song_id</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(it, i) in detail.items" :key="it.songId">
              <td>{{ i + 1 }}</td>
              <td class="spd-left">{{ it.songName }}</td>
              <td class="spd-left">{{ it.author }}</td>
              <td class="spd-left">{{ it.album }}</td>
              <td>{{ it.year || '' }}</td>
              <td>{{ it.muted ? '🔇' : '' }}</td>
              <td>{{ it.songId }}</td>
            </tr>
            <tr v-if="!detail.items.length">
              <td colspan="7" class="spd-empty">Пусто</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SitePlaylistDetailModal',
  computed: {
    detail() {
      return this.$store.getters.getSitePlaylistDetail
    },
  },
  methods: {
    repeatLabel(m) {
      return m === 'one' ? 'одна' : m === 'all' ? 'все' : 'нет'
    },
  },
}
</script>

<style scoped>
.spd-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}
.spd-modal {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  width: 720px;
  max-width: 95vw;
  max-height: 88vh;
  overflow-y: auto;
  font-size: small;
}
.spd-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}
.spd-title {
  font-size: 1.1rem;
  font-weight: 700;
}
.spd-fav {
  color: #d99413;
  font-size: 0.85rem;
}
.spd-owner {
  color: #666;
  font-size: 0.8rem;
  margin-top: 2px;
}
.spd-close {
  background: transparent;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  line-height: 1;
}
.spd-loading,
.spd-empty {
  text-align: center;
  color: gray;
  padding: 20px;
}
.spd-meta {
  color: #444;
  margin-bottom: 8px;
}
.spd-table {
  width: 100%;
  border-collapse: collapse;
}
.spd-table th,
.spd-table td {
  border: 1px solid #ddd;
  padding: 3px 6px;
  text-align: center;
}
.spd-left {
  text-align: left;
}
</style>
