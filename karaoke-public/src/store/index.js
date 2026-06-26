import { createStore } from 'vuex'
import stats from './modules/stats'
import songs from './modules/songs'
import zakroma from './modules/zakroma'

export default createStore({
  modules: {
    stats,
    songs,
    zakroma
  }
})
