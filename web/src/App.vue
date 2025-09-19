<template>
  <div id="app">
    <div class="app-header">
      <ul class="nav nav-pills">
        <li class="nav-item"><router-link to="/">Главная</router-link></li>
<!--        <li class="nav-item"><router-link to="/songs">Песни</router-link></li>-->
        <li class="nav-item"><router-link to="/songsbv">Песни</router-link></li>
        <li class="nav-item"><router-link to="/publish">Публикации</router-link></li>
        <li class="nav-item"><router-link to="/authorsbv">Авторы</router-link></li>
        <!--        <li class="nav-item"><router-link to="/publicationsbv">ПубликацииBV</router-link></li>-->
        <!--        <li class="nav-item"><router-link to="/publications">Публикации</router-link></li>-->
        <!--        <li class="nav-item"><router-link to="/unpublications">Un-Публикации</router-link></li>-->
        <!--        <li class="nav-item"><router-link to="/zakroma">Закрома</router-link></li>-->
        <!--        <li class="nav-item"><router-link to="/processes">Процессы</router-link></li>-->
        <li class="nav-item"><router-link to="/processesbv">Процессы</router-link></li>
        <li class="nav-item"><router-link to="/propertiesbv">Настройки</router-link></li>
      </ul>
      <backend-console/>
      <process-worker/>
    </div>
    <router-view/>
  </div>
</template>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif !important;
  font-weight: 300 !important;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
}

.app-header {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
}
/*.nav-item {*/
/*  padding: 5px;*/
/*  border: thin solid darkslategray;*/
/*  border-radius: 1rem;*/
/*  background-color: aquamarine;*/
/*  color: blue;*/
/*  text-decoration: none;*/
/*  margin: 0 5px;*/
/*  width: 120px;*/
/*}*/
.nav-item:hover {
  text-decoration: none;
  background-color: #4AAE9B;
}
.nav {
  display: flex;
  justify-content: center;
}
nav a {
  font-weight: bold;
  color: #2c3e50;
}

li a {
  padding: 0 5px 0 5px;
}
.router-link-active {
  padding: 0 5px 0 5px;
}
.router-link-exact-active {
  /*text-decoration: none;*/
  color: red;
  font-weight: bold;
  text-decoration: none;
  /*border: solid 1px red;*/
  /*border-radius: 2px;*/
  /*padding: 2px;*/
  /*background-color: lightpink;*/
}

.table-sm th {
  font-size: small !important;
}
.table-sm td, .table-sm th {
  padding: 0 !important;
}
.table-sm tr:hover {
  font-weight: bold !important;
}



.b-toaster {
  position: absolute;
  top: 50px;
  left: 10px;
}
.toast {
  opacity: 1;
  animation-duration: 0.3s !important;
}

.toast.fade-enter-active,
.toast.fade-leave-active {
  transition: opacity 0.3s, transform 0.3s !important;
}

.toast.fade-enter {
  opacity: 0;
  transform: translateY(-20px);
}

.toast.fade-leave-to {
  opacity: 0;
  transform: translateX(100px);
}

/* Предотвращение моргания */
.toast:not(.show) {
  display: none !important;
}

.toast-header {
  color: #fff;
  background-color: rgb(50 50 255 / 85%)
}
.toast-body {
  padding: .75rem;
  color: #000;
  background-color: rgb(200 200 255 / 85%);
}

/* Стилизация заголовка и кнопки закрытия */
.custom-toast-header {
  background-color: rgb(50 50 255 / 85%) !important;
  color: white !important;
}

/* Стилизация кнопки закрытия */
.custom-toast-header .close {
  color: white !important;
  opacity: 1 !important;
  text-shadow: none !important;
}

.custom-toast-header .close:hover {
  color: #f8f9fa !important;
  opacity: 0.8 !important;
}

.custom-header-with-icon .close {
  background: none !important;
  border: none !important;
  font-size: 1.3rem !important;
  font-weight: normal !important;
  color: #17a2b8 !important;
  opacity: 1 !important;
  padding: 0 !important;
  width: 24px !important;
  height: 24px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.custom-header-with-icon .close:hover {
  background-color: rgba(23, 162, 184, 0.1) !important;
  border-radius: 50% !important;
  color: #0f6674 !important;
}


</style>
<script setup>
import ProcessWorker from "@/components/Common/ProcessWorker.vue";
import BackendConsole from "@/components/Common/BackendConsole.vue";

</script>