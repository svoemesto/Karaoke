/**
 * Music theory utilities for transposed key name calculation.
 *
 * @see docs/features/audio-transpose.md
 */

const MAJOR_KEYS = ['C', 'C♯', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B']

const MINOR_KEYS = ['A', 'A♯', 'B', 'C', 'C♯', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'G♯']

/**
 * Parse a base key string into chromatic index and mode.
 *
 * @param baseKey - e.g. "C major", "A minor", "F♯ major"
 * @returns Object with index (0=C/A, 1=C♯/A♯, ...) and mode ('major' | 'minor')
 */
function parseKey(baseKey) {
  if (!baseKey || typeof baseKey !== 'string') return null

  const normalized = baseKey.trim().toLowerCase()
  const parts = normalized.split(/\s+/)
  if (parts.length < 2) return null

  const notePart = parts[0]
  const modePart = parts[1]

  if (modePart !== 'major' && modePart !== 'minor') return null

  const arr = modePart === 'major' ? MAJOR_KEYS : MINOR_KEYS
  const index = arr.findIndex((k) => k.toLowerCase() === notePart)

  if (index === -1) return null

  return { index, mode: modePart }
}

/**
 * Get the transposed key name given a base key and semitone offset.
 *
 * @param baseKey - e.g. "C major" or "A minor"
 * @param offset - Integer semitone offset (-6 … +6)
 * @returns Transposed key name or null if baseKey is unknown
 */
export function getTransposedKeyName(baseKey, offset) {
  const parsed = parseKey(baseKey)
  if (!parsed) return null

  const newIndex = (parsed.index + offset + 12) % 12
  const arr = parsed.mode === 'major' ? MAJOR_KEYS : MINOR_KEYS
  const noteName = arr[newIndex]

  return `${noteName} ${parsed.mode}`
}

/**
 * Get all transpose options with key names for a given base key.
 *
 * @param baseKey - e.g. "C major"
 * @returns Array of { offset, label } for -6 … +6
 */
export function getTransposeOptions(baseKey) {
  const options = []

  for (let offset = -6; offset <= 6; offset++) {
    const keyName = getTransposedKeyName(baseKey, offset)
    const label = keyName
      ? `${offset > 0 ? '+' : ''}${offset} → ${keyName}`
      : `${offset > 0 ? '+' : ''}${offset}`
    options.push({ offset, label })
  }

  return options
}
