<template>
  <transition name="modal-fade">
    <div class="dfm-modal-backdrop">
      <div class="dfm-area">

        <div class="dfm-area-modal-header">
          Фильтр для словарей
        </div>

        <div class="dfm-area-modal-body">
          <div class="dfm-root-wrapper">

            <div class="dfm-filter-row">
              <div class="dfm-row-label">
                <div v-text="'Словарь:'"/>
              </div>
              <div class="dfm-row-input">
                <select v-model="dictionariesFilterDictName" class="dfm-input-field">
                  <option value="">(все)</option>
                  <option v-for="name in dictNames" :key="name" :value="name" v-text="name"/>
                </select>
              </div>
              <button :disabled="!dictionariesFilterDictName" class="dfm-button-clear-field" @click.left="dictionariesFilterDictName=''" v-text="'X'"/>
            </div>

            <div class="dfm-filter-row">
              <div class="dfm-row-label">
                <div v-text="'Значение:'"/>
              </div>
              <div class="dfm-row-input">
                <input v-model="dictionariesFilterDictValue" class="dfm-input-field"/>
              </div>
              <button :disabled="!dictionariesFilterDictValue" class="dfm-button-clear-field" @click.left="dictionariesFilterDictValue=''" v-text="'X'"/>
            </div>

          </div>
        </div>

        <div class="dfm-area-modal-footer">
          <button type="button" class="dfm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="dfm-btn-close" @click="cancel">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>

export default {
  name: "DictionariesFilterModal",
  computed: {
    dictNames() {
      return this.$store.getters.getDictNames;
    },
    dictionariesFilterDictName: {
      get() { return this.$store.getters.getDictionariesFilterDictName; },
      set(value) { this.$store.dispatch('setDictionariesFilterDictName', { value: value }); }
    },
    dictionariesFilterDictValue: {
      get() { return this.$store.getters.getDictionariesFilterDictValue; },
      set(value) { this.$store.dispatch('setDictionariesFilterDictValue', { value: value }); }
    },
  },
  async beforeMount() {
    await this.$store.dispatch('loadDictNames');
    this.$store.dispatch('setDictionariesFilterDictName', { value: await this.$store.getters.getWebvueProp('dictionariesFilterDictName', '') });
    this.$store.dispatch('setDictionariesFilterDictValue', { value: await this.$store.getters.getWebvueProp('dictionariesFilterDictValue', '') });
  },
  methods: {
    ok() {
      this.$store.dispatch('setDictionariesFilterDictName', { value: this.dictionariesFilterDictName });
      this.$store.dispatch('setDictionariesFilterDictValue', { value: this.dictionariesFilterDictValue });

      let params = {};
      if (this.dictionariesFilterDictName) params.dictName = this.dictionariesFilterDictName;
      if (this.dictionariesFilterDictValue) params.dictValue = this.dictionariesFilterDictValue;
      this.$store.dispatch('loadDictionariesDigests', params);

      this.$emit('close');
    },
    cancel() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.dfm-modal-fade-enter,
.dfm-modal-fade-leave-active {
  opacity: 0;
}

.dfm-modal-fade-enter-active,
.dfm-modal-fade-leave-active {
  transition: opacity .5s ease
}

.dfm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.dfm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.dfm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.dfm-modal-backdrop {
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

.dfm-area {
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

.dfm-btn-close {
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

.dfm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.dfm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.dfm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.dfm-row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.dfm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}

/* Рамка/паддинг/фон/ШИРИНА заданы ЯВНО — поле сужено на 18px (10px под сдвиг кнопки
   margin-left:-10px + 8px видимого зазора), иначе круглая кнопка очистки наезжает на поле.
   Паттерн — как в SongsFilterModal.vue; select и input используют один и тот же класс, поэтому
   совпадение ширины гарантировано. */
.dfm-input-field {
  box-sizing: border-box;
  border: 1px solid #767676;
  border-radius: 5px;
  padding: 1px 4px;
  font: inherit;
  background-color: white;
  width: calc(100% - 18px);
}

.dfm-input-field:hover {
  background-color: lightyellow;
}
.dfm-input-field:focus {
  background-color: cyan;
}

select.dfm-input-field {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  cursor: pointer;
}

</style>
