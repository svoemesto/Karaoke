<template>
  <transition name="modal-fade">
    <div class="mon-modal-backdrop" @click.self="close">
      <div class="mon-area">
        <div class="mon-area-header">
          <span>Мониторинг</span>
          <button class="mon-btn-close-x" @click="close" v-text="'×'"/>
        </div>
        <div class="mon-area-body">
          <div v-if="alerts.length === 0" class="mon-empty">Активных сообщений нет.</div>
          <div
              v-for="alert in alerts"
              :key="alert.key"
              class="mon-row"
              :style="{ borderLeftColor: alert.color }"
          >
            <div class="mon-row-main">
              <div class="mon-row-title" v-text="alert.title"/>
              <div class="mon-row-body" v-text="alert.body"/>
              <div v-if="alert.detail" class="mon-row-detail" v-text="alert.detail"/>
            </div>
            <div class="mon-row-actions">
              <button class="mon-btn mon-btn-read" @click="markRead(alert)" v-text="'Прочитано'"/>
              <button
                  v-if="alert.canResolve"
                  class="mon-btn mon-btn-resolve"
                  :disabled="resolvingKeys.includes(alert.key)"
                  @click="resolve(alert)"
                  v-text="resolvingKeys.includes(alert.key) ? 'Решаем...' : 'Решить проблему'"
              />
              <div v-else class="mon-recommend-wrap">
                <button class="mon-btn mon-btn-recommend" @click="toggleTip(alert.key)" v-text="'Рекомендации'"/>
                <div v-if="openTipKey === alert.key" class="mon-recommend-popover" v-text="alert.recommendations || 'Рекомендаций нет.'"/>
              </div>
            </div>
          </div>
        </div>
        <div class="mon-area-footer">
          <button class="mon-btn-footer-close" @click="close" v-text="'Закрыть'"/>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: "MonitorModal",
  data() {
    return {
      openTipKey: null,
      resolvingKeys: []
    }
  },
  computed: {
    alerts() {
      return this.$store.getters.monitorVisibleAlerts;
    }
  },
  methods: {
    close() {
      this.openTipKey = null;
      this.$emit('close');
    },
    markRead(alert) {
      this.$store.dispatch('markMonitorRead', alert.key);
    },
    resolve(alert) {
      this.resolvingKeys.push(alert.key);
      this.$store.dispatch('resolveMonitorAlert', alert.key).finally(() => {
        this.resolvingKeys = this.resolvingKeys.filter(k => k !== alert.key);
      });
    },
    toggleTip(key) {
      this.openTipKey = this.openTipKey === key ? null : key;
    }
  }
}
</script>

<style scoped>
.mon-modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1060;
}

.mon-area {
  background: #FFFFFF;
  box-shadow: 2px 2px 20px 1px;
  display: flex;
  flex-direction: column;
  width: 640px;
  max-width: 90vw;
  max-height: 80vh;
  position: relative;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}

.mon-area-header {
  background-color: darkslategray;
  color: white;
  padding: 10px 14px;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mon-btn-close-x {
  border: none;
  background: transparent;
  color: white;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.mon-area-body {
  background-color: white;
  padding: 10px;
  overflow-y: auto;
  flex: 1 1 auto;
}

.mon-empty {
  padding: 20px;
  text-align: center;
  color: gray;
  font-size: small;
}

.mon-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  border-left: 6px solid gray;
  background-color: #f7f7f7;
  padding: 8px 10px;
  margin-bottom: 8px;
  border-radius: 4px;
}

.mon-row-main {
  flex: 1 1 auto;
  min-width: 0;
}

.mon-row-title {
  font-weight: bold;
  font-size: small;
}

.mon-row-body {
  font-size: small;
  white-space: normal;
  word-break: break-word;
}

.mon-row-detail {
  font-size: smaller;
  color: dimgray;
  font-style: italic;
}

.mon-row-actions {
  display: flex;
  flex-direction: row;
  gap: 6px;
  flex: 0 0 auto;
}

.mon-btn {
  border: 1px solid gray;
  border-radius: 6px;
  background-color: white;
  font-size: smaller;
  padding: 4px 8px;
  cursor: pointer;
  white-space: nowrap;
}
.mon-btn:hover {
  background-color: #e9e9e9;
}
.mon-btn:disabled {
  cursor: default;
  opacity: 0.6;
}

.mon-btn-resolve {
  border-color: darkgreen;
  color: darkgreen;
}
.mon-btn-resolve:hover:not(:disabled) {
  background-color: #eaffea;
}

.mon-recommend-wrap {
  position: relative;
  display: inline-block;
}

.mon-recommend-popover {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background-color: #333;
  color: white;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  width: 260px;
  white-space: pre-wrap;
  z-index: 10;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  text-align: left;
}

.mon-area-footer {
  background-color: darkslategray;
  padding: 10px 14px;
  display: flex;
  justify-content: flex-end;
}

.mon-btn-footer-close {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 16px;
  cursor: pointer;
  font-weight: bold;
  color: #4AAE9B;
  background: transparent;
  width: 120px;
  height: auto;
}
.mon-btn-footer-close:hover {
  background: darkgreen;
}
</style>
