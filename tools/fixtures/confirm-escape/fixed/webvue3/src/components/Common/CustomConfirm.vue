<template>
  <div class="сс-modal-backdrop">
    <div class="сс-area">
      <div :style="styleHeader" v-if="params.allowHtml" v-html="params.header" />
      <div :style="styleHeader" v-else v-text="params.header" />
      <div :style="styleBody" v-if="params.allowHtml" v-html="params.body" />
      <div :style="styleBody" v-else v-text="params.body" />
    </div>
  </div>
</template>

<script>
/**
 * ФИКСТУРА (исправленная версия) — спека #558, OpenProject #360, BR-123.
 *
 * Общий компонент подтверждения после правки: по умолчанию текст, сырая
 * разметка — только при явном `params.allowHtml === true`.
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
