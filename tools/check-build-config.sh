#!/usr/bin/env bash
# =====================================================================
# tools/check-build-config.sh — guard «конфигурация сборки без мёртвых дублей»
# =====================================================================
# Зачем (аудит #336, OpenProject #406, пакет PKG-S3-34, модуль «прочее»):
#
#   BR-527 (jackson-version-skew:build) — явный пин `jackson-databind:2.14.2`
#     перебивал версию, управляемую BOM'ом Spring Boot 3.5.6 (2.19.2). В
#     поставляемом jar'е оказывался СМЕШАННЫЙ стек: jackson-databind-2.14.2
#     рядом с jackson-core/annotations/datatype-*-2.19.2. Разные minor-версии
#     одного семейства поддержкой производителя не покрыты. Плюс комментарий
#     в build.gradle.kts утверждал «Jackson 2.14 (используется в проекте)» —
#     а в проекте 2.19.2.
#
#   BR-529 (heap-dump-world-readable:build) — Gradle-daemon запускался с
#     `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/gradle-heap.hprof`.
#     JVM писал полный дамп кучи в общий /tmp (режим 1777) с правами
#     0666 & ~umask → обычно 0644 → читался любым локальным пользователем.
#     В дампе — карта System.getenv(), то есть секреты окружения сборки.
#
#   BR-530 (dead-duplicate-build-config:build) — две копии конфигурации сборки,
#     которые не участвуют в сборке и расходятся с фактической:
#       (1) блок `dependencies` в корневом build.gradle.kts — у корневого
#           проекта нет `src/`, а `implementation` не наследуется в subprojects;
#       (2) `pom.xml` в корне — Maven-дескриптор, не используемый ни CI, ни
#           скриптами, с другим результатом сборки (версия 1.0-SNAPSHOT,
#           Java-target 16, зависимости 2021–2022 гг.).
#
# Правила:
#   1. jackson-explicit-version  — в build-файлах нет явной версии
#      `com.fasterxml.jackson.*` (версию задаёт BOM Spring Boot);
#   2. jackson-version-skew     — версии Jackson в репозитории не расходятся;
#   3. dead-root-dependencies    — в корневом build.gradle.kts нет блока
#      `dependencies { }` (мёртвый конфиг);
#   4. maven-pom-present        — в корне репозитория нет `pom.xml` и нет
#      ссылок на него в `.idea/misc.xml` (IDE-автоимпорт фантомного проекта);
#   5. heap-dump-global-enabled — в `org.gradle.jvmargs` нет
#      `-XX:+HeapDumpOnOutOfMemoryError` (дамп включается только явным opt-in);
#   6. heap-dump-outside-helper — `-XX:+HeapDumpOnOutOfMemoryError` встречается
#      только в sanctioned-скрипте `tools/gradle-heap-dump.sh`;
#   7. heap-dump-shared-path    — `-XX:HeapDumpPath=` не указывает в общий
#      каталог (/tmp, /var/tmp, /dev/shm, /run);
#   8. heap-dump-unsafe-perms   — sanctioned-скрипт гарантирует приватные
#      права: `umask 077` (файл дампа 0600) и каталог 0700.
#
# Использование:
#   bash tools/check-build-config.sh              # guard-режим
#   bash tools/check-build-config.sh --quiet      # для pre-commit
#   bash tools/check-build-config.sh --self-test  # фикстуры «до/после»
#   bash tools/check-build-config.sh --root DIR   # сканировать копию дерева
#
# Exit codes:
#   0 — нарушений нет
#   1 — найдены нарушения
#   2 — проверка не могла быть выполнена (нет файлов конфигурации)
# =====================================================================

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$PROJECT_ROOT"
QUIET=0
SELF_TEST=0

# Скрипт, которому РАЗРЕШЕНО включать дамп кучи.
HEAP_DUMP_HELPER="tools/gradle-heap-dump.sh"

