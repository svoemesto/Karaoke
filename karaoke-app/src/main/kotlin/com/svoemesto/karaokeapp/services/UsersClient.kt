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
