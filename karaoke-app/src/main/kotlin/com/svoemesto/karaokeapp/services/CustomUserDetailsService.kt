// file: src/main/kotlin/com/svoemesto/karaokeapp/services/CustomUserDetailsService.kt

package com.svoemesto.karaokeapp.services

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
// Убираем импорт PasswordEncoder, если он больше не нужен в этом классе
// import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val usersService: UsersService
    // Убрали @Suppress("unused") private val passwordEncoder: PasswordEncoder
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        // Используем ваш сервис для поиска пользователя
        val user = usersService.getUserByLogin(username)
            ?: throw UsernameNotFoundException("User not found: $username")
        // Возвращаем сущность Users, которая реализует UserDetails
        return user
    }
}