color_red() { [[ "$QUIET" -eq 1 ]] || printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { [[ "$QUIET" -eq 1 ]] || printf '\033[0;32m%s\033[0m\n' "$*"; }

usage() {
    sed -n '2,68p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --quiet) QUIET=1 ;;
        --self-test) SELF_TEST=1 ;;
        --root)
            ROOT="${2:-}"
            [[ -n "$ROOT" ]] || { usage >&2; exit 2; }
            shift
            ;;
        -h|--help) usage; exit 0 ;;
        *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
    shift
done

ROOT="$(cd "$ROOT" 2>/dev/null && pwd)" || {
    echo "нет такого каталога: $ROOT" >&2
    exit 2
}

violations=0

violation() {
    violations=$((violations + 1))
    color_red "  ❌ [$1] $2"
    [[ -n "${3:-}" ]] && printf '     %s\n' "$3"
    return 0
}

# Файлы, которые сканируются. Список явный: guard должен предсказуемо
# проверять одно и то же, а не «зависеть от того, что нашлось в каталоге».
# Сам guard (tools/check-build-config.sh) НЕ сканируется: в нём есть строки
# с примерами нарушений (правила и фикстуры self-test) — они не исполняются.
scan_files() {
    local f
    for f in \
        build.gradle.kts \
        settings.gradle.kts \
        gradle.properties \
        karaoke-app/build.gradle.kts \
        karaoke-web/build.gradle.kts \
        karaoke-db/build.gradle.kts \
        .idea/misc.xml \
        tools/gradle-heap-dump.sh
    do
        [[ -f "$ROOT/$f" ]] && printf '%s\n' "$f"
    done
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        printf '%s\n' "${f#"$ROOT"/}"
    done < <(find "$ROOT/deploy" -type f -name '*.sh' 2>/dev/null | LC_ALL=C sort)
    return 0
}

# Jackson с явной версией: com.fasterxml.jackson.core:jackson-databind:2.14.2
jackson_pinned_versions() {
    local file
    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        [[ "$file" == *.gradle.kts ]] || continue
        grep -oE 'com\.fasterxml\.jackson[^"]*:[0-9]+\.[0-9]+(\.[0-9]+)?' "$ROOT/$file" 2>/dev/null \
            | sed 's/.*://' | LC_ALL=C sort -u
    done < <(scan_files)
}

check_jackson() {
    local file line versions
    local -a pins=()

    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        [[ "$file" == *.gradle.kts ]] || continue
        while IFS=: read -r lineno content; do
            [[ -z "$lineno" ]] && continue
            violation "jackson-explicit-version" \
                "$file:$lineno: явная версия Jackson перебивает BOM Spring Boot" \
                "$(printf '%s' "$content" | sed 's/^[[:space:]]*//')"
            pins+=("$(printf '%s' "$content" | grep -oE 'com\.fasterxml\.jackson[^"]*:[0-9]+\.[0-9]+(\.[0-9]+)?' | head -1 | sed 's/.*://')")
        done < <(grep -nE 'com\.fasterxml\.jackson[^"]*:[0-9]+\.[0-9]+(\.[0-9]+)?' "$ROOT/$file" 2>/dev/null || true)
    done < <(scan_files)

    # Разные версии Jackson в разных местах — тот же риск, что и явный пин.
    versions="$(jackson_pinned_versions | tr '\n' ' ')"
    local -a distinct=()
    local v
    for v in $versions; do
        distinct+=("$v")
    done
    if [[ "${#distinct[@]}" -gt 1 ]]; then
        violation "jackson-version-skew" \
            "в репозитории несколько версий Jackson: ${distinct[*]}" \
            "Весь стек Jackson (databind/core/annotations/datatype-*/module-*) — одной версии, управляемой BOM."
    fi
}

