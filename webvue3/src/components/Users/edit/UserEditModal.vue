<template>
  <transition name="modal-fade">
    <div class="uem-modal-backdrop">
      <div class="uem-area">

        <div class="uem-area-modal-header">
          Редактирование пользователя
        </div>
        <div class="uem-area-modal-body">
          <UserEdit @close="close"/>
        </div>
        <div class="uem-area-modal-footer">
          <button type="button" class="uem-btn-close" @click="close">Выход</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>
import UserEdit from "../../../components/Users/edit/UserEdit.vue";

export default {
  name: "UserEditModal",
  components: {UserEdit},
  methods: {
    close() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.uem-modal-fade-enter,
.uem-modal-fade-leave-active {
  opacity: 0;
}

.uem-modal-fade-enter-active,
.uem-modal-fade-leave-active {
  transition: opacity .5s ease
}

.uem-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.uem-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.uem-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: flex-end;
}

.uem-modal-backdrop {
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

.uem-area {
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

.uem-btn-close {
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