<template>
  <div class="songs">
    <SongsTable/>
    <SongEdit/>
  </div>
</template>

<script>
// @ is an alias to /src
import SongsTable from '@/components/SongsTable'
import SongEdit from '@/components/SongEdit'

export default {
  name: 'HomeView',
  components: {
    SongsTable,
    SongEdit
  }
}
</script>

<style scoped>

  .songs {
    padding: 0;
    display: grid;
    justify-content: start;
    column-gap: 10px;
    grid-template-columns: auto auto;
    min-height: calc(100vh - 40px);
    margin: 0;
  }
</style>