<template>
  <div class="results-table-body">
    <div class="results-table-row"
      v-for="searchResult in searchResultsList"
      :key="searchResult.id">
      <div
        class="fld-url"
        v-text="searchResult.url"
        @click.left="returnRearchResult(searchResult)"
        :style="{ backgroundColor: searchResult.text === '' ? 'gray' : currentId === searchResult.id ? 'blue' : 'white' }"
        >
      </div>

    </div>
  </div>
</template>

<script>

export default {
  name: "SearchTextResultsTable",
  components: {},
  data() {
    return {
      currentId: undefined
    }
  },
  watch: {},
  props: {
    searchResultsList: {
      type: Array,
      required: true
    }
  },
  computed: {},
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