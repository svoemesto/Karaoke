<template>
  <div class="song-edit">
    <button type="button" @click="deleteSong">Удалить песню</button>
  </div>
</template>

<script>
import { escapeHtml } from '../../../utils/escapeHtml.js'

/**
 * ФИКСТУРА (исправленная версия) — спека #558, OpenProject #360, BR-123.
 *
 * Имя песни экранируется при сборке текста подтверждения («на входе»),
 * HTML-режим включается явно.
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
        allowHtml: true,
        header: 'Подтвердите удаление песни',
        body: `Удалить песню <strong>«${escapeHtml(this.song.songName)}»</strong>?`,
        timeout: 10,
        callback: () => {},
      }
      this.isCustomConfirmVisible = true
    },
  },
}
</script>
