package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Users
import com.svoemesto.karaokeapp.model.UsersDto
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.UsersService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@RequestMapping("/api/users")
class UsersController(
    val usersService: UsersService,
    val passwordEncoder: PasswordEncoder // Внедряем PasswordEncoder для вызовов сервиса
) {
    // Эти методы требуют аутентификации (токена)
    @PostMapping("/byId")
    @ResponseBody
    fun apiUser(@RequestParam id: String): Any? = usersService.getUserById(id.toLong())?.toDTO()

    @PostMapping("/update")
    @ResponseBody
    fun apiUpdateUser(
        @RequestParam(required = true) id: Long,
        @RequestParam(required = false) login: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) firstName: String?,
        @RequestParam(required = false) lastName: String?,
        @RequestParam(required = false) groups: String?
    ): Long {
        usersService.getUserById(id)?.let { user ->
            login?.let { user.login = it }
            email?.let { user.email = it }
            firstName?.let { user.firstName = it }
            lastName?.let { user.lastName = it }
            groups?.let { user.groups = it }
            user.save()
            return user.id
        }
        return 0L
    }

    @PostMapping("/delete")
    @ResponseBody
    fun apiDeleteUser(@RequestParam(required = true) id: Long): Boolean = usersService.deleteUser(id = id)

    @PostMapping("/digest")
    @ResponseBody
    fun apiUsersDigest(
        @RequestParam(required = false) id: String?,
        @RequestParam(required = false) login: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) firstName: String?,
        @RequestParam(required = false) lastName: String?,
        @RequestParam(required = false) groups: String?
    ): Map<String, Any> {

        val whereArgs: MutableMap<String, String> = mutableMapOf()
        id?.let { if (id != "") whereArgs["id"] = id }
        login?.let { if (login != "") whereArgs["login"] = login }
        email?.let { if (email != "") whereArgs["email"] = email }
        firstName?.let { if (firstName != "") whereArgs["firstName"] = firstName }
        lastName?.let { if (lastName != "") whereArgs["lastName"] = lastName }
        groups?.let { if (groups != "") whereArgs["groups"] = groups }

        val usersList = Users.loadList(
            whereArgs = whereArgs,
            database = WORKING_DATABASE
        ).map { it.toDTO() }
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "usersDigest" to usersList
        )
    }

    // Эти методы остаются открытыми
    @PostMapping("/create")
    @ResponseBody
    fun apiCreateUser(
        @RequestParam(required = true) login: String,
        @RequestParam(required = true) password: String,
        @RequestParam(required = true) passwordConfirm: String,
        @RequestParam(required = true) email: String,
        @RequestParam(required = true) firstName: String,
        @RequestParam(required = true) lastName: String
    ): Long {
        val usersDto = UsersDto(
            id = 0,
            login = login,
            password = password,
            passwordConfirm = passwordConfirm,
            email = email,
            firstName = firstName,
            lastName = lastName
        )
        // Передаем PasswordEncoder в сервис
        usersService.createNewUser(usersDto, passwordEncoder)?.let { users ->
            return users.id
        }
        return 0L
    }

    @PostMapping("/checkPassword")
    @ResponseBody
    fun apiUserCheckPassword(
        @RequestParam login: String,
        @RequestParam password: String
    ): Boolean = usersService.checkPassword(login, password, passwordEncoder) // Передаем PasswordEncoder

    @PostMapping("/resetPassword")
    @ResponseBody
    fun apiUserResetPassword(@RequestParam login: String): Boolean = usersService.resetPassword(login, passwordEncoder) // Передаем PasswordEncoder

    @PostMapping("/changePassword")
    @ResponseBody
    fun apiUserChangePassword(
        @RequestParam login: String,
        @RequestParam newPassword: String,
        @RequestParam oldPassword: String
    ): Boolean = usersService.changePassword(login, newPassword, oldPassword, passwordEncoder) // Передаем PasswordEncoder

}