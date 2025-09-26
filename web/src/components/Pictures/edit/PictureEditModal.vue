<template>
  <transition name="modal-fade">
    <div class="pem-modal-backdrop">
      <div class="pem-area">

        <div class="pem-area-modal-header">
          Редактирование картинки
        </div>
        <div class="pem-area-modal-body">
          <PictureEdit @close="close"/>
        </div>
        <div class="pem-area-modal-footer">
          <button type="button" class="pem-btn-close" @click="close">Выход</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>
import PictureEdit from "@/components/Pictures/edit/PictureEdit.vue";

export default {
  name: "PictureEditModal",
  components: {PictureEdit},
  methods: {
    close() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.pem-modal-fade-enter,
.pem-modal-fade-leave-active {
  opacity: 0;
}

.pem-modal-fade-enter-active,
.pem-modal-fade-leave-active {
  transition: opacity .5s ease
}

.pem-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.pem-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.pem-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: flex-end;
}

.pem-modal-backdrop {
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

.pem-area {
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

.pem-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 20px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 100px;
  height: auto;
}

</style>