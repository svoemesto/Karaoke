<template>
  <div class="songs">
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

  .songs {
    display: flex;
    flex-direction: column;
    margin: 10px auto;
    justify-content: center;
    align-items: center;
  }
</style>