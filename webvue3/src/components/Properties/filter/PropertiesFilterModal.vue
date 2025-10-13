<template>
  <transition name="modal-fade">
    <div class="pfm-modal-backdrop">
      <div class="pfm-area">

        <div class="pfm-area-modal-header">
          Фильтр для настроек
        </div>

        <div class="pfm-area-modal-body">
          <div class="pfm-root-wrapper">

            <div class="pfm-filter-row">
              <div class="pfm-row-label">
                <div v-text="'KEY:'"></div>
              </div>
              <div class="pfm-row-input">
                <input class="pfm-input-field" v-model="propertiesFilterKey">
              </div>
              <button :disabled="!propertiesFilterKey" class="pfm-button-clear-field" @click.left="propertiesFilterKey=''" v-text="'X'"></button>
            </div>

            <div class="pfm-filter-row">
              <div class="pfm-row-label">
                <div v-text="'Значение:'"></div>
              </div>
              <div class="pfm-row-input">
                <input class="pfm-input-field" v-model="propertiesFilterValue">
              </div>
              <button :disabled="!propertiesFilterValue" class="pfm-button-clear-field" @click.left="propertiesFilterValue=''" v-text="'X'"></button>
            </div>

            <div class="pfm-filter-row">
              <div class="pfm-row-label">
                <div v-text="'По-умолчанию:'"></div>
              </div>
              <div class="pfm-row-input">
                <input class="pfm-input-field" v-model="propertiesFilterDefaultValue">
              </div>
              <button :disabled="!propertiesFilterDefaultValue" class="pfm-button-clear-field" @click.left="propertiesFilterDefaultValue=''" v-text="'X'"></button>
            </div>

            <div class="pfm-filter-row">
              <div class="pfm-row-label">
                <div v-text="'Описание:'"></div>
              </div>
              <div class="pfm-row-input">
                <input class="pfm-input-field" v-model="propertiesFilterDescription">
              </div>
              <button :disabled="!propertiesFilterDescription" class="pfm-button-clear-field" @click.left="propertiesFilterDescription=''" v-text="'X'"></button>
            </div>

            <div class="pfm-filter-row">
              <div class="pfm-row-label">
                <div v-text="'Тип:'"></div>
              </div>
              <div class="pfm-row-input">
                <input class="pfm-input-field" v-model="propertiesFilterType">
              </div>
              <button :disabled="!propertiesFilterType" class="pfm-button-clear-field" @click.left="propertiesFilterType=''" v-text="'X'"></button>
            </div>

          </div>
        </div>

        <div class="pfm-area-modal-footer">
          <button type="button" class="pfm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="pfm-btn-close" @click="cancel">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>

export default {
  name: "PropertiesFilterModal",
  async beforeMount() {
    this.$store.dispatch('setPropertiesFilterKey', { value: await this.$store.getters.getWebvueProp('propertiesFilterKey', '') });
    this.$store.dispatch('setPropertiesFilterValue', { value: await this.$store.getters.getWebvueProp('propertiesFilterValue', '') });
    this.$store.dispatch('setPropertiesFilterDefaultValue', { value: await this.$store.getters.getWebvueProp('propertiesFilterDefaultValue', '') });
    this.$store.dispatch('setPropertiesFilterDescription', { value: await this.$store.getters.getWebvueProp('propertiesFilterDescription', '') });
    this.$store.dispatch('setPropertiesFilterType', { value: await this.$store.getters.getWebvueProp('propertiesFilterType', '') });
  },
  computed: {
    propertiesFilterKey: {
      get() { return this.$store.getters.getPropertiesFilterKey; },
      set(value) { this.$store.dispatch('setPropertiesFilterKey', { value: value }); }
    },
    propertiesFilterValue: {
      get() { return this.$store.getters.getPropertiesFilterValue; },
      set(value) { this.$store.dispatch('setPropertiesFilterValue', { value: value }); }
    },
    propertiesFilterDefaultValue: {
      get() { return this.$store.getters.getPropertiesFilterDefaultValue; },
      set(value) { this.$store.dispatch('setPropertiesFilterDefaultValue', { value: value }); }
    },
    propertiesFilterDescription: {
      get() { return this.$store.getters.getPropertiesFilterDescription; },
      set(value) { this.$store.dispatch('setPropertiesFilterDescription', { value: value }); }
    },
    propertiesFilterType: {
      get() { return this.$store.getters.getPropertiesFilterType; },
      set(value) { this.$store.dispatch('setPropertiesFilterType', { value: value }); }
    },
  },
  methods: {
    ok() {
      this.$store.dispatch('setPropertiesFilterKey', { value: this.propertiesFilterKey });
      this.$store.dispatch('setPropertiesFilterValue', { value: this.propertiesFilterValue });
      this.$store.dispatch('setPropertiesFilterDefaultValue', { value: this.propertiesFilterDefaultValue });
      this.$store.dispatch('setPropertiesFilterDescription', { value: this.propertiesFilterDescription });
      this.$store.dispatch('setPropertiesFilterType', { value: this.propertiesFilterType });

      let params = {};
      if (this.propertiesFilterKey) params.filter_key = this.propertiesFilterKey;
      if (this.propertiesFilterValue) params.filter_value = this.propertiesFilterValue;
      if (this.propertiesFilterDefaultValue) params.filter_default_value = this.propertiesFilterDefaultValue;
      if (this.propertiesFilterDescription) params.filter_description = this.propertiesFilterDescription;
      if (this.propertiesFilterType) params.filter_type = this.propertiesFilterType;
      this.$store.dispatch('loadPropertiesDigests', params )

      this.$emit('close');
    },
    cancel() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.pfm-modal-fade-enter,
.pfm-modal-fade-leave-active {
  opacity: 0;
}

.pfm-modal-fade-enter-active,
.pfm-modal-fade-leave-active {
  transition: opacity .5s ease
}

.pfm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.pfm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.pfm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.pfm-modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1055;
}

.pfm-area {
  background: #FFFFFF;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: auto;
  height: auto;
  position: relative;
  max-width: calc(100vw - 20px);
  max-height: calc(100vh - 20px);
}

.pfm-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 150px;
  height: auto;
  font-size: small;
}

.pfm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.pfm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.pfm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.pfm-row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.pfm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}

.pfm-input-field {
  border-radius: 5px;
  width: fit-content;
}

.pfm-input-field:hover {
  background-color: lightyellow;
}
.pfm-input-field:focus {
  background-color: cyan;
}

</style>