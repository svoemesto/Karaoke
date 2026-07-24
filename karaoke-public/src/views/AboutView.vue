<template>
  <div class="km-page">
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/" class="km-back">← Главная</RouterLink>
          <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
        </div>
      </div>
    </header>

    <div class="km-content">
      <!-- Hero-блок: главный акцент -->
      <section class="km-hero">
        <h1 class="km-hero-title">Полная дискография. Не сборник хитов.</h1>
        <p class="km-hero-sub">
          Если в нашей коллекции есть автор — там есть <strong>все его песни</strong>: официальные
          релизы, концертные записи, бонус-треки. Не «пара-тройка самых популярных», как на других
          karaoke-сайтах, а <strong>полная дискография</strong> — насколько хватит материалов в
          открытом доступе.
        </p>
        <p class="km-hero-examples">
          Например, у <strong>КИНО</strong> — все альбомы, все концерты, все редкие записи. У
          <strong>Агаты Кристи</strong> — оба сольных периода братьев Самойловых после распада
          группы. У <strong>Короля и Шута</strong> — все альбомы + акустика + поздние проекты
          Горшенева и Князева.
        </p>
      </section>

      <!-- Оглавление -->
      <h1 class="km-title">О проекте «Караоке на Своём Месте»</h1>
      <p class="km-lead">
        Караоке-видео с правильной разметкой текста — для тех, кто хочет петь по-настоящему.
      </p>

      <!-- Что это -->
      <section class="km-section">
        <p>
          Мы делаем <strong>настоящее караоке</strong> для русскоязычных песен: берём оригинальные
          треки, размечаем по слогам с точностью до 1/60 секунды, синхронизируем текст с музыкой.
          Воспроизведение — целиком в <strong>онлайн-плеере</strong> на нашем сайте.
        </p>
        <p>
          Каждая песня проходит через несколько стадий подготовки: аудио-анализ (BPM, тональность) и
          разделение на стемы (вокал, барабаны, бас, остальное — Demucs). Это не «караоке на
          коленке» — это полноценный продакшн.
        </p>
      </section>

      <!-- Какие песни -->
      <section class="km-section">
        <h2 class="km-section-title">Какие песни</h2>
        <p><strong>Преимущественно русский рок</strong> — от советской классики до наших дней.</p>
        <p>
          В коллекции уже готовы (и каждый день добавляются новые):
          <strong>15 топ-авторов</strong> (с полной дискографией, см. ниже) и ещё
          <strong>свыше 100 исполнителей</strong> — от АукцЫона до Ю-Питера.
        </p>
        <p>
          Всё это — на нашем сайте: полные версии для премиум-подписчиков и демо-фрагменты для
          бесплатного прослушивания.
        </p>
        <p class="km-hint-text">
          <strong>Чего у нас нет</strong> (пока): современного попа, хип-хопа, электроники,
          зарубежной музыки. Мы фокусируемся на том, что знаем и любим — на русском роке.
        </p>
      </section>

      <!-- Чем отличается -->
      <section class="km-section">
        <h2 class="km-section-title">Что умеет наш онлайн-плеер</h2>
        <ul class="km-features">
          <li>Подсвечивает текущий слог бегущим курсором</li>
          <li>Поддерживает переключение темпа (0.5×–3×)</li>
          <li>Запоминает место остановки</li>
        </ul>
        <p>
          Это не karaoke-фанера поверх YouTube — это отдельный видеоряд, синхронизированный с
          точностью до кадра.
        </p>
        <p class="km-important">
          <strong>Важно</strong>: всё воспроизведение — <strong>только онлайн на сайте</strong> (см.
          <RouterLink to="/oferta">оферту</RouterLink>). Файлы не передаются и не скачиваются —
          доступ для личного некоммерческого использования в браузере.
        </p>
      </section>

      <!-- Статистика — те же 4 категории, что и на главной (consistency) -->
      <section v-if="stats" class="km-stats-section">
        <h2 class="km-section-title">Коллекция</h2>
        <div class="km-stats-grid">
          <div class="km-stat-card">
            <div class="km-stat-number">{{ formatNum(stats.onSponsr) }}</div>
            <div class="km-stat-label">Песен в коллекции</div>
          </div>
          <div class="km-stat-card">
            <div class="km-stat-number">{{ formatNum(stats.onAir) }}</div>
            <div class="km-stat-label">В открытом доступе</div>
          </div>
          <div class="km-stat-card">
            <div class="km-stat-number">{{ formatNum(stats.exclusive) }}</div>
            <div class="km-stat-label">По подписке</div>
          </div>
          <div class="km-stat-card">
            <div class="km-stat-number">{{ formatNum(stats.inWork) }}</div>
            <div class="km-stat-label">В работе</div>
          </div>
        </div>
      </section>

      <!-- Топ-15 -->
      <section class="km-section">
        <h2 class="km-section-title">15 топ-авторов с полной дискографией</h2>
        <p class="km-hint-text">
          Каждый из этих исполнителей представлен в коллекции максимально полно (официальные релизы
          + концертные записи). Список ниже — отсортирован по алфавиту.
        </p>
        <div class="km-top-grid">
          <div v-for="author in topAuthorsList" :key="author.name" class="km-top-card">
            <div class="km-top-name">{{ author.name }}</div>
            <div v-if="author.note" class="km-top-note">{{ author.note }}</div>
          </div>
        </div>
      </section>

      <!-- Все исполнители (динамически) -->
      <section v-if="otherAuthors.length > 0" class="km-section">
        <h2 class="km-section-title">И ещё {{ otherAuthors.length }} исполнителей</h2>
        <p class="km-hint-text">
          Полный список — на странице каталога. Здесь — для затравки, в алфавитном порядке.
        </p>
        <div class="km-all-list">
          <span v-for="author in otherAuthors" :key="author" class="km-all-item">{{ author }}</span>
        </div>
      </section>

      <!-- CTA: что дальше -->
      <section class="km-cta-section">
        <h2 class="km-section-title">Что дальше?</h2>
        <p>Три способа начать:</p>
        <div class="km-cta-grid">
          <button class="km-cta-btn km-cta-secondary" @click="goToDemo">🎵 Послушать демо</button>
          <RouterLink to="/zakroma" class="km-cta-btn km-cta-secondary">
            📚 Открыть каталог
          </RouterLink>
          <RouterLink v-if="!isLoggedIn" to="/register" class="km-cta-btn km-cta-primary">
            🔖 Зарегистрироваться (бесплатно)
          </RouterLink>
          <RouterLink v-else to="/account" class="km-cta-btn km-cta-secondary">
            👤 В личный кабинет
          </RouterLink>
        </div>
        <p class="km-hint-text" style="margin-top: 1.5rem">
          Если хотите <strong>всю коллекцию без ограничений</strong> — оформите
          <RouterLink to="/premium">премиум-подписку</RouterLink> (449 ₽/мес). Одна песня навсегда —
          49 ₽.
        </p>
      </section>
    </div>
  </div>
