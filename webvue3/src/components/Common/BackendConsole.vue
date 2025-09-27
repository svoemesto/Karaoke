<template>
  <div class="backend_console">
      <textarea class="console_log" v-model="logText"></textarea>
  </div>
</template>

<script>
export default {
  name: "BackendConsole",
  computed: {
    logText() {
      return this.$store.getters.getLogText
    }
  }
}
</script>

<style scoped>

.backend_console {
  display: flex;
  margin: 0 10px;
}
.console_log {
  height: 51px;
  width: 1200px;
  background-color: darkblue;
  color: snow;
  font-size: x-small;
}
</style>