<template>
  <transition name="modal-fade">
    <div class="modal-backdrop">
      <div class="area">
        <body>
        <div class="area">
          <div class="body">
            <div v-html="params.header" :style="styleHeader"></div>
            <div v-html="params.body" :style="styleBody"></div>
            <div v-if="params.fields" class="params">
              <div v-for="fld in params.fields" :key="fld" class="param-line">
                <div v-text="fld.fldLabel" :style="fld.fldLabelStyle"></div>
                <input v-model="fld.fldValue" :style="fld.fldValueStyle">
              </div>
            </div>
            <div :style="styleFooter">
              <button v-if="!params.isAlert" type="button" class="button-ok" @click="ok" v-text="'Да'"></button>
              <button type="button" class="button-cancel" @click="close" v-text="buttonCloseCaption"></button>
            </div>
          </div>
        </div>
        </body>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: "CustomConfirm",
  props: {
    params: {
      type: Object,
      required: true,
      default: () => {}
    }
  },
  data () {
    return {
      timeToClose: undefined
    };
  },
  mounted() {
    if (this.params.timeout) {
      this.timeToClose = this.params.timeout;
      setInterval(this.decreaseTimeToClose,1000);
    }
  },
  watch: {
    timeToClose: {
      handler () {
        if (this.timeToClose < 0) {
          clearInterval(this.decreaseTimeToClose);
          this.close();
        }
      }
    }
  },
  computed: {
    buttonCloseCaption() {
      return (this.params.isAlert ? 'OK' : 'Нет') + (this.timeToClose ? ` ( ${this.timeToClose} сек. )` : '')
    },
    styleHeader() {
      return {
        backgroundColor: this.params.alertType === 'warning' ? 'darkorange' : this.params.alertType === 'error' ? 'darkred' : 'darkslategray',
        padding: '10px',
        color: 'white',
        fontSize: 'larger',
        fontWeight: '300'
      }
    },
    styleBody() {
      return {
        backgroundColor: 'white',
        padding: '10px',
        color: 'black',
        fontSize: 'larger',
        fontWeight: '300'
      }
    },
    styleFooter() {
      return {
        backgroundColor: this.params.alertType === 'warning' ? 'darkorange' : this.params.alertType === 'error' ? 'darkred' : 'darkslategray',
        padding: '10px',
        color: 'white',
        fontSize: 'larger',
        fontWeight: '300',
        display: 'flex',
        justifyContent: 'flex-end'
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
        marginRight: '10px'
      }
    },
    styleButtonCancel()
    {
      return {
        border: '1px solid white',
        borderRadius: '10px',
        fontSize: '20px',
        cursor: 'pointer',
        fontWeight: 'bold',
        color: this.params.isAlert ? '#4AAE9B' : 'indianred',
        background: 'transparent',
        width: '150px',
        height: 'auto'
      }
    }
  },
  methods: {
    decreaseTimeToClose() {
      this.timeToClose--
    },
    ok() {
      let ret = {};
      if (this.params.fields) {
        for (let i = 0; i < this.params.fields.length; i++) {
          let paramName = this.params.fields[i].fldName;
          let paramValue = this.params.fields[i].fldValue;
          ret[paramName] = paramValue;
        }
      }
      this.params.callback(ret);
      this.$emit('close');
    },
    close() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.modal-fade-enter,
.modal-fade-leave-active {
  opacity: 0;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity .5s ease
}

.modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
}

.area {
  background: #FFFFFF;
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

.params {
  display: flex;
  flex-direction: column;
}
.param-line {
  display: flex;
  flex-direction: row;
}

.button-ok {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 18px;
  cursor: pointer;
  font-weight: bold;
  color: #4AAE9B;
  background: darkslategray;
  width: 150px;
  height: auto;
  margin-right: 10px;
}
.button-ok:hover {
  background: darkgreen;
}
.button-cancel {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 18px;
  cursor: pointer;
  font-weight: bold;
  color: #4AAE9B;
  background: darkslategray;
  width: 150px;
  height: auto;
}
.button-cancel:hover {
  background: darkgreen;
}
</style>