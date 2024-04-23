<template>
  <div class="songs">
    <SongsTable/>
  </div>
</template>

<script>
// @ is an alias to /src
import SongsTable from '@/components/SongsTable'

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
    /*max-width: calc(100vw - 20px);*/
    /*max-height: calc(100vh - 50px);*/
    margin: 10px auto;
    justify-content: center;
    align-items: center;
  }
</style>