check_dead_root_dependencies() {
    local file="build.gradle.kts"
    [[ -f "$ROOT/$file" ]] || {
        color_red "❌ нет корневого $file — проверка не выполнена"
        return 2
    }
    local lineno content
    while IFS=: read -r lineno content; do
        [[ -z "$lineno" ]] && continue
        # Комментарии не исполняются — мёртвый блок в комментарии не нарушение.
        local trimmed="${content#"${content%%[![:space:]]*}"}"
        case "$trimmed" in
            \#* | //* | \** | /\**) continue ;;
        esac
        if [[ "$trimmed" =~ ^dependencies[[:space:]]*\{ ]]; then
            violation "dead-root-dependencies" \
                "$file:$lineno: блок dependencies в корневом проекте — мёртвый конфиг" \
                "У корневого проекта нет src/, implementation не наследуется в subprojects. Источник истины — build-файлы модулей."
        fi
    done < <(grep -nE '^[[:space:]]*dependencies[[:space:]]*\{' "$ROOT/$file" 2>/dev/null || true)
    return 0
}

check_maven_pom() {
    if [[ -f "$ROOT/pom.xml" ]]; then
        violation "maven-pom-present" \
            "в корне репозитория есть pom.xml — второй, неиспользуемый build-дескриптор" \
            "Maven-сборка не используется ни CI, ни скриптами; POM задаёт другой результат сборки."
    fi
    local misc=".idea/misc.xml"
    if [[ -f "$ROOT/$misc" ]]; then
        local lineno
        while IFS=: read -r lineno _; do
            [[ -z "$lineno" ]] && continue
            violation "maven-pom-present" \
                "$misc:$lineno: IDE ссылается на pom.xml (автоимпорт фантомного Maven-проекта)" \
                "Ссылку на несуществующий pom.xml убрать."
        done < <(grep -nE 'pom\.xml' "$ROOT/$misc" 2>/dev/null || true)
    fi
}

check_heap_dump() {
    local file lineno content helper="$ROOT/$HEAP_DUMP_HELPER"

    # 5. глобальное включение дампов в gradle.properties
    local props="$ROOT/gradle.properties"
    if [[ -f "$props" ]]; then
        while IFS=: read -r lineno content; do
            [[ -z "$lineno" ]] && continue
            case "$content" in
                *org.gradle.jvmargs*)
                    if [[ "$content" == *HeapDumpOnOutOfMemoryError* ]]; then
                        violation "heap-dump-global-enabled" \
                            "gradle.properties:$lineno: дамп кучи включён глобально" \
                            "Дамп включается только явным opt-in (tools/gradle-heap-dump.sh), иначе он попадает в общий /tmp."
                    fi
                    ;;
            esac
        done < <(grep -nE 'org\.gradle\.jvmargs' "$props" 2>/dev/null || true)
    fi

    # 6/7. дампы вне sanctioned-скрипта и в общих каталогах
    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        [[ "$file" == "$HEAP_DUMP_HELPER" ]] && continue
        while IFS=: read -r lineno content; do
            [[ -z "$lineno" ]] && continue
            case "$content" in
                \#* | //*) continue ;;
            esac
            if [[ "$content" == *HeapDumpOnOutOfMemoryError* ]]; then
                violation "heap-dump-outside-helper" \
                    "$file:$lineno: дамп кучи включается вне $HEAP_DUMP_HELPER" \
                    "Единственное разрешённое место — tools/gradle-heap-dump.sh (каталог 0700, файл 0600)."
            fi
            if [[ "$content" == *HeapDumpPath=* ]]; then
                local path="${content#*HeapDumpPath=}"
                path="${path%%\"*}"; path="${path#\"}"
                path="${path%%\'*}"; path="${path#\'}"
                path="${path%% *}"
                case "$path" in
                    /tmp/* | /var/tmp/* | /dev/shm/* | /run/*)
                        violation "heap-dump-shared-path" \
                            "$file:$lineno: дамп кучи пишется в общий каталог $path" \
                            "Общий каталог доступен любому локальному пользователю."
                        ;;
                esac
            fi
        done < <(grep -nE 'HeapDumpOnOutOfMemoryError|HeapDumpPath=' "$ROOT/$file" 2>/dev/null || true)
    done < <(scan_files)

    # 8. приватные права в sanctioned-скрипте
    if [[ ! -f "$helper" ]]; then
        violation "heap-dump-unsafe-perms" \
            "нет $HEAP_DUMP_HELPER — нет механизма дампа с приватными правами" \
            "Дамп кучи при OOM: каталог 0700 внутри GRADLE_USER_HOME + umask 077 → файл 0600."
    else
        if ! grep -qE 'umask[[:space:]]+077' "$helper"; then
            violation "heap-dump-unsafe-perms" \
                "$HEAP_DUMP_HELPER: нет umask 077 → файл дампа будет 0644 (0666 & ~umask)" \
                "Права файла дампа = 0666 & ~umask; при umask 077 это 0600."
        fi
        if ! grep -qE 'chmod[[:space:]]+700' "$helper"; then
            violation "heap-dump-unsafe-perms" \
                "$HEAP_DUMP_HELPER: каталог дампов не приводится к правам 700" \
                "Каталог дампов — 0700 (внутри GRADLE_USER_HOME), иначе он доступен другим пользователям."
        fi
    fi
}

run_checks() {
    violations=0
    local files
    files="$(scan_files)"
    if [[ -z "$files" ]]; then
        color_red "❌ не найдено файлов конфигурации сборки под $ROOT — проверка не выполнена"
        return 2
    fi
    check_jackson
    check_dead_root_dependencies || return 2
    check_maven_pom
    check_heap_dump

    if [[ "$violations" -gt 0 ]]; then
        color_red "Найдено нарушений конфигурации сборки: $violations"
        return 1
    fi
    color_green "OK: конфигурация сборки без мёртвых дублей, Jackson — одной версии (BOM), дампы кучи — приватные"
    return 0
}

# --- self-test (фикстуры «до/после») --------------------------------------
# Фикстура повторяет layout репозитория.
make_good_tree() {
    local root="$1"
    mkdir -p "$root/karaoke-app" "$root/karaoke-web" "$root/karaoke-db" "$root/tools" "$root/.idea" "$root/deploy"
    cat > "$root/build.gradle.kts" <<'EOF'
plugins {
    id("org.springframework.boot") version "3.5.6"
}
group = "com.svoemesto"
version = "1"
repositories {
    mavenCentral()
}
tasks.withType<Test> {
    useJUnitPlatform()
}
EOF
    cat > "$root/settings.gradle.kts" <<'EOF'
include("karaoke-app", "karaoke-web", "karaoke-db")
EOF
    cat > "$root/gradle.properties" <<'EOF'
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx4g
EOF
    cat > "$root/karaoke-app/build.gradle.kts" <<'EOF'
dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.postgresql:postgresql:42.7.8")
}
EOF
    cat > "$root/karaoke-web/build.gradle.kts" <<'EOF'
dependencies {
    implementation(project(":karaoke-app"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
EOF
    cat > "$root/karaoke-db/build.gradle.kts" <<'EOF'
plugins {
    id("java")
}
EOF
    cat > "$root/.idea/misc.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectRootManager" version="2" languageLevel="JDK_18" default="true" project-jdk-name="18" project-jdk-type="JavaSDK">
    <output url="file://$PROJECT_DIR$/out" />
  </component>
</project>
EOF
    cat > "$root/$HEAP_DUMP_HELPER" <<'EOF'
#!/usr/bin/env bash
set -uo pipefail
DUMP_DIR="${GRADLE_USER_HOME}/heap-dumps"
prepare_dump_dir() {
    umask 077
    mkdir -p "$DUMP_DIR"
    chmod 700 "$DUMP_DIR"
}
prepare_dump_dir
exec "$GRADLEW" "-Dorg.gradle.jvmargs=-Xmx4g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${DUMP_DIR}/gradle-heap.hprof" "$@"
EOF
    printf '#!/usr/bin/env bash\necho hi\n' > "$root/deploy/do.sh"
}

# Проверка одного правила: «плохое» дерево должно давать код 1,
# «хорошее» — код 0.
expect_case() {
    local name="$1" tree="$2" want="$3"
    local rc
    ROOT="$tree"
    run_checks >/dev/null 2>&1
    rc=$?
    if [[ "$rc" -ne "$want" ]]; then
        color_red "✗ self-test: [$name] ожидался код $want, получен $rc"
        return 1
    fi
    color_green "✓ self-test: [$name] → код $rc"
    return 0
}

self_test() {
    local tmp rc=0
    tmp="$(mktemp -d)" || {
        echo "self-test: не удалось создать временный каталог" >&2
        return 2
    }
    trap "rm -rf '$tmp'" EXIT

    # 0. Хорошее дерево → PASS
    make_good_tree "$tmp/good"
    expect_case "корректная конфигурация" "$tmp/good" 0 || rc=1

    # 1. Явная версия Jackson → FAIL
    make_good_tree "$tmp/jackson"
    sed -i 's#jackson-databind")#jackson-databind:2.14.2")#' "$tmp/jackson/karaoke-app/build.gradle.kts"
    expect_case "jackson-explicit-version" "$tmp/jackson" 1 || rc=1

    # 2. Разные версии Jackson → FAIL
    make_good_tree "$tmp/skew"
    sed -i 's#jackson-databind")#jackson-databind:2.14.2")#' "$tmp/skew/karaoke-app/build.gradle.kts"
    sed -i 's#jackson-databind")#jackson-databind:2.17.2")#' "$tmp/skew/karaoke-web/build.gradle.kts"
    expect_case "jackson-version-skew" "$tmp/skew" 1 || rc=1

    # 3. Мёртвый блок dependencies в корне → FAIL
    make_good_tree "$tmp/deaddeps"
    printf 'dependencies {\n    implementation("org.postgresql:postgresql:42.7.7")\n}\n' >> "$tmp/deaddeps/build.gradle.kts"
    expect_case "dead-root-dependencies" "$tmp/deaddeps" 1 || rc=1

    # 4. pom.xml в корне → FAIL
    make_good_tree "$tmp/pom"
    printf '<project/>\n' > "$tmp/pom/pom.xml"
    expect_case "maven-pom-present (pom.xml)" "$tmp/pom" 1 || rc=1

    # 4b. Ссылка IDE на pom.xml → FAIL
    make_good_tree "$tmp/ideapom"
    sed -i 's#<project version="4">#<project version="4">\n  <component name="MavenProjectsManager"><option name="originalFiles"><list><option value="$PROJECT_DIR$/pom.xml" /></list></option></component>#' "$tmp/ideapom/.idea/misc.xml"
    expect_case "maven-pom-present (IDE)" "$tmp/ideapom" 1 || rc=1

    # 5. Глобальный дамп кучи → FAIL
    make_good_tree "$tmp/heapglobal"
    sed -i 's#^org.gradle.jvmargs=.*#org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/gradle-heap.hprof#' "$tmp/heapglobal/gradle.properties"
    expect_case "heap-dump-global-enabled" "$tmp/heapglobal" 1 || rc=1

    # 6. Дамп вне sanctioned-скрипта → FAIL
    make_good_tree "$tmp/heapother"
    printf 'GRADLE_OPTS="-XX:+HeapDumpOnOutOfMemoryError"\n' > "$tmp/heapother/deploy/do.sh"
    expect_case "heap-dump-outside-helper" "$tmp/heapother" 1 || rc=1

    # 7. Дамп в общий /tmp → FAIL
    make_good_tree "$tmp/heaptmp"
    printf 'JAVA_OPTS="-XX:HeapDumpPath=/tmp/gradle-heap.hprof"\n' > "$tmp/heaptmp/deploy/do.sh"
    expect_case "heap-dump-shared-path" "$tmp/heaptmp" 1 || rc=1

    # 8. Нет umask 077 / chmod 700 в sanctioned-скрипте → FAIL
    make_good_tree "$tmp/heapunsafe"
    sed -i '/umask 077/d' "$tmp/heapunsafe/$HEAP_DUMP_HELPER"
    expect_case "heap-dump-unsafe-perms" "$tmp/heapunsafe" 1 || rc=1

    # 9. Нет файлов конфигурации → код 2 (проверка не выполнена)
    local empty
    empty="$(mktemp -d)" || { rc=1; empty=""; }
    if [[ -n "$empty" ]]; then
        expect_case "нет конфигурации → код 2" "$empty" 2 || rc=1
        rm -rf "$empty"
    fi

    if [[ "$rc" -eq 0 ]]; then
        color_green "✅ self-test: все правила работают (плохое → FAIL, хорошее → PASS)"
    fi
    return "$rc"
}

if [[ "$SELF_TEST" -eq 1 ]]; then
    self_test
    exit $?
fi

run_checks
exit $?
