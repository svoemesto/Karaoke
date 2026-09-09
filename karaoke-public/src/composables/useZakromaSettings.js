/**
 * Composable для UI-настроек страниц Закромов (`/zakroma` и `/zakroma/{authorId}/albums`):
 * - размер плашек (200..400 px, шаг 50)
 * - режим отображения («Плашки» или « «Таблица»)
 *
 * Хранилище — `localStorage` (НЕ БД), т.к. это чисто клиентские UI-настройки
 * без серверной персонализации (см. спека 356, A-003).
 *
 * @see specs/356-zakroma-albums-by-author/spec.md FR-012, FR-013
 * @see docs/features/zakroma-albums-by-author.md
 */
import { ref, watch } from 'vue'

const TILE_SIZE_KEY = 'zakroma_tile_size'
const VIEW_MODE_KEY = 'zakroma_view_mode'

export const MIN_TILE_SIZE = 200
export const MAX_TILE_SIZE = 400
export const TILE_SIZE_STEP = 50
export const DEFAULT_TILE_SIZE = 200

export const VIEW_MODE_TILES = 'tiles'
export const VIEW_MODE_TABLE = 'table'
export const DEFAULT_VIEW_MODE = VIEW_MODE_TILES

/**
 * Singleton-ссылки на ref'ы, чтобы все потребители (ZakromaView,
 * ZakromaAlbumsView, ZakromaSettings) видели одно и то же состояние.
 * Vue 3 Composition API не имеет общего состояния «из коробки»,
 * поэтому используем module-level refs.
 */
const _tileSize = ref(loadFromStorage(TILE_SIZE_KEY, DEFAULT_TILE_SIZE, parseIntSafe))
const _viewMode = ref(
  loadFromStorage(VIEW_MODE_KEY, DEFAULT_VIEW_MODE, (v) =>
    [VIEW_MODE_TILES, VIEW_MODE_TABLE].includes(v) ? v : DEFAULT_VIEW_MODE,
  ),
)

// Синхронизация с localStorage
watch(
  _tileSize,
  (v) => {
    try {
      localStorage.setItem(TILE_SIZE_KEY, String(v))
    } catch {
      // localStorage недоступен (приватный режим) — тихо игнорируем
    }
  },
  { immediate: false },
)

watch(
  _viewMode,
  (v) => {
    try {
      localStorage.setItem(VIEW_MODE_KEY, v)
    } catch {
      // ignore
    }
  },
  { immediate: false },
)

function loadFromStorage(key, defaultValue, transform) {
  try {
    const raw = localStorage.getItem(key)
    if (raw === null) return defaultValue
    return transform(raw)
  } catch {
    return defaultValue
  }
}

function parseIntSafe(raw) {
  const n = parseInt(raw, 10)
  if (!Number.isFinite(n)) return DEFAULT_TILE_SIZE
  // Clamp в допустимый диапазон + выравнивание по шагу
  const clamped = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, n))
  const stepped =
    Math.round((clamped - MIN_TILE_SIZE) / TILE_SIZE_STEP) * TILE_SIZE_STEP + MIN_TILE_SIZE
  return stepped
}

/**
 * Composable hook — реактивные настройки + сеттеры.
 * @returns {{ tileSize: Ref<number>, setTileSize: (v: number) => void, viewMode: Ref<string>, setViewMode: (v: string) => void }}
 */
export function useZakromaSettings() {
  return {
    tileSize: _tileSize,
    setTileSize(v) {
      _tileSize.value = parseIntSafe(String(v))
    },
    viewMode: _viewMode,
    setViewMode(v) {
      _viewMode.value = [VIEW_MODE_TILES, VIEW_MODE_TABLE].includes(v) ? v : DEFAULT_VIEW_MODE
    },
  }
}
