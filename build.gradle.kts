import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.5.6" // Обновлено
    id("io.spring.dependency-management") version "1.1.7" // Проверьте совместимость
    kotlin("jvm") version "2.2.20" // Обновлено
    id("org.jetbrains.kotlin.plugin.spring") version "2.2.20" // Обновлено
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" // Обновлено
    // Линтеры для Kotlin (применяются в subprojects через apply false, см. subprojects {})
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0" apply false
    // Генератор KDoc → HTML. Dokka 1.9.20 — последний 1.x, совместим
    // с Jackson 2.14 (используется в проекте) и Kotlin 2.x runtime.
    // Dokka 2.0.x требует Jackson 2.18+ (конфликт с Spring Boot 3.5 BOM).
    id("org.jetbrains.dokka") version "1.9.20" apply false
    // detekt 1.23.x скомпилирован с Kotlin 2.0.x и не запускается на Kotlin 2.2.20 runtime.
    // До выхода detekt 2.0 (с поддержкой Kotlin 2.2) — оставляем как TODO, см. plan.md Phase 2.
    // id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
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
    implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")

    implementation("clojure-interop:javax.sound:1.0.5")
    implementation("org.jflac:jflac-codec:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    implementation("org.odftoolkit:odfdom-java:0.9.0")
    implementation("org.odftoolkit:simple-odf:0.9.0")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("commons-io:commons-io:2.14.0")
    implementation("com.google.code.gson:gson:2.8.9")

    implementation("com.github.st-h:TarsosDSP:2.4.1")

    implementation("org.jsoup:jsoup:1.21.2")
    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("org.apache.commons:commons-csv:1.8")

    implementation("org.seleniumhq.selenium:selenium-java:4.37.0")

    implementation("javax.websocket:javax.websocket-all:1.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation ("org.seleniumhq.selenium:selenium-java:4.37.0")

    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("io.minio:minio:8.6.0")

}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict") // Используем add() вместо присвоения списка
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

println("KaraokeVersion: ${project.version}")

// Применяем ktlint ко всем subprojects с per-module baseline.
// ВАЖНО: ktlint-gradle 12.x генерирует ОДИН baseline-файл на модуль
// (см. README плагина). Если несколько subprojects используют один и
// тот же baseline-файл — они ПЕРЕЗАПИСЫВАЮТ друг друга, и в итоге
// baseline покрывает только последний обработанный модуль. Поэтому
// здесь per-module пути: `config/ktlint/baseline-<module>.xml`.
//
// detekt временно отключён: 1.23.x скомпилирован с Kotlin 2.0.x и
// несовместим с Kotlin 2.2.20. До выхода совместимой версии — ktlint
// покрывает форматирование + базовые правила.
//
// Чтобы сгенерировать baseline для всех модулей:
//   ./gradlew ktlintGenerateBaseline
//
// Чтобы обновить baseline после рефакторинга:
//   ./gradlew ktlintGenerateBaseline
//
// Целевой темп сокращения: ≥10%/мес (SC-002 spec.md).
//
// ktlint + Dokka применяются ТОЛЬКО к Kotlin-проектам (где применён Kotlin JVM plugin).
// karaoke-db — legacy Java-проект (см. AGENTS.md), ktlint всё равно на нём работает
// (Java-исходники попадают под общий линтер), а Dokka — нет (требует Kotlin).
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    afterEvaluate {
        // Применяем Dokka только если в проекте есть Kotlin JVM plugin.
        if (plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
            apply(plugin = "org.jetbrains.dokka")

            tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
                dokkaSourceSets.named("main") {
                    sourceRoots.from("src/main/kotlin")
                    jdkVersion.set(17)
                    reportUndocumented.set(false)
                    skipDeprecated.set(false)
                    skipEmptyPackages.set(true)
                }
            }
        }
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version = "1.5.0"
        baseline = file("${rootDir}/config/ktlint/baseline-${project.name}.xml")
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }
    }

    // Dokka: генерация документации из KDoc.
    // Применяется через afterEvaluate (см. выше) — только к Kotlin-проектам.

    // detekt-блок закомментирован до совместимости с Kotlin 2.2.
    // apply(plugin = "io.gitlab.arturbosch.detekt")
    // extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    //     baseline = file("${rootDir}/config/detekt/baseline-${project.name}.xml")
    //     buildUponDefaultConfig = true
    //     allRules = false
    //     config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    // }
    // tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    //     jvmTarget = "17"
    // }
}
