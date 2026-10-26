// import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.5.6" // Обновлено
    id("io.spring.dependency-management") version "1.1.7" // Проверьте совместимость
    kotlin("jvm") version "2.2.20" // Обновлено
    id("org.jetbrains.kotlin.plugin.spring") version "2.2.20" // Обновлено
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" // Обновлено
//    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.svoemesto"
version = "1"

java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
//    maven("https://clojars.org/repo/")
//    maven("https://raw.github.com/kokorin/maven-repo/releases")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")

//    implementation("clojure-interop:javax.sound:1.0.5")
    implementation("org.jflac:jflac-codec:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // Jackson — без явной версии: версию всего семейства (databind/core/
    // annotations/datatype-*/module-*) задаёт BOM Spring Boot 3.5.6 (2.19.2).
    // Раньше здесь стоял пин jackson-databind:2.14.2 (BR-527): в поставляемом
    // jar'е лежал databind 2.14.2 рядом с core/annotations 2.19.2 — смешанный
    // стек одного семейства, не покрытый поддержкой производителя.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.odftoolkit:odfdom-java:0.9.0")
    implementation("org.odftoolkit:simple-odf:0.9.0")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("commons-io:commons-io:2.20.0")
    implementation("com.google.code.gson:gson:2.8.9")

    implementation("com.github.st-h:TarsosDSP:2.4.1")

    implementation("org.jsoup:jsoup:1.21.2")
    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("org.apache.commons:commons-csv:1.8")

    implementation("org.seleniumhq.selenium:selenium-java:4.37.0")

    implementation("javax.websocket:javax.websocket-all:1.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.37.0")

    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5.1")
    implementation("io.minio:minio:8.6.0")

    implementation("com.microsoft.playwright:playwright:1.56.0")

    // LangChain4j убран целиком (Pass 462). Раньше зависимость держалась ТОЛЬКО ради
    // аннотации @Tool на SearchTool, но потребителя у неё не было: AI-сервисов/AiServices
    // в проекте нет, а сами LLM-вызовы (ScraperAgent, TextCorrectorAgent) идут напрямую
    // через LmStudioService (LM Studio). Аннотация была инертной, стартер — мёртвым весом
    // в образе. Путь langchain4j-ollama-spring-boot-starter/Ollama был убран раньше.
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

// Ручные сценарии (живая сеть, браузер, живые данные) — отдельный source set.
//
// Раньше такие сценарии лежали в общем наборе `src/test` и были помечены
// `@Disabled`. Пометка — плохая защита: при её снятии (или при переносе класса
// в другой модуль) `gradle test` на машине без браузера зависал на `readLine()`
// до таймаута раннера, а «зелёный» прогон учил игнорировать падения тестов.
//
// `manualTest` собирается и запускается только по явной команде и НЕ входит
// в `check`/`build`:
//   ./gradlew :karaoke-app:manualTest
val manualTest =
    sourceSets.create("manualTest") {
        // Ручные сценарии видят код приложения (как и тесты) и общую тестовую обвязку.
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
        compileClasspath += sourceSets["test"].output
        runtimeClasspath += sourceSets["test"].output
    }

configurations["manualTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["manualTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("manualTest") {
    description = "Ручные сценарии (живая сеть/браузер/данные). Не входят в check/build."
    group = "verification"
    testClassesDirs = manualTest.output.classesDirs
    classpath = manualTest.runtimeClasspath
    shouldRunAfter(tasks.test)
}

// tasks.bootJar {
//    // Настройки, если нужно, например, archiveClassifier.set("exec")
//    // archiveClassifier.set("") // Это может конфликтовать с обычным jar
//    enabled = true
//    // archiveClassifier.set("") // Убедитесь, что имя JAR соответствует COPY в Dockerfile
// }

// Обычная задача jar может быть отключена, если bootJar создаёт исполняемый JAR
// tasks.jar {
//    enabled = false // <-- Обычно отключают, если используется bootJar
//    // Или устанавливают classifier, чтобы не конфликтовать
//    // archiveClassifier.set("plain") // Если нужен обычный JAR для других целей
// }

// tasks.shadowJar {
//    archiveClassifier.set("") // Убирает суффикс shadow у JAR
//    mergeServiceFiles() // Полезно для некоторых библиотек
//    val shadowTask = this as ShadowJar
//    shadowTask.isZip64 = true
//    manifest {
//        attributes(
//            "Main-Class" to "com.svoemesto.karaokeapp.KaraokeAppApplicationKt"
//        )
//    }
// }
//
// // (Опционально) Сделать shadowJar задачей по умолчанию для build
// tasks.build {
//     dependsOn(tasks.shadowJar)
// }

tasks.register("prepareKotlinBuildScriptModel") {}
