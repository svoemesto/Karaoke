<template>
  <div class="сс-modal-backdrop">
    <div class="сс-area">
      <div :style="styleHeader" v-html="params.header" />
      <div :style="styleBody" v-html="params.body" />
    </div>
  </div>
</template>

<script>
/**
 * ФИКСТУРА (уязвимая версия) — спека #558, OpenProject #360, BR-123.
 *
 * Это снимок общего компонента подтверждения ДО правки: сырая разметка
 * принимается по умолчанию, текстового режима нет. Guard
 * tools/check-confirm-escape.py обязан отвергнуть этот файл (правило R1).
 */
export default {
  name: 'CustomConfirm',
  props: {
    params: {
      type: Object,
      required: true,
    },
  },
  computed: {
    styleHeader() {
      return { backgroundColor: 'darkslategray' }
    },
    styleBody() {
      return { backgroundColor: 'white' }
    },
  },
}
</script>
