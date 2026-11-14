import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.5.6" // Обновлено
    id("io.spring.dependency-management") version "1.1.7" // Проверьте совместимость
    kotlin("jvm") version "2.2.20" // Обновлено
    id("org.jetbrains.kotlin.plugin.spring") version "2.2.20" // Обновлено
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" // Обновлено
}

group = "com.svoemesto"
version = "1"

java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
    maven("https://clojars.org/repo/")
    maven("https://raw.github.com/kokorin/maven-repo/releases")
}

dependencies {

    implementation(project(":karaoke-app"))

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Jackson — без явной версии: версию всего семейства задаёт BOM
    // Spring Boot 3.5.6 (2.19.2). Пин 2.14.2 убран (BR-527) — он давал
    // смешанный стек (databind 2.14.2 + core/annotations 2.19.2) в артефакте.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // PostgreSQL-драйвер — без явной версии: версию задаёт BOM Spring Boot 3.5.6
    // (42.7.7). Раньше здесь стоял ручной пин драйвера 42.7.8: он перебивал
    // версию из BOM («selected by rule»), и обновление драйвера вместе с BOM
    // Spring Boot становилось невозможным. Правок, которые даёт 42.7.8, проект
    // не использует: нет queryTimeout и отмены запросов, нет DatabaseMetaData
    // и getMetaData, нет closeOnCompletion, нет репликации, нет xml-типа,
    // boolean-колонки не читаются как числа, массивы только одномерные
    // (читаются через getArray, не getResultSet).
    implementation("org.postgresql:postgresql")
    implementation("commons-io:commons-io:2.20.0")
    implementation("com.google.code.gson:gson:2.8.9")

    implementation("javax.websocket:javax.websocket-all:1.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    implementation("io.minio:minio:8.6.0")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Тестовый ключ шифрования: `Crypto` работает fail-fast (без CRYPTO_AES_* сервис
    // не стартует), а в тестовом окружении переменной нет. Значение — ТОЛЬКО для тестов,
    // в рабочих хостах ключ живёт в deploy/do.env (вне git).
    environment("CRYPTO_AES_KEY", "testAESCryptoKeyForTestsOnly1234")
    environment("CRYPTO_AES_IV", "testIv0123456789")
}

tasks.register("prepareKotlinBuildScriptModel") {}
