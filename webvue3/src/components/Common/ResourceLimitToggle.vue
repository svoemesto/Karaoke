<template>
  <button
    class="btn-round-double"
    :title="
      enabled
        ? 'Ограничение CPU включено — нажмите, чтобы снять ограничение'
        : 'Ограничение CPU выключено (безлимит) — нажмите, чтобы включить ограничение'
    "
    @click.left="toggle"
  >
    <span v-if="enabled" class="icon-40 icon-emoji">🐢</span>
    <span v-else class="icon-40 icon-emoji">🐇</span>
  </button>
</template>

<script>
const PROPERTY_KEY = 'resourceLimitsEnabled'

export default {
  name: 'ResourceLimitToggle',
  data() {
    return {
      enabled: false,
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    load() {
      this.$store.dispatch('getPropertyValuePromise', PROPERTY_KEY).then((property) => {
        this.enabled = property.value === 'true'
      })
    },
    toggle() {
      const newValue = !this.enabled
      this.$store
        .dispatch('setPropertyValuePromise', {
          propertyKey: PROPERTY_KEY,
          propertyValue: String(newValue),
        })
        .then(() => {
          this.enabled = newValue
        })
    },
  },
}
</script>

<style scoped>
.btn-round-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin: 0;
  background-color: antiquewhite;
}
.btn-round-double:hover {
  background-color: lightpink;
}
.btn-round-double:focus {
  background-color: darksalmon;
}
.icon-40 {
  width: 40px;
  height: 40px;
}
.icon-emoji {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  line-height: 1;
}
</style>
