<template>
  <div class="results-table-body">
    <div
v-for="searchResult in searchResultsList"
      :key="searchResult.id"
      class="results-table-row">
      <div
        class="fld-url"
        :style="{ backgroundColor: currentId === searchResult.id ? 'blue' : searchResult.text === '' ? 'gray' : 'white' }"
        @click.left="returnRearchResult(searchResult)"
        v-text="searchResult.url"
        />

    </div>
  </div>
</template>

<script>

export default {
  name: "SearchTextResultsTable",
  components: {},
  props: {
    searchResultsList: {
      type: Array,
      required: true
    }
  },
  data() {
    return {
      currentId: undefined
    }
  },
  computed: {},
  watch: {},
  methods: {
    onRowClicked(item, index) {
      console.log(`Row '${index}' clicked: `, item.url);
    },
    returnRearchResult(searchResult) {
      console.log(`returnRearchResult: `, searchResult);
      this.currentId = searchResult.id;
      this.$emit('selectedResult', searchResult);
    },
  }
}
</script>

<style scoped>

.results-table-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  /* width: fit-content; */
}

.fld-url {
  min-width: 350px;
  max-width: 350px;
  text-align: left;
  font-size: small;
  cursor: default;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}
.fld-url:hover {
  text-decoration: underline;
  cursor: pointer;
}


.results-table-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}

</style>