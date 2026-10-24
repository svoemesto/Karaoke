<template>
  <div class="song-edit">
    <button type="button" @click="deleteSong">Удалить песню</button>
  </div>
</template>

<script>
/**
 * ФИКСТУРА (уязвимая версия) — спека #558, OpenProject #360, BR-123.
 *
 * Имя песни подставляется в текст подтверждения как разметка, без
 * экранирования: имя с `<img src=x onerror=…>` выполняется в сессии
 * администратора. Guard обязан найти это место (правило R3).
 */
export default {
  name: 'SongEdit',
  data() {
    return {
      song: { songName: 'Говорит Деметра' },
    }
  },
  methods: {
    deleteSong() {
      this.customConfirmParams = {
        header: 'Подтвердите удаление песни',
        body: `Удалить песню <strong>«${this.song.songName}»</strong>?`,
        timeout: 10,
        callback: () => {},
      }
      this.isCustomConfirmVisible = true
    },
  },
}
</script>
