<template>
  <transition name="modal-fade">
    <div class="сс-modal-backdrop">
      <div class="сс-area">
        <div :style="styleHeader" v-html="params.header" />
        <div :style="styleBody" v-html="params.body" />
        <div v-if="params.fields" class="сс-params">
          <div v-for="fld in params.fields" :key="fld" class="сс-param-line-fields">
            <div :style="fld.fldLabelStyle" v-text="fld.fldLabel" />
            <div v-if="fld.fldIsBoolean" class="сс-param-line-buttons">
              <button
                class="сс-group-button-boolean"
                :class="
                  fld.fldValue === 'true' || fld.fldValue === true
                    ? 'сс-group-button-boolean-active'
                    : ''
                "
                type="button"
                value="true"
                :disabled="fld.disabled"
                @click="fld.fldValue = 'true'"
              >
                TRUE
              </button>
              <button
                class="сс-group-button-boolean"
                :class="
                  fld.fldValue === 'false' || fld.fldValue === false
                    ? 'сс-group-button-boolean-active'
                    : ''
                "
                type="button"
                value="false"
                :disabled="fld.disabled"
                @click="fld.fldValue = 'false'"
              >
                FALSE
              </button>
            </div>
            <div v-else-if="fld.fldIsSelect" class="сс-param-line-buttons">
              <select
                v-model="fld.fldValue"
                class="сс-select-field"
                :style="fld.fldValueStyle"
                :disabled="fld.disabled"
              >
                <option v-for="opt in fld.fldOptions" :key="opt" :value="opt" v-text="opt" />
              </select>
            </div>
            <div v-else class="сс-param-line-buttons">
              <input v-model="fld.fldValue" :style="fld.fldValueStyle" :disabled="fld.disabled" />
            </div>
          </div>
        </div>
        <div :style="styleFooter">
          <button
            v-if="!params.isAlert"
            type="button"
            class="сс-button-ok"
            @click="ok"
            v-text="'Да'"
          />
          <button
            type="button"
            class="сс-button-cancel"
            @click="close"
            v-text="buttonCloseCaption"
          />
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: 'CustomConfirm',
  props: {
    params: {
      type: Object,
      required: true,
      default: () => {},
    },
  },
  data() {
    return {
      timeToClose: undefined,
    }
  },
  computed: {
    buttonCloseCaption() {
      return (
        (this.params.isAlert ? 'OK' : 'Нет') +
        (this.timeToClose ? ` ( ${this.timeToClose} сек. )` : '')
      )
    },
    styleHeader() {
      return {
        backgroundColor:
          this.params.alertType === 'warning'
            ? 'darkorange'
            : this.params.alertType === 'error'
              ? 'darkred'
              : 'darkslategray',
        padding: '10px',
        color: 'white',
        fontSize: 'larger',
        fontWeight: '300',
      }
    },
    styleBody() {
      return {
        backgroundColor: 'white',
        padding: '10px',
        color: 'black',
        fontSize: 'larger',
        fontWeight: '300',
      }
    },
    styleFooter() {
      return {
        backgroundColor:
          this.params.alertType === 'warning'
            ? 'darkorange'
            : this.params.alertType === 'error'
              ? 'darkred'
              : 'darkslategray',
        padding: '10px',
        color: 'white',
        fontSize: 'larger',
        fontWeight: '300',
        display: 'flex',
        justifyContent: 'flex-end',
      }
    },
    styleButtonOk() {
      return {
        border: '1px solid white',
        borderRadius: '10px',
        fontSize: '20px',
        cursor: 'pointer',
        fontWeight: 'bold',
        color: '#4AAE9B',
        background: 'transparent',
        width: '150px',
        height: 'auto',
        marginRight: '10px',
      }
    },
    styleButtonCancel() {
      return {
        border: '1px solid white',
        borderRadius: '10px',
        fontSize: '20px',
        cursor: 'pointer',
        fontWeight: 'bold',
        color: this.params.isAlert ? '#4AAE9B' : 'indianred',
        background: 'transparent',
        width: '150px',
        height: 'auto',
      }
    },
  },
  watch: {
    timeToClose: {
      handler() {
        if (this.timeToClose < 0) {
          clearInterval(this.decreaseTimeToClose)
          this.close()
        }
      },
    },
  },
  mounted() {
    if (this.params.timeout) {
      this.timeToClose = this.params.timeout
      setInterval(this.decreaseTimeToClose, 1000)
    }
  },
  methods: {
    decreaseTimeToClose() {
      this.timeToClose--
    },
    ok() {
      let ret = {}
      if (this.params.fields) {
        for (let i = 0; i < this.params.fields.length; i++) {
          let paramName = this.params.fields[i].fldName
          let paramValue = this.params.fields[i].fldValue
          ret[paramName] = paramValue
        }
      }
      this.params.callback(ret)
      this.$emit('close')
    },
    close() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.сс-modal-fade-enter,
.сс-modal-fade-leave-active {
  opacity: 0;
}

.сс-modal-fade-enter-active,
.сс-modal-fade-leave-active {
  transition: opacity 0.5s ease;
}

.сс-modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1055;
}

.сс-area {
  background: #ffffff;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: auto;
  height: auto;
  position: relative;
  max-width: 1280px;
  max-height: 720px;
  font-family: Avenir, Helvetica, Arial, sans-serif;
  font-weight: 300;
}

.сс-params {
  display: flex;
  flex-direction: column;
}
.сс-param-line-fields {
  display: flex;
  flex-direction: row;
}
.сс-param-line-buttons {
  display: flex;
  flex-direction: row;
  justify-content: left;
}

.сс-button-ok {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 18px;
  cursor: pointer;
  font-weight: bold;
  color: #4aae9b;
  background: darkslategray;
  width: 150px;
  height: auto;
  margin-right: 10px;
}
.сс-button-ok:hover {
  background: darkgreen;
}
.сс-button-cancel {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 18px;
  cursor: pointer;
  font-weight: bold;
  color: #4aae9b;
  background: darkslategray;
  width: 150px;
  height: auto;
}
.сс-button-cancel:hover {
  background: darkgreen;
}
.сс-group-button-boolean {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: auto;
}
.сс-group-button-boolean-active {
  background-color: dodgerblue;
}

/* Инвариант DEVELOPMENT.md: нативный select рисует свою ОС-рамку/паддинг/высоту поверх заданных
   стилей — appearance:none обязателен. Ширину/border-radius задаёт fld.fldValueStyle (тот же
   объект стиля, что и у текстового поля этого ряда) — отдельный border здесь только чтобы select
   не остался совсем без рамки после сброса appearance. */
.сс-select-field {
  box-sizing: border-box;
  border: 1px solid gray;
  padding: 2px 4px;
  background-color: white;
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
}
</style>
