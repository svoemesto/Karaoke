<template>
  <div :style="styleRoot">
    <div :style="styleVal" @dblclick="$emit('dblclick')" @click.left="$emit('click')" v-text="text">
    </div>
  </div>
</template>

<script>
export default {
  name: "SongTableBodyTd",
  props: {
    id: {
      type: Number,
      required: true
    },
    name: {
      type: String,
      required: true
    },
    text: {
      type: String,
      required: true
    },
    position: {
      type: String,
      required: false
    },
    color: {
      type: String,
      required: false,
      default: () => '#fff'
    }
  },
  computed: {
    disabled() {
      return this.$store.getters.isChangedProcess;
    },
    textAlign() {
      return this.$store.getters.getProcessFieldParams(this.name).textAlign;
    },
    fontSize() {
      return this.$store.getters.getProcessFieldParams(this.name).fontSize;
    },
    width() {
      return this.$store.getters.getProcessFieldParams(this.name).width+'px';
    },
    currId() {
      return this.$store.getters.getCurrentProcessId;
    },
    isCurrent() {
      return this.currId === this.id;
    },
    styleRoot() {
      let result = {
        margin: 0,
        alignContent: 'center',
        backgroundColor: this.disabled && !this.isCurrent ? 'lightgray' : this.color,
        marginLeft: this.position === 'left'? '10px' : '0',
        padding: '0',
        fontSize: '0',
        minWidth: this.width,
        maxWidth: this.width,
        borderStyle: `solid solid ${this.isCurrent ? 'solid' : 'none'} ${this.position === 'left' ? 'solid' : 'none'}`,
        borderWidth: `${ this.isCurrent ? '3px' : 'thin' } ${ this.isCurrent && this.position === 'right' ? '3px' : 'thin' } ${ this.isCurrent ? '2px' : 'thin' } ${ this.isCurrent && this.position === 'left' ? '3px' : 'thin' }`,
        borderColor: `${ this.isCurrent ? 'red' : 'black' } ${ this.isCurrent && this.position === 'right' ? 'red' : 'black' } ${ this.isCurrent ? 'red' : 'black' } ${ this.isCurrent && this.position === 'left' ? 'red' : 'black' }`
      }
      return result
    },
    styleVal() {
      return {
        margin: 0,
        height: '100%',
        padding: '0 4px 0 4px',
        textAlign: this.textAlign,
        fontSize: this.fontSize,
        color: this.disabled && !this.isCurrent ? 'darkgray' : this.isCurrent ? 'mediumblue' : 'black',
        fontWeight: this.isCurrent ? 'bold' : 'normal'
      }
    }
  }
}
</script>

<style scoped>

</style>