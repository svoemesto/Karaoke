<template>
  <div class="km-page">
    <!-- Хедер -->
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/zakroma" class="km-back">← Назад</RouterLink>
          <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
        </div>
        <div class="km-header-right">
          <AuthStatusWidget />
          <div class="km-theme-toggle">
            <button
              :class="['km-tb', theme === 'light' ? 'active' : '']"
              title="Светлая"
              @click="setTheme('light')"
            >
              ☀
            </button>
            <button
              :class="['km-tb', theme === 'system' ? 'active' : '']"
              title="Авто"
              @click="setTheme('system')"
            >
              ⬡
            </button>
            <button
              :class="['km-tb', theme === 'dark' ? 'active' : '']"
              title="Тёмная"
              @click="setTheme('dark')"
            >
              🌙
            </button>
          </div>
        </div>
      </div>
    </header>

    <!-- Загрузка -->
    <div v-if="currentSongIsLoading" class="km-loading">Загрузка...</div>

    <!-- Удалено -->
    <div v-else-if="currentSong && currentSong.contentRemoved" class="km-removed-wrapper">
      <div class="km-removed-card">
        <div class="km-removed-icon">🔒</div>
        <div class="km-removed-title">Информация о произведении удалена</div>
        <div class="km-removed-subtitle">по требованию правообладателя</div>
        <hr class="km-removed-divider" />
        <div class="km-removed-hint">
          Страница недоступна в соответствии с обращением<br />
          об авторских правах. Если вы считаете, что это<br />
          произошло по ошибке — свяжитесь с нами.
        </div>
        <RouterLink to="/" class="km-btn-home">← На главную</RouterLink>
      </div>
    </div>

    <!-- Страница песни -->
    <div v-else-if="currentSong" class="km-song">
      <!-- Hero-баннер -->
      <div class="km-hero">
        <img
          v-if="currentSong.songPictureUrl"
          :src="currentSong.songPictureUrl"
          class="km-hero-banner"
          alt=""
          @error="$event.target.style.display = 'none'"
        />
        <div class="km-hero-overlay">
          <h1 class="km-song-title">«{{ currentSong.songName }}»</h1>
          <p class="km-song-author">{{ currentSong.author }}</p>
        </div>
      </div>

      <div class="km-content">
        <!-- Метаданные -->
        <div class="km-meta-card">
          <!-- specs/012-entity-description-fields FR-019/020/021: предупреждение/короткое
               описание/описание песни — независимо друг от друга. -->
          <div
            v-if="currentSong.warning || currentSong.shortDescription || currentSong.description"
            class="km-song-notes"
          >
            <div v-if="currentSong.warning" class="km-warning-text">{{ currentSong.warning }}</div>
            <div
              v-if="currentSong.shortDescription || currentSong.description"
              class="km-song-note-line"
            >
              <span v-if="currentSong.shortDescription" class="km-short-description-text">{{
                currentSong.shortDescription
              }}</span>
              <span
                v-if="currentSong.description"
                class="km-info-icon"
                :title="currentSong.description"
                >ⓘ</span
              >
            </div>
          </div>
          <div class="km-meta-grid">
            <div class="km-meta-item">
              <span class="km-meta-label">Исполнитель</span>
              <span class="km-meta-value" @click="onMetaClick('author', $event)">{{
                currentSong.author
              }}</span>
            </div>
            <div class="km-meta-item">
              <span class="km-meta-label">Год</span>
              <span class="km-meta-value" @click="onMetaClick('year', $event)">{{
                currentSong.year
              }}</span>
            </div>
            <div class="km-meta-item">
              <span class="km-meta-label">Альбом</span>
              <span class="km-meta-value" @click="onMetaClick('album', $event)">{{
                currentSong.album
              }}</span>
            </div>
            <div class="km-meta-item">
              <span class="km-meta-label">Трек</span>
              <span class="km-meta-value">{{ currentSong.track }}</span>
            </div>
            <div v-if="currentSong.key" class="km-meta-item">
              <span class="km-meta-label">Тональность</span>
              <span class="km-meta-value" @click="onMetaClick('key', $event)">{{
                currentSong.key
              }}</span>
            </div>
            <div v-if="currentSong.bpm" class="km-meta-item">
              <span class="km-meta-label">Темп (уд/м)</span>
              <span class="km-meta-value">{{ currentSong.bpm }}</span>
            </div>
            <div class="km-meta-actions">
              <FavoriteIcon :song-id="currentSong.id" label="В избранное" />
              <PlaylistIcon :song-id="currentSong.id" label="В плейлист" />
              <ShareButton />
              <ShareLinkButton :song-id="currentSong.id" />
              <!-- Self-assign (FR-005/US2, specs/182-editor-self-assign-tasks): кнопка «Взять в
                   работу» появляется ТОЛЬКО для self-assign-редакторов на странице конкретной песни
                   (а не в Закромах), когда песня свободна. Если задание уже наше — показываем
                   «Открыть задание» (variant A, см. clarification Q4). -->
              <button
                v-if="showSelfAssignButton"
                class="km-self-assign-btn"
                :disabled="assigningSongId === currentSong.id"
                @click="onSelfAssignClick"
              >
                {{ assigningSongId === currentSong.id ? 'Берём…' : 'Взять в работу' }}
              </button>
              <button
                v-else-if="showOpenAssignmentButton"
                class="km-self-assign-open-btn"
                @click="onOpenAssignmentClick"
              >
                Открыть задание
              </button>
            </div>
          </div>
        </div>

        <!-- Онлайн-плеер: между блоком информации о песне и "Ссылки на просмотр". В демо-режиме
             (playerIsDemo) это тот же iframe — сам плеер получит demo-токен и обрежется до
             фрагмента "до конца первого куплета" (см. PublicPlayerController.access/KaraokePlayer.js) -->
        <div
          v-if="playerCanWatch || playerIsDemo"
          class="km-player-card"
          :class="{ 'km-player-page-mode': playerDisplayMode === 'page' }"
        >
          <div class="km-player-label">
            🎤 Онлайн-плеер караоке<span v-if="playerIsDemo" class="km-player-demo-badge"
              >ДЕМО</span
            >
          </div>
          <div class="km-video-wrap km-player-wrap">
            <iframe
              ref="playerIframe"
              :src="`/player/${currentSong.id}`"
              allow="autoplay; fullscreen"
              frameborder="0"
              allowfullscreen
            />
          </div>
        </div>

        <!-- Демо-режим: контент есть, но полного доступа нет — вместо карточки ожидания (та
             рассчитана на "ещё не готово") сразу предлагаем подписку рядом с самим демо-плеером.
             Раньше условие включало "!onAir" — избыточно (isDemo и так требует ready=true, а под
             старыми правилами onAir+ready всегда означало canWatch=true, isDemo=false); под новыми
             правилами окна (specs/143) isDemo достижим и при onAir=true (эфир был, окно истекло),
             поэтому условие держится только на playerIsDemo. -->
        <div v-if="playerIsDemo" class="km-waiting-card">
          <div class="km-waiting-title">Это демо-фрагмент</div>
          <div class="km-waiting-body">
            В демо-режиме доступен только небольшой фрагмент песни. Оформите подписку, чтобы слушать
            песню целиком.
          </div>

          <div v-if="!playerIsPremiumUser" class="km-waiting-offer">
            <div class="km-waiting-offer-icon">🪙</div>
            <div class="km-waiting-offer-title">Премиум-подписка</div>
            <div class="km-waiting-offer-desc">Подписка на всю коллекцию или на одну песню</div>
            <div class="km-waiting-offer-actions">
              <RouterLink to="/premium" class="km-waiting-cta"
                >Оформить премиум-подписку →</RouterLink
              >
              <button
                v-if="isLoggedIn && canOfferSongSubscription"
                class="km-waiting-cta km-song-sub-cta"
                @click="songSubscriptionModalVisible = true"
              >
                Оформить подписку на эту песню →
              </button>
            </div>
          </div>

          <div v-if="!isLoggedIn" class="km-waiting-login">
            Также вы можете <RouterLink to="/register">зарегистрироваться</RouterLink> или
            <RouterLink to="/login">войти</RouterLink> на сайте — это понадобится для оформления
            подписки.
          </div>
        </div>

        <!-- Видео ВК — старое место, только когда контент физически не готов (не путать с "готов,
             но окно бесплатного доступа истекло" — тот случай ниже, карточка ожидания). -->
        <div v-if="currentSong.onAir && !playerReady && playerAccessLoaded" class="km-videos">
          <div v-if="currentSong.idVkKaraoke" class="km-video-block" @click="onPlay('karaoke')">
            <div class="km-video-label">Karaoke</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkKaraokeOID}&id=${currentSong.idVkKaraokeID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
                frameborder="0"
                allowfullscreen
              />
            </div>
          </div>
          <div v-if="currentSong.idVkLyrics" class="km-video-block" @click="onPlay('lyrics')">
            <div class="km-video-label">Lyrics</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkLyricsOID}&id=${currentSong.idVkLyricsID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
                frameborder="0"
                allowfullscreen
              />
            </div>
          </div>
          <div v-if="currentSong.idVkMelody" class="km-video-block" @click="onPlay('tabs')">
            <div class="km-video-label">TABS</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkMelodyOID}&id=${currentSong.idVkMelodyID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
                frameborder="0"
                allowfullscreen
              />
            </div>
          </div>
          <div v-if="currentSong.idVkChords" class="km-video-block" @click="onPlay('chords')">
            <div class="km-video-label">Chords</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkChordsOID}&id=${currentSong.idVkChordsID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
                frameborder="0"
                allowfullscreen
              />
            </div>
          </div>
        </div>

        <!-- Ещё не в эфире, ЛИБО в эфире+контент готов, но окно бесплатного доступа истекло
             (specs/143) — и плеер недоступен даже в демо-режиме. Сообщение об ожидании/подписке.
             Тоже на старом месте видео-блока. Когда демо доступен (playerIsDemo) — своя отдельная
             карточка сразу под демо-плеером, см. выше. -->
        <div
          v-if="
            (!currentSong.onAir || playerReady) &&
            !playerCanWatch &&
            !playerIsDemo &&
            playerAccessLoaded
          "
          class="km-waiting-card"
        >
          <div class="km-waiting-title">{{ waitingTitle }}</div>
          <div class="km-waiting-body">{{ waitingBody }}</div>

          <div v-if="!playerIsPremiumUser" class="km-waiting-offer">
            <div class="km-waiting-offer-icon">🪙</div>
            <div class="km-waiting-offer-title">Премиум-подписка</div>
            <div class="km-waiting-offer-desc">Подписка на всю коллекцию или на одну песню</div>
            <div class="km-waiting-offer-actions">
              <RouterLink to="/premium" class="km-waiting-cta"
                >Оформить премиум-подписку →</RouterLink
              >
              <button
                v-if="isLoggedIn && canOfferSongSubscription"
                class="km-waiting-cta km-song-sub-cta"
                @click="songSubscriptionModalVisible = true"
              >
                Оформить подписку на эту песню →
              </button>
            </div>
          </div>

          <div v-if="!isLoggedIn" class="km-waiting-login">
            Также вы можете <RouterLink to="/register">зарегистрироваться</RouterLink> или
            <RouterLink to="/login">войти</RouterLink> на сайте — это понадобится для оформления
            подписки.
          </div>
          <div v-else-if="playerIsPremiumUser" class="km-waiting-login">
            Вы премиум-пользователь — как только материалы для плеера будут готовы, он появится
            здесь автоматически.
          </div>
        </div>

        <SongSubscriptionModal
          :visible="songSubscriptionModalVisible"
          :song-id="currentSong && currentSong.id"
          :song-name="currentSong ? `${currentSong.songName} — ${currentSong.author}` : ''"
          @close="songSubscriptionModalVisible = false"
          @activated="onSongSubscriptionActivated"
        />

        <!-- Текст / Табы / Аккорды -->
        <div v-if="currentSong.formattedTextSong" class="km-text-card">
          <div class="km-text-header">Текст песни</div>
          <div class="km-text-body" v-html="currentSong.formattedTextSong" />
        </div>
        <div v-if="currentSong.formattedTextTabs" class="km-text-card">
          <div class="km-text-header">Табулатура</div>
          <div class="km-text-body" v-html="currentSong.formattedTextTabs" />
        </div>
        <div v-if="currentSong.formattedTextChords" class="km-text-card">
          <div class="km-text-header">Аккорды</div>
          <div class="km-text-body" v-html="currentSong.formattedTextChords" />
        </div>
      </div>
    </div>

    <p v-else class="km-not-found">Песня не найдена.</p>
  </div>
</template>

<script>
import { useRoute } from 'vue-router'
import { mapGetters, mapActions } from 'vuex'
import AuthStatusWidget from '../components/AuthStatusWidget.vue'
import FavoriteIcon from '../components/FavoriteIcon.vue'
import PlaylistIcon from '../components/PlaylistIcon.vue'
import ShareButton from '../components/ShareButton.vue'
import ShareLinkButton from '../components/ShareLinkButton.vue'
import { useDesign } from '../composables/useDesign'
import { useEngagementTracking } from '../composables/useEngagementTracking'
import { useAuth } from '../composables/useAuth'
import { usePlayerAccess } from '../composables/usePlayerAccess'
import { usePlaylistMembership } from '../composables/usePlaylistMembership'
import { trackPlay, trackMetaClick } from '../services/tracking'
import { pluralDays } from '../utils/pluralRu'
import SongSubscriptionModal from '../components/SongSubscriptionModal.vue'
import { useCart } from '../composables/useCart'
import { assignSelf as apiAssignSelf } from '../services/songEditorApi'

/**
 * Публичная страница песни (`/song?id=...`).
 *
 * Функционал:
 * - **Header**: автор, альбом, год, имя песни, тип (song/instrumental/poetry).
 * - **Player**: переключатель «embed» (встроенный) / «premium» (полный плеер).
 * - **Waiting screen**: показывается либо если песня ещё не вышла в эфир (использует
 *   `currentSong.airTimestamp` и `daysUntilAir`), либо если эфир уже был, но окно бесплатного
 *   доступа истекло (`currentSong.onAir` внутри уже суженного v-if — см. `playerReady`/
 *   `waitingTitle`, specs/143-song-free-access-window).
 * - **Subscription modal**: если песня доступна только по подписке
 *   (`songSubscriptionAvailable`) — предлагает купить.
 * - **Metadata**: «сгенерировано Kdenlive + MLT», ссылка на Boosty.
 *
 * Использует Vuex-модули:
 * - `auth` — авторизация (влияет на доступ к премиум-плееру).
 * - `cart` — добавление в корзину.
 * - `playlistMembership` — добавление в плейлисты.
 *
 * @see docs/features/premium-stems.md
 * @see docs/features/mp4-render.md (Player)
 * @see docs/features/song-free-access.md
 */
export default {
  name: 'SongView',
  components: {
    AuthStatusWidget,
    SongSubscriptionModal,
    FavoriteIcon,
    PlaylistIcon,
    ShareButton,
    ShareLinkButton,
  },
  setup() {
    const route = useRoute()
    useEngagementTracking('song', () => route.query.id)
    const { theme, applyTheme } = useDesign()
    function setTheme(val) {
      theme.value = val
      applyTheme(val)
    }
    const { isLoggedIn, user } = useAuth()
    const playerAccess = usePlayerAccess()
    const cart = useCart()
    const playlistMembership = usePlaylistMembership()
    return { theme, setTheme, isLoggedIn, user, playerAccess, cart, playlistMembership }
  },
  data() {
    return {
      playerDisplayMode: 'embed',
      songSubscriptionModalVisible: false,
      // FR-005 (self-assign): блокировка двойных кликов на кнопке "Взять в работу".
      assigningSongId: null,
    }
  },
  computed: {
    ...mapGetters('songs', ['currentSong', 'currentSongIsLoading']),
    playerCanWatch() {
      return this.playerAccess.canWatch.value
    },
    playerAccessLoaded() {
      return this.playerAccess.loaded.value
    },
    playerIsPremiumUser() {
      return this.playerAccess.isPremiumUser.value
    },
    playerIsDemo() {
      return this.playerAccess.isDemo.value
    },
    // Готовность контента (независимо от платного доступа) — отличает "контент физически не готов"
    // (легаси VK-видео-фоллбек) от "готов, но окно бесплатного доступа истекло" (карточка
    // ожидания с предложением подписки). specs/143-song-free-access-window.
    playerReady() {
      return this.playerAccess.ready.value
    },
    daysUntilAir() {
      const ts = this.currentSong?.airTimestamp
      if (!ts) return null
      return Math.ceil((ts - Date.now()) / 86400000)
    },
    waitingTitle() {
      const s = this.currentSong
      if (!s) return ''
      // s.onAir здесь означает "эфир уже был, но окно бесплатного доступа истекло" — этот
      // computed вычисляется только внутри v-if, который для !onAir уже отсеян (см. шаблон,
      // "!currentSong.onAir || playerReady"). specs/143-song-free-access-window, FR-015: тот же
      // текст, что раньше показывался для песен с флагом "эксклюзив".
      if (s.onAir) return 'Эта песня доступна только по подписке'
      if (this.daysUntilAir === null) return 'Дата выхода в эфир пока не определена'
      if (this.daysUntilAir <= 0) return 'Песня скоро появится в эфире'
      return `Песня выйдет в эфир через ${this.daysUntilAir} ${pluralDays(this.daysUntilAir)}`
    },
    waitingBody() {
      const s = this.currentSong
      if (!s) return ''
      if (s.onAir) {
        return 'Оформите подписку, чтобы посмотреть эту песню.'
      }
      return 'Не хотите ждать эфир? Оформите подписку — и песня станет доступна сразу.'
    },
    // Отдельная подписка на песню (см. план монетизации) — предлагаем, только если админ пометил
    // песню как продающуюся (idTariff>0 на бэкенде -> songSubscriptionAvailable) и плеер сейчас
    // всё равно недоступен обычными путями (иначе кнопка была бы бессмысленна).
    canOfferSongSubscription() {
      return !!(
        this.currentSong &&
        this.currentSong.songSubscriptionAvailable &&
        !this.playerCanWatch &&
        this.playerAccessLoaded
      )
    },
    // FR-007/US2 — self-assign редактор: залогинен + isEditor + canSelfAssignTasks. Фронт НЕ
    // доверяет, а бэкенд перепроверяет в /api/public/songeditor/assign-self. showSelfAssignButton
    // — обёртка: показывать ТОЛЬКО на свободной песне (для чужого/своего задания — другой шаблон).
    // NOTE: useAuth().user использует JSON-ключи Jackson DTO (после отбрасывания is-префикса):
    // поле `isEditor` Kotlin сериализуется как `editor`, `isBanned` → `banned`. См. AGENTS.md Q&A.
    canSelfAssignEditor() {
      return !!(this.user && this.user.editor && this.user.canSelfAssignTasks)
    },
    userId() {
      return this.user && this.user.id ? Number(this.user.id) : 0
    },
    showSelfAssignButton() {
      return !!(this.canSelfAssignEditor && this.currentSong && !this.currentSong.assignment)
    },
    showOpenAssignmentButton() {
      return !!(
        this.canSelfAssignEditor &&
        this.currentSong &&
        this.currentSong.assignment &&
        this.currentSong.assignment.assigneeId === this.userId
      )
    },
  },

  watch: {
    '$route.query.id': {
      immediate: true,
      handler(id) {
        if (id) this.loadSong(id)
      },
    },
    currentSong: {
      handler(song) {
        if (song) document.title = `${song.songName} — ${song.author}`
        document.body.style.background = song?.contentRemoved ? 'var(--km-bg)' : ''
        if (song?.id) {
          // US6: если в sessionStorage есть активная share-сессия для этой песни — передаём её
          // хеш в /access, чтобы бэкенд выдал canWatch=true (гость, не премиум, см. spec.md FR-050).
          const shareSession =
            typeof sessionStorage !== 'undefined'
              ? sessionStorage.getItem(`kp_share_session_${song.id}`) || null
              : null
          this.playerAccess.checkAccess(song.id, shareSession)
        }
        if (song?.id) this.playlistMembership.load([song.id])
        this.playerDisplayMode = 'embed'
      },
    },
  },
  mounted() {
    window.addEventListener('message', this.onPlayerMessage)
  },
  beforeUnmount() {
    document.body.style.background = ''
    window.removeEventListener('message', this.onPlayerMessage)
  },
  methods: {
    ...mapActions('songs', ['loadSong']),
    // FR-005/US2: клик по "Взять в работу" на странице конкретной песни. Защита от двойных кликов
    // через assigningSongId (UI) + атомарная транзакция на бэке (FR-006). На 409 — песня уже
    // занята (другой редактор успел раньше) — перезагружаем данные песни. На таймаут — НЕ трогаем
    // локальное состояние, можно повторить.
    async onSelfAssignClick() {
      if (!this.currentSong || this.assigningSongId === this.currentSong.id) return
      this.assigningSongId = this.currentSong.id
      try {
        const { status, body } = await apiAssignSelf(this.currentSong.id)
        if (status === 200 && body && body.ok) {
          // Оптимистичная подмена — assignment = своё, бэкенд вернёт то же при следующей загрузке.
          this.currentSong.assignment = {
            id: body.id,
            assigneeId: this.userId,
            assignedAt: null,
            adminStatus: 'open',
          }
          this.notify('Задание взято в работу — перейдите в «Мои задания»', 'success')
        } else if (status === 409) {
          this.currentSong.assignment = {
            id: 0,
            assigneeId: 0,
            assignedAt: null,
            adminStatus: 'taken',
          }
          this.notify('Эта песня уже занята другим редактором', 'warning')
        } else if (status === 403) {
          this.notify('У вас нет права брать песни — обратитесь к администратору', 'error')
        } else {
          this.notify(
            'Не удалось взять песню: ' + (body && body.error ? body.error : 'ошибка сервера'),
            'error',
          )
        }
      } catch (e) {
        this.notify('Не удалось взять песню — проверьте интернет и попробуйте снова', 'error')
        console.warn('[song/self-assign] network error', e)
      } finally {
        this.assigningSongId = null
      }
    },
    // FR-007/US2 var.A: своё задание — открываем редактор (онлайн-разметку для этой песни).
    onOpenAssignmentClick() {
      if (!this.currentSong || !this.currentSong.assignment || !this.currentSong.assignment.id)
        return
      this.$router.push(`/account/editor/${this.currentSong.assignment.id}`)
    },
    /** Общий нотификатор (тосты в углу). Простая лёгкая обёртка — без зависимости от bootstrap. */
    notify(message, kind) {
      if (!this.$bvToast) {
        window.alert(message)
        return
      }
      this.$bvToast.toast(message, {
        title: kind === 'error' ? 'Ошибка' : kind === 'warning' ? 'Внимание' : 'Готово',
        variant: kind === 'error' ? 'danger' : kind === 'warning' ? 'warning' : 'success',
        solid: true,
        noAutoHide: false,
        autoHideDelay: 4000,
      })
    },
    // Player card starts embedded in the page; the player itself (running same-origin inside the
    // iframe) posts here when its "Широкий" button is toggled, asking us to resize the iframe's own
    // box between the small embedded card and a full-viewport overlay. Sourced-checked against our
    // own iframe's contentWindow so unrelated postMessage traffic (browser extensions etc.) is ignored.
    onPlayerMessage(event) {
      if (
        !event.data ||
        event.data.source !== 'karaoke-player' ||
        event.data.type !== 'display-mode'
      )
        return
      const iframe = this.$refs.playerIframe
      if (iframe && event.source !== iframe.contentWindow) return
      this.playerDisplayMode = event.data.mode
    },
    onPlay(version) {
      trackPlay(this.currentSong.id, version)
    },
    async onMetaClick(field, event) {
      const resp = await trackMetaClick(field, this.currentSong.id, event)
      if (resp && resp.meta) {
        sessionStorage.setItem(`kp_token_${this.currentSong.id}`, resp.meta)
        // New tab, not router.push: the player needs the full viewport (position:fixed inside it
        // isn't enough — it still inherits App.vue's .modernScreen wrapper otherwise) and a fresh
        // tab keeps the song page as-is behind it. sessionStorage is cloned into same-origin tabs
        // opened this way, so the token set just above is already there when it loads.
        window.open(`/player/${this.currentSong.id}`, '_blank')
      }
    },
    // Акция довела цену подписки на песню до нуля — доступ уже проставлен на бэкенде синхронно,
    // просто перезапрашиваем access(), чтобы плеер встроился без перезагрузки страницы.
    onSongSubscriptionActivated() {
      const id = this.currentSong?.id
      if (id) this.playerAccess.checkAccess(id)
      if (id && this.cart.isInCart(id)) this.cart.toggle(id)
    },
  },
}
</script>

<style scoped>
.km-page {
  min-height: 100vh;
  background: var(--km-bg);
  color: var(--km-text);
}

/* Хедер */
.km-header {
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
  position: sticky;
  top: 0;
  z-index: 100;
}
.km-header-inner {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.km-header-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.km-back {
  color: var(--km-accent);
  text-decoration: none;
  font-size: 0.85rem;
  white-space: nowrap;
}
.km-back:hover {
  text-decoration: underline;
}
.km-logo {
  height: 36px;
  width: auto;
}
.km-header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.km-theme-toggle {
  display: flex;
  border: 1px solid var(--km-border);
  border-radius: 20px;
  overflow: hidden;
}
.km-tb {
  background: transparent;
  color: var(--km-text2);
  border: none;
  padding: 0.2rem 0.55rem;
  font-size: 0.95rem;
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
}
.km-tb:hover {
  background: var(--km-hover);
  color: var(--km-text);
}
.km-tb.active {
  background: var(--km-accent);
  color: #fff;
}

/* Loading / not found */
.km-loading,
.km-not-found {
  text-align: center;
  color: var(--km-text2);
  padding: 4rem 1rem;
}

/* Страница «удалено» */
.km-removed-wrapper {
  min-height: calc(100vh - 60px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}
.km-removed-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 16px;
  padding: 3rem 3.5rem;
  max-width: 540px;
  width: 100%;
  text-align: center;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.4);
}
.km-removed-icon {
  font-size: 4rem;
  margin-bottom: 1.25rem;
  display: block;
  line-height: 1;
}
.km-removed-title {
  color: var(--km-text);
  font-size: 1.4rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}
.km-removed-subtitle {
  color: var(--km-text2);
  font-size: 1rem;
  margin-bottom: 1.5rem;
}
.km-removed-divider {
  border: none;
  border-top: 1px solid var(--km-border);
  margin: 1.25rem 0;
}
.km-removed-hint {
  color: var(--km-text2);
  font-size: 0.82rem;
  margin-bottom: 2rem;
  line-height: 1.6;
}
.km-btn-home {
  background: var(--km-bg2);
  border: 1px solid var(--km-border);
  color: var(--km-text);
  border-radius: 8px;
  padding: 0.55rem 1.8rem;
  font-size: 0.95rem;
  text-decoration: none;
  display: inline-block;
  transition: background 0.2s;
}
.km-btn-home:hover {
  background: var(--km-hover);
  color: var(--km-text);
  text-decoration: none;
}

/* Song content */
.km-song {
}

/* Hero */
.km-hero {
  position: relative;
  background: #000;
  max-height: 320px;
  overflow: hidden;
}
.km-hero-banner {
  width: 100%;
  max-height: 320px;
  object-fit: cover;
  opacity: 0.55;
  display: block;
}
.km-hero-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 1.5rem 1.5rem 1.2rem;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.85) 0%, transparent 100%);
}
.km-song-title {
  font-size: clamp(1.4rem, 4vw, 2.4rem);
  font-weight: 700;
  color: var(--km-accent2);
  margin: 0 0 0.25rem;
  line-height: 1.2;
}
.km-song-author {
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.8);
  margin: 0;
}

