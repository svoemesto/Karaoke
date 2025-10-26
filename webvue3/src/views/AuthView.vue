<!-- file: src/views/AuthView.vue -->
<template>
  <div class="auth-container">
    <h1>{{ isLogin ? 'Login' : 'Register' }}</h1>

    <!-- Форма Регистрации -->
    <form v-if="!isLogin" @submit.prevent="handleRegister" class="auth-form">
      <div class="form-group">
        <label for="regLogin">Login:</label>
        <input type="text" id="regLogin" v-model="registerForm.login" required />
      </div>
      <div class="form-group">
        <label for="regEmail">Email:</label>
        <input type="email" id="regEmail" v-model="registerForm.email" required />
      </div>
      <div class="form-group">
        <label for="regFirstName">First Name:</label>
        <input type="text" id="regFirstName" v-model="registerForm.firstName" required />
      </div>
      <div class="form-group">
        <label for="regLastName">Last Name:</label>
        <input type="text" id="regLastName" v-model="registerForm.lastName" required />
      </div>
      <div class="form-group">
        <label for="regPassword">Password:</label>
        <input type="password" id="regPassword" v-model="registerForm.password" required />
      </div>
      <div class="form-group">
        <label for="regPasswordConfirm">Confirm Password:</label>
        <input type="password" id="regPasswordConfirm" v-model="registerForm.passwordConfirm" required />
      </div>
      <button type="submit" :disabled="isProcessing">Register</button>
    </form>

    <!-- Форма Логина -->
    <form v-else @submit.prevent="handleLoginSubmit" class="auth-form"> <!-- Изменено имя метода -->
      <div class="form-group">
        <label for="login">Login:</label>
        <input type="text" id="login" v-model="loginForm.login" required />
      </div>
      <div class="form-group">
        <label for="password">Password:</label>
        <input type="password" id="password" v-model="loginForm.password" required />
      </div>
      <!-- Кнопка "Login via OAuth2" -->
      <button type="button" @click="handleOAuth2Login" :disabled="isProcessing">Login via OAuth2</button>
      <!-- Или можно заменить стандартную кнопку логина -->
      <!-- <button type="submit" :disabled="isProcessing">Login (API)</button> -->
      <button type="submit" :disabled="isProcessing">Login (API)</button> <!-- Оставляем старую кнопку -->
    </form>

    <div class="toggle-form">
      <p>
        {{ isLogin ? "Don't have an account?" : "Already have an account?" }}
        <a href="#" @click.prevent="toggleForm">{{ isLogin ? 'Register' : 'Login' }}</a>
      </p>
    </div>

    <div v-if="message" :class="['message', messageType]">
      {{ message }}
    </div>
  </div>
</template>

<script>
import axios from 'axios';
import { promisedXMLHttpRequest } from "../lib/utils.js";
// --- ИМПОРТИРУЕМ AuthService ---
import AuthService from '../services/AuthService'; // Убедитесь, что путь правильный
// ---

