package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Users
import com.svoemesto.karaokeapp.model.UsersDto
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

interface UsersClient {
    fun getUserById(id: Long): Users?
    fun deleteUser(id: Long): Boolean
    fun getUserByLogin(login: String): Users?
    fun isUserPresent(login: String): Boolean
    fun createNewUser(userDto: UsersDto, passwordEncoder: PasswordEncoder): Users?
    fun checkPassword(login: String, password: String, passwordEncoder: PasswordEncoder): Boolean
    fun resetPassword(login: String, passwordEncoder: PasswordEncoder): Boolean
    fun changePassword(login: String, newPassword: String, oldPassword: String, passwordEncoder: PasswordEncoder): Boolean
}
@Service
class UsersClientImpl: UsersClient {
    override fun getUserById(id: Long): Users? {
        return Users.getUsersById(id = id, database = WORKING_DATABASE)
    }

    override fun deleteUser(id: Long): Boolean {
        return Users.deleteUser(id = id, database = WORKING_DATABASE)
    }

    override fun getUserByLogin(login: String): Users? {
        return Users.getUserByLogin(login = login, database = WORKING_DATABASE)
    }

    override fun isUserPresent(login: String): Boolean {
        return getUserByLogin(login) != null
    }

    override fun createNewUser(userDto: UsersDto, passwordEncoder: PasswordEncoder): Users? {
        // Вызываем статический метод из Users, передавая PasswordEncoder
        return Users.createNewUser(userDto = userDto, database = WORKING_DATABASE, passwordEncoder = passwordEncoder)
    }

    override fun checkPassword(login: String, password: String, passwordEncoder: PasswordEncoder): Boolean {
        // Вызываем статический метод из Users, передавая PasswordEncoder
        return Users.checkPassword(login = login, password = password, database = WORKING_DATABASE, passwordEncoder = passwordEncoder)
    }

    override fun resetPassword(login: String, passwordEncoder: PasswordEncoder): Boolean {
        // Вызываем статический метод из Users, передавая PasswordEncoder
        return Users.resetPassword(login = login, database = WORKING_DATABASE, passwordEncoder = passwordEncoder)
    }

    override fun changePassword(login: String, newPassword: String, oldPassword: String, passwordEncoder: PasswordEncoder): Boolean {
        // Вызываем статический метод из Users, передавая PasswordEncoder
        return Users.changePassword(login = login, newPassword = newPassword, oldPassword = oldPassword, database = WORKING_DATABASE, passwordEncoder = passwordEncoder)
    }
}