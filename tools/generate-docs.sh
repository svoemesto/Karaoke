#!/usr/bin/env bash
# Генерирует HTML-документацию из KDoc/JSDoc для всех модулей проекта.
#
# Выходы:
#   - build/api-docs/dokka/karaoke-app/index.html   (Kotlin — backend)
#   - build/api-docs/dokka/karaoke-web/index.html   (Kotlin — public)
#   - build/api-docs/typedoc-webvue3/index.html     (JS — admin SPA)
#   - build/api-docs/typedoc-karaoke-public/index.html (JS — public SPA)
#
# Запуск: ./tools/generate-docs.sh
#         (или с флагом --clean для удаления старых выходов перед генерацией)
#
# BR-598 (аудит #336, пакет PKG-S3-30): в скрипте был только `set -e` (без
# `-o pipefail`), а все четыре шага сборки были пайпами в `tail -3`.
# Код возврата пайпа = код возврата `tail` (всегда 0), поэтому падение
# dokka/typedoc НЕ прерывало скрипт: он доходил до «✅ Готово!» и сообщал
# об успехе при отсутствующей документации. Теперь `set -euo pipefail` и
# явная проверка кода возврата каждого шага.

set -euo pipefail

CLEAN=0
if [ "${1:-}" == "--clean" ]; then
    CLEAN=1
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# BR-599: Hard Gate AGENTS.md Tier-1 (Pass 372) требует gradle с
# GRADLE_USER_HOME. Раньше переменная не задавалась — wrapper писал в
# ~/.gradle (read-only в DSH-sandbox) и падал с FileNotFoundException.
# Путь — от корня репозитория (переносимо между машинами матрицы AGENTS.md).
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$REPO_ROOT/.gradle}"

if [ $CLEAN -eq 1 ]; then
    echo "==> Очистка старых выходов..."
    rm -rf build/api-docs
fi

echo "==> [1/4] Dokka для karaoke-app (Kotlin backend)..."
# BR-598: pipefail делает код возврата пайпа кодом возврата dokka/typedoc,
# а `if !` даёт понятное сообщение вместо молчаливого «успеха».
if ! ./gradlew :karaoke-app:dokkaJavadoc --no-daemon 2>&1 | tail -3; then
    echo "ERROR: dokkaJavadoc для karaoke-app завершился с ошибкой." >&2
    exit 1
fi

echo "==> [2/4] Dokka для karaoke-web (Kotlin public)..."
if ! ./gradlew :karaoke-web:dokkaJavadoc --no-daemon 2>&1 | tail -3; then
    echo "ERROR: dokkaJavadoc для karaoke-web завершился с ошибкой." >&2
    exit 1
fi

echo "==> [3/4] typedoc для webvue3 (admin SPA)..."
if ! (cd webvue3 && bash -lc 'npx typedoc --out ../build/api-docs/typedoc-webvue3' 2>&1) | tail -3; then
    echo "ERROR: typedoc для webvue3 завершился с ошибкой." >&2
    exit 1
fi

echo "==> [4/4] typedoc для karaoke-public (public SPA)..."
if ! (cd karaoke-public && bash -lc 'npx typedoc --out ../build/api-docs/typedoc-karaoke-public' 2>&1) | tail -3; then
    echo "ERROR: typedoc для karaoke-public завершился с ошибкой." >&2
    exit 1
fi

# Собираем Dokka в один общий каталог
echo "==> Объединяю Dokka в build/api-docs/dokka/..."
rm -rf build/api-docs/dokka
mkdir -p build/api-docs/dokka
cp -r karaoke-app/build/dokka/javadoc build/api-docs/dokka/karaoke-app
cp -r karaoke-web/build/dokka/javadoc build/api-docs/dokka/karaoke-web

echo ""
echo "✅ Готово! Открой:"
echo "   build/api-docs/dokka/karaoke-app/index.html"
echo "   build/api-docs/dokka/karaoke-web/index.html"
echo "   build/api-docs/typedoc-webvue3/index.html"
echo "   build/api-docs/typedoc-karaoke-public/index.html"
