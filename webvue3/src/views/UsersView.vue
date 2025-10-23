<template>
  <div class="usersstable">
    <UsersTable/>
  </div>
</template>

<script>
import UsersTable from '../components/Users/UsersTable.vue'
export default {
  name: "UsersView",
  components: {
    UsersTable
  }
}
</script>

<style scoped>

.userstable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}

</style>