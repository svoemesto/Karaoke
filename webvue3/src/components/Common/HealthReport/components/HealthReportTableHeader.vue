<template>
  <div class="hrl-table-footer">
    <button
      type="button"
      class="hrl-table-header-button-repair-all"
      :disabled="healthReportListCanRepair.length === 0"
      @click="repairAll"
    >
      Repair All ({{ healthReportListCanRepair.length }})
    </button>
    <button
      type="button"
      class="hrl-table-header-button-reset-cache"
      title="Сбросить кеш хранилища для этой песни и перечитать реальное состояние MinIO"
      @click="resetStorageCache"
    >
      Сбросить кеш
    </button>
  </div>
</template>

<script>
/**
 * Компонент «Health Report Table Header».
 *
 * @see AGENTS.md
 */
export default {
  name: 'HealthReportTableHeader',
  props: {
    healthReportList: {
      type: Array,
      required: true,
      defaults: [],
    },
    // OpenProject #325: id песни передаётся явно — при нуле записей HR список пуст,
    // и раньше songId было взять неоткуда, поэтому сброс кеша был недоступен.
    songId: {
      type: [String, Number],
      default: null,
    },
  },
  computed: {
    healthReportListCanRepair() {
      return this.healthReportList.filter((healthReport) => healthReport.canResolve)
    },
    effectiveSongId() {
      if (this.songId !== null && this.songId !== undefined && this.songId !== '') {
        return this.songId
      }
      return this.healthReportList.length > 0 ? this.healthReportList[0].songId : null
    },
  },
  methods: {
    repairAll() {
      if (this.healthReportList.length === 0) return
      this.$store.dispatch('repairAllPromise', this.effectiveSongId)
    },
    // Спека #446: сброс persistent-кеша хранилища для одной песни; после — перезапрос
    // health-report (loadHealthReportList), чтобы увидеть актуальное состояние MinIO.
    resetStorageCache() {
      if (this.effectiveSongId === null) return
      const songId = this.effectiveSongId
      this.$store.dispatch('resetStorageCachePromise', [songId]).then(() => {
        this.$store.dispatch('loadHealthReportList', songId)
      })
    },
  },
}
</script>

<style scoped>
.hrl-table-footer {
  width: fit-content;
  display: flex;
  gap: 4px;
}

.hrl-table-header-button-repair-all {
  min-width: 140px;
  max-width: 140px;
  text-align: center;
  padding: 0 3px;
  font-size: small;
}

.hrl-table-header-button-reset-cache {
  min-width: 110px;
  max-width: 110px;
  text-align: center;
  padding: 0 3px;
  font-size: small;
}
</style>