/* Content area */
.km-content {
  max-width: 900px;
  margin: 0 auto;
  padding: 1.25rem 1rem 3rem;
}

/* Метаданные */
.km-meta-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1rem 1.25rem;
  margin-bottom: 1rem;
}
.km-meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 0.75rem;
}
/* specs/012-entity-description-fields: предупреждение (красным), короткое описание (серым),
   описание (в тултипе через иконку ⓘ) — над сеткой тональности/темпа, в том же блоке информации. */
.km-song-notes {
  margin-bottom: 0.75rem;
}
.km-warning-text {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--km-danger, #dc3545);
  text-transform: uppercase;
  margin-bottom: 0.2rem;
}
.km-song-note-line {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.km-short-description-text {
  font-size: 0.85rem;
  color: var(--km-text2);
}
.km-info-icon {
  cursor: help;
  color: var(--km-text2);
  font-size: 0.9rem;
}
.km-meta-item {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}
.km-meta-label {
  font-size: 0.7rem;
  color: var(--km-text2);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}
.km-meta-value {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--km-text);
}

/* Избранное / плейлисты — в той же сетке карточки метаданных, без отдельного блока */
.km-meta-actions {
  grid-column: span 2;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  align-self: end;
  gap: 0.6rem;
}
.km-meta-actions :deep(.fav-icon),
.km-meta-actions :deep(.pl-icon) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0.4rem 0.85rem;
  background: var(--km-bg);
  border: 1px solid var(--km-border);
  border-radius: 999px;
  color: var(--km-text);
  font-size: 0.82rem;
  font-weight: 600;
  text-decoration: none;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.km-meta-actions :deep(.fav-icon:hover),
