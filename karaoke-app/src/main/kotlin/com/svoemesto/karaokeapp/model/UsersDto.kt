package com.svoemesto.karaokeapp.model

import java.io.Serializable

data class UsersDto(
    val id: Long,
    val login: String,
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val groups: String = "",
): Serializable, Comparable<UsersDto>, KaraokeDbTableDto {
    override fun compareTo(other: UsersDto): Int {
        return login.compareTo(other.login)
    }

    override fun validationErrors(): List<String> {
        val errors: MutableList<String> = mutableListOf()
        if (login.length < 3) errors.add("Длина логина должна быть больше 3 символов")
        if (password != "" && password.length < 6) errors.add("Длина пароля должна быть больше 6 символов")
        if (password != passwordConfirm) errors.add("Не совпадают пароль и подтверждение")
        if (firstName == "") errors.add("Поле 'Имя' не может быть пустым")
        if (lastName == "") errors.add("Поле 'Фамилия' не может быть пустым")
        return errors
    }

    override fun isValid(): Boolean {
        val errors = validationErrors()
        if (errors.isNotEmpty()) println(errors.joinToString(", "))
        return errors.isEmpty()
    }

    override fun fromDto(): Users {
        val result = Users()
        result.id = id
        result.login = login
        result.email = email
        result.firstName = firstName
        result.lastName = lastName
        result.groups = groups
        return result
    }

}