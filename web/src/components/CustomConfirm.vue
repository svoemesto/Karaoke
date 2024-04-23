<template>
  <transition name="modal-fade">
    <div class="modal-backdrop">
      <div class="area">
        <body>
        <div class="area">
          <div class="body">
            <div v-html="params.header" :style="styleHeader">
            </div>
            <div v-html="params.body" :style="styleBody">
            </div>
            <div :style="styleFooter">
              <button v-if="!params.isAlert" type="button" :style="styleButtonOk" @click="ok">Да</button>
              <button type="button" :style="styleButtonCancel" @click="close">{{buttonCloseCaption}}</button>
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
  props: {
    params: {
      type: Object,
      required: true,
      default: () => {}
    }
  },
  methods: {
    decreaseTimeToClose() {
      this.timeToClose--
    },
    ok() {
      this.params.callback();
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
}

</style>