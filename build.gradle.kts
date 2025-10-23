import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.0.5"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.7.22"
    kotlin("plugin.spring") version "1.7.22"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.21"
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
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")

//    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.0")
    implementation("clojure-interop:javax.sound:1.0.5")
    implementation("org.jflac:jflac-codec:1.5.2")
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") // для Котлин 2.2.0
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    implementation("org.odftoolkit:odfdom-java:0.9.0")
    implementation("org.odftoolkit:simple-odf:0.9.0")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("commons-io:commons-io:2.14.0")
    implementation("com.google.code.gson:gson:2.8.9")

    implementation("com.github.st-h:TarsosDSP:2.4.1")

//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.0")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("org.apache.commons:commons-csv:1.8")

    implementation("org.seleniumhq.selenium:selenium-java:4.37.0")

    implementation("javax.websocket:javax.websocket-all:1.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation ("org.seleniumhq.selenium:selenium-java:4.8.0")

    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("io.minio:minio:8.6.0")

}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

//tasks.withType<KotlinCompile> {
//    compilerOptions {
//        freeCompilerArgs.add("-Xjsr305=strict") // Используем add() вместо присвоения списка
//        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
//    }
//}

tasks.withType<Test> {
    useJUnitPlatform()
}

println("KaraokeVersion: ${project.version}")