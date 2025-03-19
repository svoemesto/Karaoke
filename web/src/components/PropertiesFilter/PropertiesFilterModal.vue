<template>
  <transition name="modal-fade">
    <div class="modal-backdrop">
      <div class="area">

        <div class="area-modal-header">
          Фильтр для настроек
        </div>

        <div class="area-modal-body">
          <div class="root-wrapper">

            <div class="filter-row">
              <div class="row-label">
                <div v-text="'KEY:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="propertiesFilterKey">
              </div>
              <button :disabled="!propertiesFilterKey" class="button-clear-field" @click.left="propertiesFilterKey=''" v-text="'X'"></button>
            </div>

            <div class="filter-row">
              <div class="row-label">
                <div v-text="'Значение:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="propertiesFilterValue">
              </div>
              <button :disabled="!propertiesFilterValue" class="button-clear-field" @click.left="propertiesFilterValue=''" v-text="'X'"></button>
            </div>

            <div class="filter-row">
              <div class="row-label">
                <div v-text="'По-умолчанию:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="propertiesFilterDefaultValue">
              </div>
              <button :disabled="!propertiesFilterDefaultValue" class="button-clear-field" @click.left="propertiesFilterDefaultValue=''" v-text="'X'"></button>
            </div>

            <div class="filter-row">
              <div class="row-label">
                <div v-text="'Описание:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="propertiesFilterDescription">
              </div>
              <button :disabled="!propertiesFilterDescription" class="button-clear-field" @click.left="propertiesFilterDescription=''" v-text="'X'"></button>
            </div>

            <div class="filter-row">
              <div class="row-label">
                <div v-text="'Тип:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="propertiesFilterType">
              </div>
              <button :disabled="!propertiesFilterType" class="button-clear-field" @click.left="propertiesFilterType=''" v-text="'X'"></button>
            </div>

          </div>
        </div>

        <div class="area-modal-footer">
          <button type="button" class="btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="btn-close" @click="cancel">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>

export default {
  name: "PropertiesFilterModal",
  data() {
    return {
      propertiesFilterKey: this.$store.getters.getPropertiesFilterKey,
      propertiesFilterValue: this.$store.getters.getPropertiesFilterValue,
      propertiesFilterDefaultValue: this.$store.getters.getPropertiesFilterDefaultValue,
      propertiesFilterDescription: this.$store.getters.getPropertiesFilterDescription,
      propertiesFilterType: this.$store.getters.getPropertiesFilterType
    }
  },
  methods: {
    ok() {
      this.$store.dispatch('setPropertiesFilterKey', { propertiesFilterKey: this.propertiesFilterKey });
      this.$store.dispatch('setPropertiesFilterValue', { propertiesFilterValue: this.propertiesFilterValue });
      this.$store.dispatch('setPropertiesFilterDefaultValue', { propertiesFilterDefaultValue: this.propertiesFilterDefaultValue });
      this.$store.dispatch('setPropertiesFilterDescription', { propertiesFilterDescription: this.propertiesFilterDescription });
      this.$store.dispatch('setPropertiesFilterType', { propertiesFilterType: this.propertiesFilterType });

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

.modal-fade-enter,
.modal-fade-leave-active {
  opacity: 0;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity .5s ease
}

.area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
}

.area {
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

.btn-close {
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

.root-wrapper {
  display: flex;
  flex-direction: column;
}
.button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}

.input-field {
  border-radius: 5px;
  width: fit-content;
}

.input-field:hover {
  background-color: lightyellow;
}
.input-field:focus {
  background-color: cyan;
}

</style>