<template>
  <transition name="modal-fade">
    <div class="picfm-modal-backdrop">
      <div class="picfm-area">

        <div class="picfm-area-modal-header">
          Фильтр для настроек
        </div>

        <div class="picfm-area-modal-body">
          <div class="picfm-root-wrapper">

            <div class="picfm-filter-row">
              <div class="picfm-row-label">
                <div v-text="'ID:'"></div>
              </div>
              <div class="picfm-row-input">
                <input class="picfm-input-field" v-model="picturesFilterId">
              </div>
              <button :disabled="!picturesFilterId" class="picfm-button-clear-field" @click.left="picturesFilterId=''" v-text="'X'"></button>
            </div>

            <div class="picfm-filter-row">
              <div class="picfm-row-label">
                <div v-text="'Имя:'"></div>
              </div>
              <div class="picfm-row-input">
                <input class="picfm-input-field" v-model="picturesFilterName">
              </div>
              <button :disabled="!picturesFilterName" class="picfm-button-clear-field" @click.left="picturesFilterName=''" v-text="'X'"></button>
            </div>

          </div>
        </div>

        <div class="picfm-area-modal-footer">
          <button type="button" class="picfm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="picfm-btn-close" @click="cancel">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>

export default {
  name: "PicturesFilterModal",
  async beforeMount() {
    this.$store.dispatch('setPicturesFilterId', { value: await this.$store.getters.getWebvueProp('picturesFilterId', '') });
    this.$store.dispatch('setPicturesFilterName', { value: await this.$store.getters.getWebvueProp('picturesFilterName', '') });
  },
  computed: {
    picturesFilterId: {
      get() { return this.$store.getters.getPicturesFilterId; },
      set(value) { this.$store.dispatch('setPicturesFilterId', { value: value }); }
    },
    picturesFilterName: {
      get() { return this.$store.getters.getPicturesFilterName; },
      set(value) { this.$store.dispatch('setPicturesFilterName', { value: value }); }
    }
  },
  methods: {
    ok() {
      this.$store.dispatch('setPicturesFilterId', { value: this.picturesFilterId });
      this.$store.dispatch('setPicturesFilterName', { value: this.picturesFilterName });

      let params = {};
      if (this.picturesFilterId) params.filterId = this.picturesFilterId;
      if (this.picturesFilterName) params.filterName = this.picturesFilterName;
      this.$store.dispatch('loadPicturesDigests', params )

      this.$emit('close');
    },
    cancel() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.picfm-modal-fade-enter,
.picfm-modal-fade-leave-active {
  opacity: 0;
}

.picfm-modal-fade-enter-active,
.picfm-modal-fade-leave-active {
  transition: opacity .5s ease
}

.picfm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.picfm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.picfm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.picfm-modal-backdrop {
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

.picfm-area {
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

.picfm-btn-close {
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

.picfm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.picfm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.picfm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.picfm-row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.picfm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}

.picfm-input-field {
  border-radius: 5px;
  width: fit-content;
}

.picfm-input-field:hover {
  background-color: lightyellow;
}
.picfm-input-field:focus {
  background-color: cyan;
}

</style>