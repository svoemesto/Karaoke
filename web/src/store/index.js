import Vue from 'vue'
import Vuex from 'vuex'
import song from '../components/Songs/store'
import songBv from '../components/SongsBv/store'
import songFilter from '../components/SongsFilter/store'
import processesBv from '../components/ProcessesBv/store'
import processFilter from '../components/ProcessesFilter/store'
import publish from '../components/Publish/store'
import publicationBv from '../components/PublicationsBv/store'
// import process from '../components/Processes/store'
Vue.use(Vuex)

export default new Vuex.Store({

  modules: {
    song,
    publish,
    songBv,
    processesBv,
    publicationBv,
    songFilter,
    processFilter,
    // process
  }
})
