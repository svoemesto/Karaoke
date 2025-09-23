<template>
  <div class="songstable">
    <SongsTable/>
  </div>
</template>

<script>
// @ is an alias to /src
import SongsTable from '@/components/Songs/SongsTable.vue'

export default {
  name: 'SongView',
  components: {
    SongsTable
  }
}
</script>

<style scoped>

.songstable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>