<template>
  <div class="songsbv">
    <SongsBvTable/>
  </div>
</template>

<script>
// @ is an alias to /src
import SongsBvTable from '@/components/SongsBv/SongsBvTable.vue'

export default {
  name: 'SongBvView',
  components: {
    SongsBvTable
  }
}
</script>

<style scoped>

.songsbv {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>