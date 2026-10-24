- **OpenProject #387** (2026-10-24, аудит #336, BR-523): добавлены хуки
  `karaoke-public-unit-tests` (юнит-тесты публичного SPA) и
  `marker-escape-guard` (BR-523); таблица хуков приведена в соответствие с
  фактическим `.pre-commit-config.yaml` (23 активных хука); в перечень
  CI-джобов добавлены джобы юнит-тестов, guard'а и e2e.
- **Pass 390** (2026-10-24, OpenProject #390, BR-581): guard-хуки
  `gradle-user-home-guard` / `docker-config-guard` и хук `ktlint` больше не
  зашивают путь `/home/nsa/Karaoke` — ожидаемый `GRADLE_USER_HOME` /
  `DOCKER_CONFIG` выводится из корня репозитория. Добавлен раздел
  «Guard-хуки (Pass 372–375)».