<template>
  <transition name="modal-fade">
    <div class="sem-modal-backdrop">
      <div class="sem-area">

        <div class="sem-area-modal-header">
          Редактирование песни
        </div>
        <div class="sem-area-modal-body">
          <SongEdit @close="close"/>
        </div>
        <div class="sem-area-modal-footer">
          <button type="button" class="sem-btn-close" @click="close">Выход</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>
import SongEdit from "@/components/Songs/edit/SongEdit.vue";

export default {
  name: "SongEditModal",
  components: {SongEdit},
  methods: {
    close() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.sem-modal-fade-enter,
.sem-modal-fade-leave-active {
  opacity: 0;
}

.sem-modal-fade-enter-active,
.sem-modal-fade-leave-active {
  transition: opacity .5s ease
}

.sem-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.sem-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.sem-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: flex-end;
}

.sem-modal-backdrop {
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

.sem-area {
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

.sem-btn-close {
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