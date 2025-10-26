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
    <form v-else @submit.prevent="handleLoginSubmit" class="auth-form">
      <div class="form-group">
        <label for="login">Login:</label>
        <input type="text" id="login" v-model="loginForm.login" required />
      </div>
      <div class="form-group">
        <label for="password">Password:</label>
        <input type="password" id="password" v-model="loginForm.password" required />
      </div>
      <button type="submit" :disabled="isProcessing">Login (API)</button>
      <button type="button" @click="handleOAuth2Login" :disabled="isProcessing">Login via OAuth2</button>
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
import {promisedXMLHttpRequest} from "../lib/utils.js";
import AuthService from '../services/AuthService';

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
      isProcessing: false
    };
  },
  methods: {
    toggleForm() {
      if (!this.isProcessing) {
        this.isLogin = !this.isLogin;
        this.clearMessage();
      }
    },
    async handleRegister() {
      if (this.isProcessing) return;
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

        if (response.status === 200) {
          this.showMessage('Registration successful!', 'success');
          this.isLogin = true;
        } else {
          this.showMessage('Registration failed.', 'error');
        }
      } catch (error) {
        console.error('Registration error:', error);
        this.showMessage('Registration failed: ' + (error.response?.data?.error || error.message), 'error');
      } finally {
        this.isProcessing = false;
      }
    },
    async handleLoginSubmit() {
      if (this.isProcessing) return;
      this.isProcessing = true;
      this.clearMessage();
      const { login, password } = this.loginForm;

      try {
        const response = await promisedXMLHttpRequest({
          method: 'POST',
          url: '/api/users/checkPassword',
          params: { login: login, password: password }
        });
        if (response === 'true') {
          this.showMessage('Login successful!', 'success');
          this.$router.push('/');
        } else {
          this.showMessage('Login failed. Invalid credentials.', 'error');
        }
      } catch (error) {
        console.error('Login error:', error);
        this.showMessage('Login failed: ' + (error.response?.data?.error || error.message), 'error');
      } finally {
        this.isProcessing = false;
      }
    },
    async handleOAuth2Login() {
      if (this.isProcessing) return;
      this.isProcessing = true;
      try {
        await AuthService.signinRedirect(); // начинаем процесс авторизации
        this.showMessage('Redirecting to authorization server...', 'info');
      } catch (error) {
        console.error('Error during OAuth2 login:', error);
        this.showMessage('Failed to log in via OAuth2.', 'error');
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
  }
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