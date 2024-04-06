<template>
  <div :style="styleWrapperTd">
    <datalist :id="datalistId">
      <option v-for="val in dict" :key="val" :value="val"/>
    </datalist>
    <td :style="styleTd">
      <div style="align-content: center">
        <div :style="styleVal">
          {{this.label}}
        </div>
      </div>
      <div>
        <input v-if="dict"
               :list="datalistId"
               :style="styleInput"
               :disabled="disabled"
               v-model="field"
               @change="$emit( 'change-field', name, field )"
               @keyup.enter="$emit( 'apply-filter' )"
        />
        <input v-else
               :style="styleInput"
               :disabled="disabled"
               v-model="field"
               @change="$emit( 'change-field', name, field )"
               @keyup.enter="$emit( 'apply-filter' )"
        />
      </div>
    </td>
  </div>
</template>

<script>
export default {
  name: "SongTableHeadTd",
  data() {
    return {
      field: this.fld
    }
  },
  props: {
    name: {
      type: String,
      required: true
    },
    position: {
      type: String,
      required: false
    },
    fld: {
      type: String,
      required: true
    },
    dict: {
      type: Array,
      required: false
    }
  },
  computed: {
    disabled() {
      return this.$store.getters.isChanged;
    },
    datalistId() {
      return 'datalistId'+ this.name;
    },
    width() {
      return this.$store.getters.getFieldParams(this.name).width+'px';
    },
    label() {
      return this.$store.getters.getFieldParams(this.name).label;
    },
    labelPadding() {
      return this.$store.getters.getFieldParams(this.name).labelPadding;
    },
    fontSize() {
      return this.$store.getters.getFieldParams(this.name).labelFontSize;
    },
    fontSizeFilter() {
      return this.$store.getters.getFieldParams(this.name).filterFontSize;
    },
    widthFilter() {
      return this.$store.getters.getFieldParams(this.name).filterWidth+'px';
    },
    styleWrapperTd() {
      return {
        alignContent: 'center',
        backgroundColor: 'darkturquoise',
        marginLeft: this.position === 'left'? '10px' : '0',
        padding: '0',
        fontSize: '0',
        minWidth: this.width,
        maxWidth: this.width,
        borderStyle: `solid solid solid ${this.position === 'left' ? 'solid' : 'none'}`,
        borderWidth: `thin`,
        borderColor: `black`
      }
    },
    styleTd() {
      return {
        display: 'block'
      }
    },
    styleVal() {
      return {
        height: '20px',
        padding: this.labelPadding,
        textAlign: 'center',
        fontSize: this.fontSize,
        color: `black`,
        fontWeight: `normal`
      }
    },
    styleInput() {
      return {
        height: '20px',
        padding: '0',
        textAlign: 'left',
        fontSize: this.fontSizeFilter,
        color: `black`,
        fontWeight: `normal`,
        minWidth: this.widthFilter,
        maxWidth: this.widthFilter
      }
    },
  }
}
</script>

<style scoped>

</style>