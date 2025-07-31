import Vue from 'vue'
import Vuex from 'vuex'
import song from '../components/Songs/store'
import songBv from '../components/SongsBv/store'
import songFilter from '../components/SongsFilter/store'
import smartCopy from '../components/SmartCopy/store'
import fileExplorer from '../components/FileExplorer/store'
import processesBv from '../components/ProcessesBv/store'
import propertiesBv from '../components/PropertiesBv/store'
import processFilter from '../components/ProcessesFilter/store'
import propertiesFilter from '../components/PropertiesFilter/store'
import publish from '../components/Publish/store'
import publicationBv from '../components/PublicationsBv/store'
import common from '../components/Common/store'
// import process from '../components/Processes/store'
Vue.use(Vuex)

export default new Vuex.Store({

  modules: {
    song,
    publish,
    songBv,
    processesBv,
    propertiesBv,
    publicationBv,
    common,
    songFilter,
    smartCopy,
    fileExplorer,
    processFilter,
    propertiesFilter,
    // process
  }
})