</template>

<script>
import { apiGet } from '../services/api'
import { useAuth } from '../composables/useAuth'
import { useEngagementTracking } from '../composables/useEngagementTracking'
import { trackUi } from '../services/tracking'

// Топ-15 авторов с полной дискографией — список согласован с владельцем проекта.
// Эти 15 исполнителей представлены в коллекции максимально полно: официальные релизы +
// концертные записи. Hardcoded для MVP; в будущем можно вынести в tbl_authors.is_top boolean.
const TOP_AUTHORS = [
  { name: 'Агата Кристи', note: 'оба сольных периода братьев Самойловых' },
  { name: 'АлисА', note: 'Константин Кинчев' },
  { name: 'Ария', note: 'Валерий Кипелов и др.' },
  { name: 'ДДТ', note: 'Юрий Шевчук' },
  { name: 'Калинов Мост', note: 'Дмитрий Ревякин' },
  { name: 'КИНО', note: 'Виктор Цой' },
  { name: 'Король и Шут', note: 'Михаил Горшенёв, Андрей Князев' },
  { name: 'Ленинград', note: 'Сергей Шнуров' },
  { name: 'Мельница', note: 'Хелависа' },
  { name: 'Наутилус Помпилиус', note: 'Вячеслав Бутусов' },
  { name: 'Пикник', note: 'Эдмунд Шклярский' },
  { name: 'Сплин', note: 'Александр Васильев' },
  { name: 'Ундервуд', note: 'Ткаченко и Кучеренко' },
  { name: 'ЧайФ', note: 'Владимир Шахрин' },
  { name: 'Чиж & Co', note: 'Сергей Чиграков' },
]

/**
 * View-страница «О проекте» — описание проекта, топ-15, динамический список исполнителей.
 *
 * @see docs/strategy/growth.md — стратегия роста (QW-9 в роадмапе)
 * @see docs/strategy/about-page-draft.md — черновик контента страницы
 * @see docs/strategy/growth-audit.md — полный аудит (H1.9, H1.21, H1.22, H1.23)
 * @see AGENTS.md
 */
