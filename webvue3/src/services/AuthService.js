// src/services/AuthService.js
import { UserManager, WebStorageStateStore } from 'oidc-client-ts';

const settings = {
    authority: 'http://localhost:7906', // Авторизационный сервер
    client_id: 'vue-client',
    redirect_uri: 'http://localhost:7906/callback', // Куда направить после авторизации
    response_type: 'code',
    scope: 'openid profile email api:read api:write',
    userStore: new WebStorageStateStore({ store: window.sessionStorage })
};

class AuthService {
    userManager;

    constructor() {
        this.userManager = new UserManager(settings);
    }

    // Начать процесс авторизации
    signinRedirect = () => {
        return this.userManager.signinRedirect();
    };

    // Обработать callback после редиректа
    signinCallback = () => {
        return this.userManager.signinCallback();
    };

    // Выход из системы
    signoutRedirect = () => {
        return this.userManager.signoutRedirect();
    };

    // Получить текущего пользователя
    getUser = () => {
        return this.userManager.getUser();
    };

    // Получение токена для запросов к API
    getAccessToken = async () => {
        const user = await this.getUser();
        return user?.access_token;
    };
}

export default new AuthService();