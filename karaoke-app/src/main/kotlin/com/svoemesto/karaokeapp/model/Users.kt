package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.getMd5Hash
import com.svoemesto.karaokeapp.services.SNS
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.password.PasswordEncoder
import java.io.Serializable

// Добавим поле authorities, если планируется использовать роли
// @KaraokeDbTableField(name = "authorities") // Нужно ли хранить в БД?
// var authorities: String = "ROLE_USER" // Пример по умолчанию
class Users(override val database: Connection = (WORKING_DATABASE as Connection)) : Serializable, Comparable<Users>, KaraokeDbTable,
    UserDetails {

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "login")
    var login: String = ""

    @KaraokeDbTableField(name = "password_hash")
    var passwordHash: String = ""

    @KaraokeDbTableField(name = "email")
    var email: String = ""

    @KaraokeDbTableField(name = "first_name")
    var firstName: String = ""

    @KaraokeDbTableField(name = "last_name")
    var lastName: String = ""

    @KaraokeDbTableField(name = "groups")
    var groups: String = ""
    override fun getTableName(): String = TABLE_NAME

    override fun compareTo(other: Users): Int {
        return login.compareTo(other.login)
    }

    override fun toDTO(): UsersDto {
        return UsersDto(
            id = id,
            login = login,
            email = email,
            firstName = firstName,
            lastName = lastName,
            groups = groups
        )
    }

    // --- Методы для работы с паролем через PasswordEncoder ---
    // Используется в UsersServiceImpl для установки нового пароля
    fun setPasswordWithEncoder(encodedPassword: String) {
        this.passwordHash = encodedPassword
    }

    fun checkPassword(password: String, passwordEncoder: PasswordEncoder): Boolean {
        return passwordEncoder.matches(password, this.passwordHash)
    }


    fun changePassword(newPassword: String, oldPassword: String, passwordEncoder: PasswordEncoder): Boolean {
        if (newPassword.length > 6) {
            // Сначала проверяем старый пароль с помощью текущего хеша (MD5 или BCrypt)
            if (checkPassword(oldPassword, passwordEncoder)) {
                setPasswordWithEncoder(passwordEncoder.encode(newPassword))
                save() // Сохраняем обновленный хеш
                return true
            }
        }
        return false
    }

    // --- UserDetails Implementation ---
    override fun getUsername(): String = this.login
    override fun getPassword(): String = this.passwordHash // Возвращает захешированный пароль
    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_USER")) // Или загрузить из БД
    override fun isEnabled(): Boolean = true // Добавьте поле в БД если нужно
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true

    override fun toString(): String {
        return "Users(database=$database, id=$id, login='$login', passwordHash='[PROTECTED]', email='$email', firstName='$firstName', lastName='$lastName', groups='$groups')"
    }

    fun resetPassword(passwordEncoder: PasswordEncoder) {
        // Установить пароль на пустую строку или на сгенерированный, захешированный
        val resetPasswordHash = passwordEncoder.encode("") // Или сгенерированный пароль
        setPasswordWithEncoder(resetPasswordHash)
        save()
    }

    @Suppress("unused")
    fun changePassword(newPassword: String, oldPassword: String): Boolean {
        if (newPassword.length > 6) {
            val newPasswordHash = getMd5Hash(newPassword)
            val oldPasswordHash =  if (oldPassword == "") "" else getMd5Hash(oldPassword)
            if (oldPasswordHash == passwordHash && newPasswordHash != null) {
                passwordHash = newPasswordHash
                save()
                return true
            }
        }
        return false
    }

    constructor() : this(
        database = (WORKING_DATABASE as Connection)
    )
    companion object {

        const val TABLE_NAME = "tbl_users"

        // --- Обновленные методы, принимающие PasswordEncoder ---
        fun checkPassword(login: String, password: String, database: KaraokeConnection, passwordEncoder: PasswordEncoder): Boolean {
            return getUserByLogin(login = login, database = database)?.checkPassword(password, passwordEncoder) ?: false
        }

        fun resetPassword(login: String, database: KaraokeConnection, passwordEncoder: PasswordEncoder): Boolean {
            return getUserByLogin(login = login, database = database)?.let {
                it.resetPassword(passwordEncoder)
                true
            } ?: false
        }

        fun changePassword(login: String, newPassword: String, oldPassword: String, database: KaraokeConnection, passwordEncoder: PasswordEncoder): Boolean {
            return getUserByLogin(login = login, database = database)?.changePassword(newPassword = newPassword, oldPassword = oldPassword, passwordEncoder) ?: false
        }

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()
            if (whereArgs.containsKey("id")) where += "id=${whereArgs["id"]}"
            if (whereArgs.containsKey("login")) where += "LOWER(login) LIKE '%${whereArgs["login"]?.lowercase()}%'"
            if (whereArgs.containsKey("email")) where += "LOWER(email) LIKE '%${whereArgs["email"]?.lowercase()}%'"
            if (whereArgs.containsKey("firstName")) where += "LOWER(first_name) LIKE '%${whereArgs["firstName"]?.lowercase()}%'"
            if (whereArgs.containsKey("lastName")) where += "LOWER(last_name) LIKE '%${whereArgs["lastName"]?.lowercase()}%'"
            if (whereArgs.containsKey("groups")) where += "LOWER(groups) LIKE '%${whereArgs["groups"]?.lowercase()}%'"
            return where
        }

        fun loadList(whereArgs: Map<String, String>,
                     limit: Int = 0,
                     offset: Int = 0,
                     database: KaraokeConnection): List<Users> {
            return KaraokeDbTable.loadList(
                clazz = Users::class,
                tableName = TABLE_NAME,
                whereList = getWhereList(whereArgs),
                limit = limit,
                offset = offset,
                database = database
            ).map { it as Users }
        }

        fun createNewUser(userDto: UsersDto, database: KaraokeConnection, passwordEncoder: PasswordEncoder): Users? {
            if (userDto.isValid()) {
                if (getUserByLogin(login = userDto.login, database = database) == null) {
                    val newUser = Users(database = (database as Connection))
                    newUser.login = userDto.login
                    newUser.setPasswordWithEncoder(passwordEncoder.encode(userDto.password))
                    newUser.email =  userDto.email
                    newUser.firstName = userDto.firstName
                    newUser.lastName = userDto.lastName
                    val newUserInDb = KaraokeDbTable.createDbInstance(
                        entity = newUser,
                        database = database
                    ) as? Users?
                    newUserInDb?.let {
                        return it
                    }
                } else {
                    SNS.send(SseNotification.error(Message( // Message не определен, замените на ваш класс
                        type = "error",
                        head = "Создание нового пользователя.",
                        body = "Не удалось создать нового пользователя, потому что пользователь с таким логином уже существует."
                    )))
                }
            } else {
                SNS.send(SseNotification.error(Message( // Message не определен, замените на ваш класс
                    type = "error",
                    head = "Создание нового пользователя.",
                    body = "Не удалось создать нового пользователя из-за следующих ошибок: ${userDto.validationErrors().joinToString(", ")}"
                )))
            }
            return null
        }

        fun getUsersById(id: Long, database: KaraokeConnection): Users? {
            return KaraokeDbTable.loadById(
                clazz = Users::class,
                tableName = TABLE_NAME,
                id = id,
                database = database
            ) as? Users?
        }

        fun getUserByLogin(login: String, database: KaraokeConnection): Users? {
            return KaraokeDbTable.loadList(
                clazz = Users::class,
                tableName = TABLE_NAME,
                whereList = listOf("login='$login'"),
                database = database
            ).firstOrNull() as? Users?
        }

        fun deleteUser(id: Long, database: KaraokeConnection): Boolean {
            return KaraokeDbTable.delete(
                tableName = TABLE_NAME,
                id = id,
                database = database
            )
        }
    }

}