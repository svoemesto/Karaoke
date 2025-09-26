import Vue from 'vue'
import Vuex from 'vuex'
import song from '../components/Songs/store'
import songFilter from '../components/Songs/filter/store'
import smartCopy from '../components/Common/SmartCopy/store'
import fileExplorer from '../components/Common/FileExplorer/store'
import processes from '../components/Processes/store'
import properties from '../components/Properties/store'
import authors from '../components/Authors/store'
import pictures from '../components/Pictures/store'
import processFilter from '../components/Processes/filter/store'
import propertiesFilter from '../components/Properties/filter/store'
import authorsFilter from '../components/Authors/filter/store'
import picturesFilter from '../components/Pictures/filter/store'
import publish from '../components/Publish/store'
import common from '../components/Common/store'
Vue.use(Vuex)

export default new Vuex.Store({

  modules: {
    song,
    publish,
    processes,
    properties,
    authors,
    pictures,
    common,
    songFilter,
    smartCopy,
    fileExplorer,
    processFilter,
    propertiesFilter,
    authorsFilter,
    picturesFilter,
  }
})
