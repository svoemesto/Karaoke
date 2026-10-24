- **OpenProject #369 / BR-507** (2026-10-24): в CI добавлены джобы
  `backend-build` (compileKotlin + bootJar) и `backend-test`
  (`:karaoke-app:test :karaoke-web:test`) — до этого workflow только
  линтовал, и PR с некомпилируемым кодом или упавшим тестом мержился
  в master. Хук `check-feature-doc` и CI-джоб `docs-lint` теперь
  проверяют **оба** каталога per-feature документов
  (`docs/features/` и `archive/docs/features/`, BR-510).
  Автор: agent (Karaoke), аудит #336 (P-78).
- **OpenProject #387** (2026-10-24, аудит #336, BR-523): добавлены хуки
  `karaoke-public-unit-tests` (юнит-тесты публичного SPA) и
  `marker-escape-guard` (BR-523); таблица хуков приведена в соответствие с
  фактическим `.pre-commit-config.yaml` (23 активных хука); в перечень
  CI-джобов добавлены джобы юнит-тестов, guard'а и e2e.
