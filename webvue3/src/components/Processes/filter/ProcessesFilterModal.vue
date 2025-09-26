<template>
  <transition name="modal-fade">
    <div class="prfm-modal-backdrop">
      <div class="prfm-area">

        <div class="prfm-area-modal-header">
          Фильтр для песен
        </div>

        <div class="prfm-area-modal-body">
          <div class="prfm-root-wrapper">

            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'ID:'"></div>
              </div>
              <div class="prfm-row-input">
                <input class="prfm-input-field" v-model="processesFilterId">
              </div>
              <button :disabled="!processesFilterId" class="prfm-button-clear-field" @click.left="processesFilterId=''" v-text="'X'"></button>
            </div>

            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'Имя:'"></div>
              </div>
              <div class="prfm-row-input">
                <input class="prfm-input-field" v-model="processesFilterName">
              </div>
              <button :disabled="!processesFilterName" class="prfm-button-clear-field" @click.left="processesFilterName=''" v-text="'X'"></button>
            </div>

            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'Статус:'"></div>
              </div>
              <div class="prfm-row-input">
                <input class="prfm-input-field" v-model="processesFilterStatus">
              </div>
              <button :disabled="!processesFilterStatus" class="prfm-button-clear-field" @click.left="processesFilterStatus=''" v-text="'X'"></button>
            </div>

            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'Приоритет:'"></div>
              </div>
              <div class="prfm-row-input">
                <input class="prfm-input-field" v-model="processesFilterPriority">
              </div>
              <button :disabled="!processesFilterPriority" class="prfm-button-clear-field" @click.left="processesFilterPriority=''" v-text="'X'"></button>
            </div>

            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'Описание:'"></div>
              </div>
              <div class="prfm-row-input">
                <input class="prfm-input-field" v-model="processesFilterDescription">
              </div>
              <button :disabled="!processesFilterDescription" class="prfm-button-clear-field" @click.left="processesFilterDescription=''" v-text="'X'"></button>
            </div>

            <div class="prfm-filter-row">
              <div class="prfm-row-label">
                <div v-text="'Тип:'"></div>
              </div>
              <div class="prfm-row-input">
                <input class="prfm-input-field" v-model="processesFilterType">
              </div>
              <button :disabled="!processesFilterType" class="prfm-button-clear-field" @click.left="processesFilterType=''" v-text="'X'"></button>
            </div>

          </div>
        </div>

        <div class="prfm-area-modal-footer">
          <button type="button" class="prfm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="prfm-btn-close" @click="cancel">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>

export default {
  name: "ProcessesFilterModal",
  data() {
    return {
      processesFilterId: this.$store.getters.getProcessesFilterId,
      processesFilterName: this.$store.getters.getProcessesFilterName,
      processesFilterStatus: this.$store.getters.getProcessesFilterStatus,
      processesFilterPriority: this.$store.getters.getProcessesFilterPriority,
      processesFilterDescription: this.$store.getters.getProcessesFilterDescription,
      processesFilterType: this.$store.getters.getProcessesFilterType
    }
  },
  methods: {
    ok() {
      this.$store.dispatch('setProcessesFilterId', { processesFilterId: this.processesFilterId });
      this.$store.dispatch('setProcessesFilterName', { processesFilterName: this.processesFilterName });
      this.$store.dispatch('setProcessesFilterStatus', { processesFilterStatus: this.processesFilterStatus });
      this.$store.dispatch('setProcessesFilterPriority', { processesFilterPriority: this.processesFilterPriority });
      this.$store.dispatch('setProcessesFilterDescription', { processesFilterDescription: this.processesFilterDescription });
      this.$store.dispatch('setProcessesFilterType', { processesFilterType: this.processesFilterType });

      let params = {};
      if (this.processesFilterId) params.filter_id = this.processesFilterId;
      if (this.processesFilterName) params.filter_name = this.processesFilterName;
      if (this.processesFilterStatus) params.filter_status = this.processesFilterStatus;
      if (this.processesFilterPriority) params.filter_priority = this.processesFilterPriority;
      if (this.processesFilterDescription) params.filter_description = this.processesFilterDescription;
      if (this.processesFilterType) params.filter_type = this.processesFilterType;
      params.filter_notail = 'true'
      this.$store.dispatch('loadProcessesDigests', params )

      this.$emit('close');
    },
    cancel() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.prfm-modal-fade-enter,
.prfm-modal-fade-leave-active {
  opacity: 0;
}

.prfm-modal-fade-enter-active,
.prfm-modal-fade-leave-active {
  transition: opacity .5s ease
}

.prfm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.prfm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.prfm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.prfm-modal-backdrop {
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

.prfm-area {
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

.prfm-btn-close {
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

.prfm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.prfm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.prfm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.prfm-row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.prfm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}

.prfm-input-field {
  border-radius: 5px;
  width: fit-content;
}

.prfm-input-field:hover {
  background-color: lightyellow;
}
.prfm-input-field:focus {
  background-color: cyan;
}

</style>