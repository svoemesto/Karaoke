<template>
  <div id="app">
    <div class="app-header">
      <ul class="nav nav-pills">
        <li class="nav-item"><router-link to="/">Главная</router-link></li>
        <li class="nav-item"><router-link to="/songs">Песни</router-link></li>
        <li class="nav-item"><router-link to="/publish">Публикации</router-link></li>
        <li class="nav-item"><router-link to="/authors">Авторы</router-link></li>
        <li class="nav-item"><router-link to="/processes">Процессы</router-link></li>
        <li class="nav-item"><router-link to="/properties">Настройки</router-link></li>
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

.b-toaster.b-toaster-top-left {
  top: 50px;
  left: 10px;
}

.toast-header-info {
  color: #fff;
  background-color: rgb(50 50 255 / 85%)
}
.toast-body-info {
  padding: .75rem;
  color: #000;
  background-color: rgb(200 200 255 / 85%);
}

</style>
<script setup>
import ProcessWorker from "@/components/Common/ProcessWorker.vue";
import BackendConsole from "@/components/Common/BackendConsole.vue";

</script>