package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.model.Users
import com.svoemesto.karaokeapp.model.UsersDto
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.logging.Logger

@Service
class UsersServiceImpl(
    val usersClient: UsersClient
    // Убрали val passwordEncoder: PasswordEncoder из конструктора
): UsersService {
    private val logger = Logger.getLogger(UsersServiceImpl::class.java.name)

    override fun getUserById(id: Long): Users? = usersClient.getUserById(id = id)
    override fun deleteUser(id: Long): Boolean = usersClient.deleteUser(id = id)
    override fun getUserByLogin(login: String): Users? = usersClient.getUserByLogin(login = login)
    override fun isUserPresent(login: String): Boolean = usersClient.isUserPresent(login = login)

    override fun createNewUser(userDto: UsersDto, passwordEncoder: PasswordEncoder): Users? {
        // Используем PasswordEncoder из параметра метода
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
        if (newPassword.length <= 6) {
            logger.warning("New password is too short for user: $login")
            return false
        }

        // Вызываем метод в Client, передавая PasswordEncoder
        return usersClient.changePassword(login = login, newPassword = newPassword, oldPassword = oldPassword, passwordEncoder = passwordEncoder)
    }
}