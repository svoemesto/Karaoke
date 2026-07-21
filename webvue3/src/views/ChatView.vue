<template>
  <div class="chatview">
    <ChatPanel />
  </div>
</template>

<script>
import ChatPanel from '../components/Chat/ChatPanel.vue'

/**
 * View-страница «Chat» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */
export default {
  name: 'ChatView',
  components: {
    ChatPanel,
  },
}
</script>

<style scoped>
.chatview {
  display: flex;
  flex-direction: column;
  width: 100%;
}
</style>
