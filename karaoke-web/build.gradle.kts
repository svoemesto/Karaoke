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
    implementation("org.springframework.boot:spring-boot-starter-security")

//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") // для Котлин 2.2.0
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("commons-io:commons-io:2.20.0")
    implementation("com.google.code.gson:gson:2.8.9")

    implementation("javax.websocket:javax.websocket-all:1.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

//tasks.withType<KotlinCompile> {
//    kotlinOptions {
//        freeCompilerArgs = listOf("-Xjsr305=strict")
//        jvmTarget = "17"
//    }
//}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("prepareKotlinBuildScriptModel"){}