export default {
  name: 'AboutView',
  setup() {
    const { isLoggedIn } = useAuth()
    // Трекинг времени на странице + scroll-вехи 25/50/75/100% в tbl_events.
    useEngagementTracking('about')
    return { isLoggedIn }
  },
  data() {
    return {
      topAuthorsList: TOP_AUTHORS,
      allAuthors: [],
      stats: null,
      loading: false,
    }
  },
  computed: {
    // Все авторы, не вошедшие в топ-15, отсортированы по алфавиту.
    otherAuthors() {
      const topNames = new Set(TOP_AUTHORS.map((a) => a.name.toLowerCase()))
      return this.allAuthors
        .filter((name) => !topNames.has(name.toLowerCase()))
        .sort((a, b) => a.localeCompare(b, 'ru'))
    },
  },
  async mounted() {
    document.title = 'О проекте — Караоке на «Своём Месте»'
    await this.load()
  },
  methods: {
    formatNum(n) {
      return n != null ? Number(n).toLocaleString('ru-RU') : ''
    },
    async load() {
      this.loading = true
      try {
        // Параллельно: список авторов + статистика.
        const [authors, stats] = await Promise.all([
          apiGet('/api/public/authors'),
          apiGet('/api/public/stats').catch(() => null),
        ])
        this.allAuthors = Array.isArray(authors) ? authors : []
        this.stats = stats
      } catch (e) {
        // Не блокируем страницу — топ-15 и так видны.
        this.allAuthors = []
      }
      this.loading = false
    },
    goToDemo() {
      trackUi('navigate', 'about_cta_demo')
      this.$router.push('/zakroma')
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
.km-header {
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
}
.km-header-inner {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  align-items: center;
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
}
.km-logo {
  height: 36px;
  width: auto;
}
.km-content {
  max-width: 800px;
  margin: 0 auto;
  padding: 2rem 1rem 4rem;
}

/* Hero */
.km-hero {
  background: linear-gradient(135deg, var(--km-accent), var(--km-accent2, var(--km-accent)));
  color: #fff;
  border-radius: 16px;
  padding: 2.5rem 1.75rem;
  margin-bottom: 2.5rem;
  text-align: center;
}
.km-hero-title {
  font-size: 2rem;
  font-weight: 800;
  margin: 0 0 1rem;
  line-height: 1.2;
}
.km-hero-sub {
  font-size: 1.05rem;
  margin: 0 0 1rem;
  line-height: 1.5;
  opacity: 0.95;
}
.km-hero-examples {
  font-size: 0.95rem;
  margin: 1rem 0 0;
  line-height: 1.5;
  opacity: 0.85;
  font-style: italic;
}

/* Заголовок и лид */
.km-title {
  font-size: 1.5rem;
  font-weight: 700;
  margin: 0 0 0.5rem;
}
.km-lead {
  color: var(--km-text2);
  font-size: 1rem;
  margin: 0 0 2rem;
}

/* Секции */
.km-section {
  margin-bottom: 2.5rem;
}
.km-section-title {
  font-size: 1.2rem;
  font-weight: 700;
  margin: 0 0 1rem;
  color: var(--km-text);
}
.km-hint-text {
  font-size: 0.9rem;
  color: var(--km-text2);
  line-height: 1.5;
}
.km-important {
  font-size: 0.9rem;
  color: var(--km-text);
  background: var(--km-hover);
  border-left: 3px solid var(--km-accent);
  border-radius: 6px;
  padding: 0.75rem 1rem;
  margin: 1rem 0 0;
  line-height: 1.5;
}
.km-features {
  list-style: none;
  padding: 0;
  margin: 0 0 1rem;
}
.km-features li {
  position: relative;
  padding-left: 1.5rem;
  margin-bottom: 0.5rem;
  font-size: 0.95rem;
  line-height: 1.5;
}
.km-features li::before {
  content: '✓';
  position: absolute;
  left: 0;
  color: var(--km-accent);
  font-weight: 700;
}

/* Статистика */
.km-stats-section {
  margin-bottom: 2.5rem;
  padding: 1.5rem;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
}
.km-stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}
.km-stat-card {
  text-align: center;
}
.km-stat-number {
  font-size: 1.8rem;
  font-weight: 800;
  color: var(--km-accent);
  line-height: 1.1;
}
.km-stat-label {
  font-size: 0.8rem;
  color: var(--km-text2);
  margin-top: 0.3rem;
}

/* Топ-15 */
.km-top-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.75rem;
}
.km-top-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 10px;
  padding: 0.75rem 1rem;
}
.km-top-name {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--km-text);
  margin-bottom: 0.2rem;
}
.km-top-note {
  font-size: 0.78rem;
  color: var(--km-text2);
  line-height: 1.3;
}

/* Все авторы (динамически) */
.km-all-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.km-all-item {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 6px;
  padding: 0.3rem 0.7rem;
  font-size: 0.85rem;
  color: var(--km-text);
}

/* CTA */
.km-cta-section {
  margin-top: 3rem;
  padding: 2rem 1.5rem;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 14px;
  text-align: center;
}
.km-cta-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.75rem;
  margin: 1.5rem 0 0;
}
.km-cta-btn {
  display: block;
  text-align: center;
  padding: 0.9rem 1rem;
  border-radius: 10px;
  font-size: 0.95rem;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  border: 1px solid transparent;
  background: transparent;
}
.km-cta-primary {
  background: var(--km-accent);
  color: #fff;
}
.km-cta-primary:hover {
  filter: brightness(1.1);
}
.km-cta-secondary {
  background: transparent;
  color: var(--km-accent);
  border-color: var(--km-accent);
}
.km-cta-secondary:hover {
  background: var(--km-hover);
}

/* Mobile */
@media (max-width: 700px) {
  .km-hero {
    padding: 1.5rem 1rem;
  }
  .km-hero-title {
    font-size: 1.5rem;
  }
  .km-hero-sub,
  .km-hero-examples {
    font-size: 0.9rem;
  }
  .km-stats-grid,
  .km-top-grid,
  .km-cta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
