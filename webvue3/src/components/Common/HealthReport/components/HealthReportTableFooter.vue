<template>
  <div class="hrl-table-footer">
    <button type="button" class="hrl-table-footer-button-close" @click="close">Выход</button>
  </div>
</template>

<script>


export default {
  name: "HealthReportTableFooter",
  methods: {
    close() {
      this.$emit('close');
    }
  },

}

</script>

<style scoped>

.hrl-table-footer {
  width: fit-content;
}

.hrl-table-footer-button-close {
  min-width: 140px;
  max-width: 140px;
  text-align: center;
  padding: 0 3px;
  font-size: small;
}

</style>