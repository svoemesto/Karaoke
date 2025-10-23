// src/services/AuthService.js
import { UserManager, WebStorageStateStore } from 'oidc-client-ts';

// Конфигурация OIDC клиента
// ВАЖНО: URL-адреса должны соответствовать тем, что вы указали в Spring Authorization Server
const settings = {
    // authority теперь должен быть внешним адресом, который проксируется nginx
    authority: 'http://localhost:7906', // Браузер обращается сюда, nginx проксирует на бэкенд
    client_id: 'vue-client',
    // redirect_uri теперь указывает на внешний адрес вашего фронтенда + /callback
    redirect_uri: 'http://localhost:7906/callback', // ВАЖНО: порт 7906
    post_logout_redirect_uri: 'http://localhost:7906/',
    response_type: 'code',
    scope: 'openid profile email api:read api:write',
    filterProtocolClaims: true,
    loadUserInfo: true,
    automaticSilentRenew: false,
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
};

class AuthService {
    userManager;

    constructor() {
        this.userManager = new UserManager(settings);
    }

    // Начать процесс входа
    signinRedirect = () => {
        return this.userManager.signinRedirect();
    };

    // Обработать callback после редиректа (на /callback)
    signinCallback = () => {
        return this.userManager.signinCallback();
    };

    // Начать процесс выхода
    signoutRedirect = () => {
        return this.userManager.signoutRedirect();
    };

    // Получить текущего пользователя (включая токены)
    getUser = () => {
        return this.userManager.getUser();
    };

    // Удалить информацию о пользователе из хранилища
    removeUser = () => {
        return this.userManager.removeUser();
    };

    // Получить Access Token для использования в API вызовах
    getAccessToken = async () => {
        const user = await this.getUser();
        return user?.access_token;
    };

    // Проверить, истек ли токен (опционально)
    isTokenExpired = async () => {
        const user = await this.getUser();
        if (user) {
            return user.expired; // true если токен истек
        }
        return true; // если пользователя нет, считаем токен недействительным
    };

    // Обновить токен (если refresh_token есть и automaticSilentRenew отключен)
    signinSilent = () => {
        return this.userManager.signinSilent();
    };
}

export default new AuthService();