.km-meta-actions :deep(.pl-icon:hover) {
  border-color: var(--km-accent);
}
.km-meta-actions :deep(.fav-icon.fav-on) {
  color: #e11d2a;
  border-color: #e11d2a;
}
.km-meta-actions :deep(.pl-icon.pl-on) {
  color: #0077ff;
  border-color: #0077ff;
}
.km-meta-actions :deep(.share-trigger) {
  padding: 0.4rem 0.85rem;
  font-size: 0.82rem;
  font-weight: 600;
  border-radius: 999px;
  background: var(--km-bg);
  color: var(--km-text);
  border-color: var(--km-border);
}
.km-meta-actions :deep(.share-trigger:hover) {
  border-color: var(--km-accent);
}

/* Self-assign (FR-005/US2, specs/182-editor-self-assign-tasks): кнопка в карточке песни. */
.km-self-assign-btn {
  appearance: none;
  background: var(--km-accent);
  color: #fff;
  border: 1px solid var(--km-accent);
  border-radius: 6px;
  padding: 6px 14px;
  font-size: 0.95em;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.15s ease;
  margin-left: 0.5rem;
}
.km-self-assign-btn:hover:not(:disabled) {
  opacity: 0.85;
}
.km-self-assign-btn:disabled {
  opacity: 0.6;
  cursor: progress;
}
.km-self-assign-open-btn {
  appearance: none;
  background: transparent;
  color: var(--km-text);
  border: 1px solid var(--km-accent);
  border-radius: 6px;
  padding: 6px 14px;
  font-size: 0.95em;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.15s ease;
  margin-left: 0.5rem;
}
.km-self-assign-open-btn:hover {
  opacity: 0.7;
}

