package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.Users
import com.svoemesto.karaokeapp.model.UsersDto
import org.springframework.stereotype.Service
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.logging.Logger

interface UsersService {
    fun getUserById(id: Long): Users?
    fun deleteUser(id: Long): Boolean
    fun getUserByLogin(login: String): Users?
    fun isUserPresent(login: String): Boolean
    fun createNewUser(userDto: UsersDto, passwordEncoder: PasswordEncoder): Users? // Добавлен PasswordEncoder
    fun checkPassword(login: String, password: String, passwordEncoder: PasswordEncoder): Boolean // Добавлен PasswordEncoder
    fun resetPassword(login: String, passwordEncoder: PasswordEncoder): Boolean // Добавлен PasswordEncoder
    fun changePassword(login: String, newPassword: String, oldPassword: String, passwordEncoder: PasswordEncoder): Boolean // Добавлен PasswordEncoder
}

@Service
class UsersServiceImpl(
    val usersClient: UsersClient,
    val passwordEncoder: PasswordEncoder // Внедряем PasswordEncoder
): UsersService {
    private val logger = Logger.getLogger(UsersServiceImpl::class.java.name)

    override fun getUserById(id: Long): Users? = usersClient.getUserById(id = id)
    override fun deleteUser(id: Long): Boolean = usersClient.deleteUser(id = id)
    override fun getUserByLogin(login: String): Users? = usersClient.getUserByLogin(login = login)
    override fun isUserPresent(login: String): Boolean = usersClient.isUserPresent(login = login)

    override fun createNewUser(userDto: UsersDto, passwordEncoder: PasswordEncoder): Users? {
        // Используем PasswordEncoder из параметра (или из внедрения)
        // Проверка совпадения паролей уже в DTO
        if (userDto.password != userDto.passwordConfirm) {
            logger.warning("Password and password confirmation do not match for login: ${userDto.login}")
            throw IllegalArgumentException("Passwords do not match")
        }

        if (isUserPresent(userDto.login)) {
            logger.warning("User already exists with login: ${userDto.login}")
            throw IllegalArgumentException("User already exists")
        }

        // Вызываем метод в Client, передавая PasswordEncoder
        val createdUser = usersClient.createNewUser(userDto = userDto, passwordEncoder = passwordEncoder)

        logger.info("User created successfully with login: ${userDto.login}")
        return createdUser
    }

    override fun checkPassword(login: String, password: String, passwordEncoder: PasswordEncoder): Boolean {
        // Вызываем метод в Client, передавая PasswordEncoder
        return usersClient.checkPassword(login = login, password = password, passwordEncoder = passwordEncoder)
    }

    override fun resetPassword(login: String, passwordEncoder: PasswordEncoder): Boolean {
        // Вызываем метод в Client, передавая PasswordEncoder
        return usersClient.resetPassword(login = login, passwordEncoder = passwordEncoder)
    }

    override fun changePassword(login: String, newPassword: String, oldPassword: String, passwordEncoder: PasswordEncoder): Boolean {
        // Проверка длины нового пароля (опционально, можно в DTO)
        if (newPassword.length <= 6) {
            logger.warning("New password is too short for user: $login")
            return false
        }

        // Вызываем метод в Client, передавая PasswordEncoder
        return usersClient.changePassword(login = login, newPassword = newPassword, oldPassword = oldPassword, passwordEncoder = passwordEncoder)
    }
}