export default {
  name: 'AuthView',
  data() {
    return {
      isLogin: true,
      registerForm: {
        login: '',
        email: '',
        firstName: '',
        lastName: '',
        password: '',
        passwordConfirm: ''
      },
      loginForm: {
        login: '',
        password: ''
      },
      message: '',
      messageType: '',
      // --- Добавим флаг для обработки ---
      isProcessing: false
      // ---
    };
  },
  methods: {
    toggleForm() {
      if (!this.isProcessing) { // Не переключаем, если идет обработка
        this.isLogin = !this.isLogin;
        this.clearMessage();
      }
    },
    async handleRegister() {
      if (this.isProcessing) return; // Защита от двойного клика
      this.isProcessing = true;
      this.clearMessage();
      const { login, email, firstName, lastName, password, passwordConfirm } = this.registerForm;

      if (password !== passwordConfirm) {
        this.showMessage('Passwords do not match.', 'error');
        this.isProcessing = false;
        return;
      }

      try {
        const response = await promisedXMLHttpRequest({
          method: 'POST',
          url: '/api/users/create',
          params: {
            login: login,
            password: password,
            passwordConfirm: passwordConfirm,
            email: email,
            firstName: firstName,
            lastName: lastName
          }
        });

        if (response > 0) {
          this.showMessage('Registration successful! Redirecting to login...', 'success');
          // Очищаем форму
          this.registerForm = {
            login: '',
            email: '',
            firstName: '',
            lastName: '',
            password: '',
            passwordConfirm: ''
          };
          // Переключаемся на форму логина
          this.isLogin = true;
          // Необязательно: сразу попытаться залогинить
          // this.loginForm.login = login;
          // this.loginForm.password = password; // Не храните пароль в открытом виде в форме!
          // await this.handleLogin(); // Это может быть нежелательно
        } else {
          this.showMessage('Registration failed. User might already exist.', 'error');
        }
      } catch (error) {
        console.error('Registration error:', error);
        this.showMessage('Registration failed: ' + (error.response?.data?.error || error.message), 'error');
      } finally {
        this.isProcessing = false;
      }
    },
    // --- НОВЫЙ метод для обработки OAuth2 логина ---
    async handleOAuth2Login() {
      if (this.isProcessing) return;
      this.isProcessing = true;
      this.clearMessage();
      try {
        console.log('Initiating OAuth2 login...');
        this.showMessage('Redirecting to authorization server...', 'success');
        // --- Инициируем OAuth2 Authorization Code Flow ---
        // Это перенаправит браузер на страницу логина Authorization Server
        await AuthService.signinRedirect();
        // Код после этой строки не выполнится из-за редиректа
        // Если мы здесь, значит, что-то пошло не так
        console.warn('AuthService.signinRedirect did not redirect.');
        this.showMessage('Failed to initiate OAuth2 login. Please try again.', 'error');
      } catch (error) {
        console.error('OAuth2 login error:', error);
        this.showMessage('OAuth2 login failed: ' + (error.message || 'Unknown error'), 'error');
      } finally {
        this.isProcessing = false;
      }
    },
    // --- Обновлённый метод для обработки логина через API (без OAuth2) ---
    async handleLoginSubmit() { // Переименован с handleLogin
      if (this.isProcessing) return; // Защита от двойного клика
      this.isProcessing = true;
      this.clearMessage();
      const { login, password } = this.loginForm;

      try {
        // Проверяем логин/пароль через ваш API (не OAuth2)
        const response = await promisedXMLHttpRequest({
          method: 'POST',
          url: '/api/users/checkPassword',
          params: { login: login, password: password }
        });

        if (response === 'true') {
          // --- ВАЖНО: Теперь запускаем OAuth2 Authorization Code Flow ---
          // Вместо сообщения "Login successful", мы инициируем OAuth2 flow
          console.log('Проверка пароля по базе данных пройдена.');
          this.showMessage('Verifying credentials...', 'success');
          // AuthService.signinRedirect() перенаправит браузер на сервер авторизации
          await AuthService.signinRedirect();
          // Код после этой строки не выполнится из-за редиректа
        } else {
          console.log('Проверка пароля по базе данных не пройдена.');
          this.showMessage('Login failed. Invalid credentials.', 'error');
        }
      } catch (error) {
        console.error('Login error:', error);
        this.showMessage('Login failed: ' + (error.response?.data?.error || error.message), 'error');
      } finally {
        this.isProcessing = false;
      }
    },
    clearMessage() {
      this.message = '';
      this.messageType = '';
    },
    showMessage(text, type) {
      this.message = text;
      this.messageType = type;
    }
  },
};
</script>

<style scoped>
/* ... (остальные стили остаются без изменений) ... */
.auth-container {
  max-width: 400px;
  margin: 50px auto;
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 5px;
  background-color: #f9f9f9;
}

.auth-form .form-group {
  margin-bottom: 15px;
}

.auth-form label {
  display: block;
  margin-bottom: 5px;
  font-weight: bold;
}

.auth-form input {
  width: 100%;
  padding: 8px;
  box-sizing: border-box;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.auth-form button {
  width: 100%;
  padding: 10px;
  background-color: #007bff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
  margin-bottom: 10px; /* Добавлен отступ между кнопками */
}

.auth-form button:hover {
  background-color: #0056b3;
}

.toggle-form {
  margin-top: 20px;
  text-align: center;
}

.toggle-form a {
  color: #007bff;
  text-decoration: none;
}

.toggle-form a:hover {
  text-decoration: underline;
}

.message {
  margin-top: 15px;
  padding: 10px;
  border-radius: 4px;
  text-align: center;
}

.message.error {
  background-color: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
}

.message.success {
  background-color: #d4edda;
  color: #155724;
  border: 1px solid #c3e6cb;
}
</style>