/* Онлайн-плеер, встроенный вместо видео ВК */
.km-player-card {
  margin-bottom: 1rem;
  border: 1px solid var(--km-accent);
  border-radius: 12px;
  padding: 0.75rem;
  background: var(--km-card);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--km-accent) 15%, transparent);
}
.km-player-label {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--km-accent2);
  margin-bottom: 0.5rem;
  letter-spacing: 0.02em;
}
.km-player-demo-badge {
  display: inline-block;
  margin-left: 0.5rem;
  padding: 0.1rem 0.4rem;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #fff;
  background: #f80;
  border-radius: 4px;
  vertical-align: middle;
}
.km-player-wrap {
  border-radius: 8px;
}

/* "Широкий" режим — плеер (внутри iframe) сам попросил родительскую страницу растянуть его на весь
   вьюпорт вместо маленькой карточки. position:fixed игнорирует max-width родительских .km-content
   (тот не создаёт containing block), так что этого достаточно, без переноса в другое место DOM. */
.km-player-card.km-player-page-mode {
  position: fixed;
  inset: 0;
  z-index: 2000;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 0;
  box-shadow: none;
  background: #000;
}
.km-player-card.km-player-page-mode .km-player-label {
  display: none;
}
.km-player-card.km-player-page-mode .km-video-wrap {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  aspect-ratio: unset;
  border-radius: 0;
}

