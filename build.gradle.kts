import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.5.6" // Обновлено
    id("io.spring.dependency-management") version "1.1.7" // Проверьте совместимость
    kotlin("jvm") version "2.2.20" // Обновлено
    id("org.jetbrains.kotlin.plugin.spring") version "2.2.20" // Обновлено
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" // Обновлено
    // Линтеры для Kotlin (применяются в subprojects через apply false, см. subprojects {})
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0" apply false
    // Генератор KDoc → HTML. Dokka 1.9.20 — последний 1.x.
    // Комментарий раньше утверждал «Jackson 2.14 (используется в проекте)» —
    // это было неверно (BR-527): в проекте Jackson 2.19.2 (BOM Spring Boot
    // 3.5.6), а 2.14.2 существовал только как мёртвый пин jackson-databind.
    // Dokka подключён с `apply false` — в сборку модулей не входит.
    id("org.jetbrains.dokka") version "1.9.20" apply false
    // detekt 1.23.x скомпилирован с Kotlin 2.0.x и не запускается на Kotlin 2.2.20 runtime.
    // FR-002/FR-007 (specs/001-code-standards-docs/spec.md) требуют ktlint+detekt вместе;
    // до выхода detekt-релиза с поддержкой Kotlin 2.2 detekt остаётся отключён (config/detekt/
    // baseline.xml + detekt.yml уже готовы и подключатся, как только апстрим станет совместим).
    // Открытый TODO зафиксирован в .specify/memory/constitution.md (Follow-up TODOs).
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

// Блок `dependencies` в корневом проекте УДАЛЁН (BR-530, аудит #336 /
// OpenProject #406).
//
// Он был мёртвым: у корневого проекта нет каталога `src/`, а `implementation`
// НЕ наследуется в subprojects — каждый модуль имеет свой `build.gradle.kts`.
// При этом версии в корне расходились с модульными (postgresql 42.7.7 против
// 42.7.8, commons-io 2.14.0 против 2.20.0, commons-lang3 3.18.0 против
// 3.14.0), поэтому правка зависимости в корне меняла НИЧЕГО в собираемом
// приложении — но файл выглядел как основной и читался первым.
// Источник истины по зависимостям — build-файлы модулей.
// Явно заданной версии Jackson здесь тоже больше нет: весь стек Jackson
// управляется BOM'ом Spring Boot 3.5.6 (BR-527).

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict") // Используем add() вместо присвоения списка
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
// Модуль karaoke-db (legacy Java-проект с шаблонным Main) удалён 2026-10-26: он
// нигде не собирался и не использовался; SQL-скрипты инициализации БД лежат в
// deploy/karaoke-db/ и к Gradle отношения не имеют.
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
