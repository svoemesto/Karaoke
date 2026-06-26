<template>
  <div v-if="!isHomePage" id="nonHomeScreen">
    <div id="nonHomeBody">
      <router-view />
    </div>
  </div>
  <router-view v-else />
</template>

<script>
export default {
  name: 'App',
  computed: {
    isHomePage() {
      return this.$route.path === '/'
    }
  }
}
</script>

<style>
#nonHomeScreen {
  padding: 1em;
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  margin: 0;
}
#nonHomeBody {
  align-self: center;
  padding: 5px;
  display: flex;
}
</style>