/* Ожидание / предложение подписки */
.km-waiting-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 1rem;
  text-align: center;
}
.km-waiting-title {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--km-text);
  margin-bottom: 0.5rem;
}
.km-waiting-body {
  color: var(--km-text2);
  font-size: 0.95rem;
  margin-bottom: 1rem;
  line-height: 1.5;
}
.km-waiting-offer {
  background: var(--km-bg2);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1.25rem 1rem;
  margin-bottom: 0.75rem;
}
.km-waiting-offer-icon {
  font-size: 2rem;
  margin-bottom: 0.35rem;
  line-height: 1;
}
.km-waiting-offer-title {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--km-text);
  margin-bottom: 0.2rem;
}
.km-waiting-offer-desc {
  font-size: 0.85rem;
  color: var(--km-text2);
  margin-bottom: 0.9rem;
}
.km-waiting-offer-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.6rem;
}
.km-waiting-cta {
  display: inline-block;
  background: var(--km-accent);
  color: #fff;
  border-radius: 8px;
  padding: 0.6rem 1.4rem;
  font-weight: 600;
  text-decoration: none;
  border: none;
  cursor: pointer;
  font-family: inherit;
  font-size: 0.9rem;
}
.km-waiting-cta:hover {
  opacity: 0.9;
  color: #fff;
  text-decoration: none;
}
.km-song-sub-cta {
  background: transparent;
  color: var(--km-accent);
  border: 1px solid var(--km-accent);
}
.km-song-sub-cta:hover {
  background: var(--km-hover);
  color: var(--km-accent);
  opacity: 1;
}
.km-waiting-login {
  font-size: 0.82rem;
  color: var(--km-text2);
  margin-top: 0.5rem;
}
.km-waiting-login a {
  color: var(--km-accent2);
}

/* Видео */
.km-videos {
  margin-bottom: 1rem;
}
.km-video-block {
  margin-bottom: 1.25rem;
}
.km-video-label {
  font-size: 0.78rem;
  color: var(--km-text2);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
  margin-bottom: 0.4rem;
}
.km-video-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 10px;
  overflow: hidden;
  background: #000;
}
.km-video-wrap iframe {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

/* Текст песни */
.km-text-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1rem 1.25rem;
  margin-bottom: 1rem;
}
.km-text-header {
  font-size: 0.8rem;
  color: var(--km-text2);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
  margin-bottom: 0.75rem;
}
.km-text-body {
  font-size: 1rem;
  line-height: 1.7;
  color: var(--km-text);
  white-space: pre-wrap;
  font-family: monospace;
}
.km-text-body :deep(*) {
  color: var(--km-text) !important;
}

/* Мобильные правки */
@media (max-width: 600px) {
  .km-hero {
    max-height: 220px;
  }
  .km-hero-banner {
    max-height: 220px;
  }
  .km-removed-card {
    padding: 2rem 1.5rem;
  }
}
</style>
