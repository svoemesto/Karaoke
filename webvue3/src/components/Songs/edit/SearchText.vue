<template>
  <transition name="modal-fade">
    <div class="st-modal-backdrop">
      <div class="st-area">
        <div class="st-body">
          <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
          <div class="header">
            
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>

export default {
  name: "SearchText",
  components: {
    CustomConfirm
  },
  data() {
    return {
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
    }
  },
  props: {
    songId: {
      type: String,
      required: true
    }
  },
  methods: {
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    }
  }
}

</script>

<style scoped>

.st-modal-backdrop {
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

.st-area {
  background: #FFFFFF;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: calc(100vw - 50px);
  height: calc(100vh - 50px);
  position: relative;
}

.st-body {
  width: 100%;
  height: 100%;
  font-size: 14px;
  font-family: sans-serif;
  display: grid;
  gap: 5px;
  background-color: #ddd;
  grid-template-columns: 300px 100px 1fr;
  grid-template-rows: 50px 271px 50px 50px 1fr 50px;
}

</style>