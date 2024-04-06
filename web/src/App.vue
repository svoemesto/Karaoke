<!--<script src="./assets/js/jquery-3.6.0.min.js"></script>-->
<!--<script src="./assets/js/sockjs.js"></script>-->
<!--<script src="./assets/js/stomp.js"></script>-->
<!--<script>-->
<!--let stompClientSettings = null;-->
<!--function connect() {-->
<!--  let socketSettings = new SockJS('/apis/message');-->
<!--  stompClientSettings = Stomp.over(socketSettings);-->
<!--  stompClientSettings.connect({}, function (frame) {-->
<!--    console.log('Connected: ' + frame);-->
<!--    stompClientSettings.subscribe('/apis/messages/recordchange', function (recordchange) {-->
<!--      console.log("Получено сообщение: ", recordchange.body);-->
<!--    });-->
<!--  });-->
<!--}-->

<!--$(function () {-->
<!--  connect();-->
<!--});-->

<!--</script>-->

<template>
  <div id="app">
    <ul class="nav nav-pills">
      <li class="nav-item"><router-link to="/">Главная</router-link></li>
      <li class="nav-item"><router-link to="/songs">Песни</router-link></li>
      <li class="nav-item"><router-link to="/publications">Публикации</router-link></li>
      <li class="nav-item"><router-link to="/unpublications">Un-Публикации</router-link></li>
      <li class="nav-item"><router-link to="/zakroma">Закрома</router-link></li>
      <li class="nav-item"><router-link to="/processes">Процессы</router-link></li>
    </ul>
    <router-view/>
  </div>
</template>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  font-weight: 300;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
}

.nav-item {
  /*display: block;*/
  padding-right: 5px;
  padding-left: 5px;
}
nav a {
  font-weight: bold;
  color: #2c3e50;
}

.router-link-exact-active {
  text-decoration: none;
  color: darkblue;
  font-weight: bold;
  border: solid 1px red;
  border-radius: 2px;
  padding: 2px;
  background-color: lightpink;
}
</style>
