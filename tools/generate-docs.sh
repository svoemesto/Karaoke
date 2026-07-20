#!/usr/bin/env bash
# Генерирует HTML-документацию из KDoc/JSDoc для всех модулей проекта.
#
# Выходы:
#   - docs/api/dokka/karaoke-app/index.html   (Kotlin — backend)
#   - docs/api/dokka/karaoke-web/index.html   (Kotlin — public)
#   - docs/api/typedoc-webvue3/index.html     (JS — admin SPA)
#   - docs/api/typedoc-karaoke-public/index.html (JS — public SPA)
#
# Запуск: ./tools/generate-docs.sh
#         (или с флагом --clean для удаления старых выходов перед генерацией)

set -e

CLEAN=0
if [ "$1" == "--clean" ]; then
    CLEAN=1
fi

cd "$(dirname "$0")/.."

if [ $CLEAN -eq 1 ]; then
    echo "==> Очистка старых выходов..."
    rm -rf docs/api/dokka docs/api/typedoc-webvue3 docs/api/typedoc-karaoke-public
fi

echo "==> [1/4] Dokka для karaoke-app (Kotlin backend)..."
./gradlew :karaoke-app:dokkaJavadoc --no-daemon 2>&1 | tail -3

echo "==> [2/4] Dokka для karaoke-web (Kotlin public)..."
./gradlew :karaoke-web:dokkaJavadoc --no-daemon 2>&1 | tail -3

echo "==> [3/4] typedoc для webvue3 (admin SPA)..."
(cd webvue3 && bash -lc 'npx typedoc --out ../docs/api/typedoc-webvue3' 2>&1) | tail -3

echo "==> [4/4] typedoc для karaoke-public (public SPA)..."
(cd karaoke-public && bash -lc 'npx typedoc --out ../docs/api/typedoc-karaoke-public' 2>&1) | tail -3

# Собираем Dokka в один общий каталог
echo "==> Объединяю Dokka в docs/api/dokka/..."
rm -rf docs/api/dokka
mkdir -p docs/api/dokka
cp -r karaoke-app/build/dokka/javadoc docs/api/dokka/karaoke-app
cp -r karaoke-web/build/dokka/javadoc docs/api/dokka/karaoke-web

echo ""
echo "✅ Готово! Открой:"
echo "   docs/api/dokka/karaoke-app/index.html"
echo "   docs/api/dokka/karaoke-web/index.html"
echo "   docs/api/typedoc-webvue3/index.html"
echo "   docs/api/typedoc-karaoke-public/index.html"
