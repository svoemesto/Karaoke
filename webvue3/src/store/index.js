import { createStore } from 'vuex'

// Импортируйте ваши будущие модули
import song from '../components/Songs/store'
import songFilter from '../components/Songs/filter/store'
import smartCopy from '../components/Common/SmartCopy/store'
import healthReport from '../components/Common/HealthReport/store'
import fileExplorer from '../components/Common/FileExplorer/store'
import processes from '../components/Processes/store'
import properties from '../components/Properties/store'
import authors from '../components/Authors/store'
import siteUsers from '../components/SiteUsers/store'
import sitePlaylists from '../components/SitePlaylists/store'
import songEditor from '../components/SongEditor/store'
import publicSettings from '../components/PublicSettings/store'
import pictures from '../components/Pictures/store'
import processFilter from '../components/Processes/filter/store'
import propertiesFilter from '../components/Properties/filter/store'
import authorsFilter from '../components/Authors/filter/store'
import picturesFilter from '../components/Pictures/filter/store'
import publish from '../components/Publish/store'
import publishFilter from '../components/Publish/filter/store'
import common from '../components/Common/store'
import stats from '../components/Stats/store'
import sync from '../components/Sync/store'
import { useToast } from 'bootstrap-vue-next'

export default createStore({
  components: {
    useToast
  },
  modules: {
    song,
    publish,
    processes,
    properties,
    authors,
    siteUsers,
    sitePlaylists,
    songEditor,
    publicSettings,
    pictures,
    common,
    stats,
    sync,
    songFilter,
    smartCopy,
    healthReport,
    fileExplorer,
    processFilter,
    propertiesFilter,
    authorsFilter,
    picturesFilter,
    publishFilter,
  },

  state: {
    stompClientSongs: null
  },
  mutations: {
    // мутации
  },
  actions: {

  },
  getters: {
    // геттеры
  }
})