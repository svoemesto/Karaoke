<template>
  <div class="usm-overlay" @click.self="close">
    <div class="usm-modal">
      <div class="usm-head">
        <div class="usm-title">Подписки/покупки: {{ userLabel }}</div>
        <button class="usm-close" @click="close">×</button>
      </div>

      <div v-if="isLoading" class="usm-loading">Загрузка...</div>
      <template v-else>
        <table class="usm-table">
          <thead>
            <tr>
              <th>Дата</th>
              <th>Тип</th>
              <th>Название</th>
              <th>Сумма</th>
              <th>Статус</th>
              <th>Автопрод.</th>
              <th>Оплачено</th>
              <th>Заказ</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="sub in subscriptions" :key="sub.id">
              <td class="usm-nowrap">{{ formatDate(sub.createdAt) }}</td>
              <td>{{ sub.scope === 'SITE' ? 'Сайт' : 'Песня' }}</td>
              <td class="usm-left">
                {{
                  sub.scope === 'SITE'
                    ? sub.tariffName || `тариф #${sub.tariffId}`
                    : sub.songName || `песня #${sub.idSong}`
                }}
              </td>
              <td class="usm-nowrap">
                {{ sub.finalPrice }}₽
                <span
                  v-if="sub.discount > 0"
                  class="usm-hint"
                  :title="`Базовая цена ${sub.basePrice}₽, скидка ${sub.discount}₽${sub.promoApplied ? ' (' + sub.promoApplied + ')' : ''}`"
                  >*</span
                >
              </td>
              <td :class="statusClass(sub.status)">{{ statusLabel(sub.status) }}</td>
              <td>{{ sub.scope === 'SITE' ? (sub.autoRenew ? 'Да' : 'Нет') : '—' }}</td>
              <td class="usm-nowrap">{{ sub.paidAt ? formatDate(sub.paidAt) : '—' }}</td>
              <td class="usm-nowrap" :title="sub.orderId || ''">
                {{ sub.orderId ? sub.orderId.slice(0, 8) : '—' }}
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!subscriptions.length" class="usm-empty">Подписок и покупок нет</div>
      </template>
    </div>
  </div>
</template>

<script>
/**
 * Модальное окно для subscriptions.
 *
 * @emits close
 *
 * @see AGENTS.md
 */
export default {
  name: 'UserSubscriptionsModal',
  props: {
    siteUserId: { type: Number, required: true },
    userLabel: { type: String, default: '' },
    target: { type: String, default: 'local' },
  },
  emits: ['close'],
  computed: {
    subscriptions() {
      return this.$store.getters.getSiteUserSubscriptions
    },
    isLoading() {
      return this.$store.getters.getSiteUserSubscriptionsIsLoading
    },
  },
  async mounted() {
    await this.$store.dispatch('setSiteUsersTarget', this.target)
    this.$store.dispatch('loadSiteUserSubscriptions', this.siteUserId)
  },
  methods: {
    statusLabel(status) {
      switch (status) {
        case 'PAID':
          return 'Оплачено'
        case 'PENDING':
          return 'Ожидание'
        case 'CREATED':
          return 'Создано'
        case 'FAILED':
          return 'Ошибка'
        case 'REFUNDED':
          return 'Возврат'
        case 'CANCELED':
          return 'Отменено'
        default:
          return status
      }
    },
    statusClass(status) {
      if (status === 'PAID') return 'usm-status-ok'
      if (status === 'FAILED' || status === 'CANCELED') return 'usm-status-bad'
      return 'usm-status-neutral'
    },
    formatDate(ts) {
      if (!ts) return '—'
      return new Date(ts.replace(' ', 'T')).toLocaleString('ru-RU', {
        timeZone: 'Europe/Moscow',
        day: '2-digit',
        month: '2-digit',
        year: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
    },
    close() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.usm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1070;
}
.usm-modal {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  width: 860px;
  max-width: 95vw;
  max-height: 88vh;
  overflow-y: auto;
  font-size: small;
}
.usm-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}
.usm-title {
  font-size: 1.05rem;
  font-weight: 700;
}
.usm-close {
  background: transparent;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  line-height: 1;
}
.usm-loading,
.usm-empty {
  text-align: center;
  color: gray;
  padding: 20px;
}
.usm-table {
  width: 100%;
  border-collapse: collapse;
}
.usm-table th,
.usm-table td {
  border: 1px solid #ddd;
  padding: 4px 6px;
  text-align: center;
  font-size: small;
}
.usm-left {
  text-align: left;
}
.usm-nowrap {
  white-space: nowrap;
}
.usm-hint {
  color: gray;
  cursor: help;
}
.usm-status-ok {
  color: darkgreen;
}
.usm-status-bad {
  color: darkred;
}
.usm-status-neutral {
  color: #7a6300;
}
</style>
