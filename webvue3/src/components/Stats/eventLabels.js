// Человекочитаемые подписи типов событий (значения tbl_events.event_type) для графиков и легенд.
export const TYPE_LABELS = {
  callRest: 'Просмотры страниц',
  clickToLink: 'Клики по ссылкам',
  play: 'Видео на странице',
  player: 'Онлайн-плеер',
  engagement: 'Время на странице',
  ui: 'UI-действия',
}

export function typeLabel(t) {
  return TYPE_LABELS[t] || t || 'неизвестно'
}

// Цвета серий по типу события — согласованы между всеми графиками дашборда.
export const TYPE_COLORS = {
  callRest: '#4e79a7',
  clickToLink: '#f28e2b',
  play: '#e15759',
  player: '#59a14f',
  engagement: '#76b7b2',
  ui: '#af7aa1',
}

export function typeColor(t) {
  return TYPE_COLORS[t] || '#9c9c9c'
}
