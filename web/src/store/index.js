import Vue from 'vue'
import Vuex from 'vuex'
import song from './modules/song'
Vue.use(Vuex)

export default new Vuex.Store({

  modules: {
    song
  }
})
