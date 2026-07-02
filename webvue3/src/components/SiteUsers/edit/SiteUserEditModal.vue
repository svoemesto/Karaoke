<template>
  <transition name="modal-fade">
    <div class="suem-modal-backdrop">
      <div class="suem-area">
        <div class="suem-area-modal-header">Редактирование пользователя сайта</div>
        <div class="suem-area-modal-body">
          <SiteUserEdit @close="close" />
        </div>
        <div class="suem-area-modal-footer">
          <button type="button" class="suem-btn-close" @click="close">Выход</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import SiteUserEdit from "./SiteUserEdit.vue";

export default {
  name: "SiteUserEditModal",
  components: { SiteUserEdit },
  methods: {
    close() { this.$emit('close') }
  }
}
</script>

<style scoped>
.suem-modal-fade-enter,
.suem-modal-fade-leave-active { opacity: 0; }
.suem-modal-fade-enter-active,
.suem-modal-fade-leave-active { transition: opacity .5s ease }

.suem-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}
.suem-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}
.suem-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  display: flex;
  justify-content: flex-end;
}
.suem-modal-backdrop {
  position: fixed;
  top: 0; bottom: 0; left: 0; right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1055;
}
.suem-area {
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
.suem-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 20px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 100px;
}
</style>
