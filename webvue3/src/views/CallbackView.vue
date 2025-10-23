<!-- src/views/CallbackView.vue -->
<template>
  <div class="callback-container">
    <h2>Processing Login...</h2>
    <p>Please wait while we complete the authentication process.</p>
  </div>
</template>

<script>
import AuthService from '../services/AuthService';

export default {
  name: 'CallbackView',
  async created() {
    try {
      // Вызываем signinCallback для обмена кода на токены
      await AuthService.signinCallback();
      // После успешного получения токенов, перенаправляем пользователя, например, на главную
      this.$router.push({ name: 'home' });
    } catch (error) {
      console.error('Error processing login callback:', error);
      // Обработка ошибки, например, перенаправление на страницу логина
      this.$router.push({ name: 'auth' });
    }
  },
};
</script>

<style scoped>
.callback-container {
  text-align: center;
  padding: 50px;
}
</style>