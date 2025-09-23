import Vue from 'vue'
import Vuex from 'vuex'
import song from '../components/Songs/store'
import songFilter from '../components/Songs/filter/store'
import smartCopy from '../components/Common/SmartCopy/store'
import fileExplorer from '../components/Common/FileExplorer/store'
import processesBv from '../components/Processes/store'
import propertiesBv from '../components/Properties/store'
import authorsBv from '../components/Authors/store'
import processFilter from '../components/Processes/filter/store'
import propertiesFilter from '../components/Properties/filter/store'
import authorsFilter from '../components/Authors/filter/store'
import publish from '../components/Publish/store'
import common from '../components/Common/store'
Vue.use(Vuex)

export default new Vuex.Store({

  modules: {
    song,
    publish,
    processesBv,
    propertiesBv,
    authorsBv,
    common,
    songFilter,
    smartCopy,
    fileExplorer,
    processFilter,
    propertiesFilter,
    authorsFilter,
  }
})
