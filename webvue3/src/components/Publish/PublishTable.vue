<template>
  <div class="publish-root">
    <PublishTableBody :publish-digest="publishDigest" />
    <PublishTableFooter />
  </div>
</template>

<script>

import PublishTableBody from "../../components/Publish/components/PublishTableBody.vue";
import PublishTableFooter from "../../components/Publish/components/PublishTableFooter.vue";

export default {
  name: "PublishTable",
  components: {PublishTableFooter, PublishTableBody},
  computed: {
    publishDigest() {
      return this.$store.getters.getPublishDigest
    },
  }
}
</script>

<style scoped>

.publish-root {
  display: flex;
  flex-direction: column;
  width: calc(100vw - 50px);
  height: calc(100vh - 95px);
}

</style>