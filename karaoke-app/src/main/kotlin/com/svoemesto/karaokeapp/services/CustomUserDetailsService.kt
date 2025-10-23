// file: src/main/kotlin/com/svoemesto/karaokeapp/config/CustomUserDetailsService.kt

package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Users
import com.svoemesto.karaokeapp.services.UsersService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val usersService: UsersService,
    private val passwordEncoder: PasswordEncoder // Внедряем PasswordEncoder
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        // Используем ваш сервис для поиска пользователя
        val user = usersService.getUserByLogin(username)
            ?: throw UsernameNotFoundException("User not found: $username")
        // Возвращаем сущность Users, которая реализует UserDetails
        return user
    }
}