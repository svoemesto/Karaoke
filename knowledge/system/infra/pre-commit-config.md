# Pre-commit config

> **Домен**: system (infrastructure)
> **Компонента**: детальное описание `.pre-commit-config.yaml`.

## Назначение

`.pre-commit-config.yaml` — **11 pre-commit хуков**, запускаемых
перед каждым `git commit`. Защищают от:
- Bad code style (ktlint, eslint, prettier).
- Broken doc links (lychee).
- Wrong per-feature doc structure.
- UI ↔ backend field mismatch.

## Установка

```bash
pip install pre-commit
pre-commit install  # активирует git-hook
pre-commit run --all-files  # ручной прогон
```

**Обход при срочном коммите**: `git commit --no-verify` (исключение).

## Активные хуки (11)

| # | ID | Команда | Назначение | Триггер |
|---|---|---|---|---|
| 1 | `ktlint` | `./gradlew ktlintCheck` | Kotlin formatting | `*.{kt,java}` |
| 2 | (закомментирован) `detekt` | `./gradlew detekt` | Kotlin code smells | ждёт Kotlin 2.2 |
| 3 | `eslint-webvue3` | `cd webvue3 && npm run lint:check` | ESLint для admin SPA | `webvue3/**/*.{vue,js,ts}` |
| 4 | `eslint-karaoke-public` | `cd karaoke-public && npm run lint:check` | ESLint для public SPA | `karaoke-public/**/*.{vue,js,ts}` |
| 5 | `prettier-webvue3` | `npx prettier --check` | Formatting webvue3 | (тот же) |
| 6 | `prettier-karaoke-public` | `npx prettier --check` | Formatting karaoke-public | (тот же) |
| 7 | `verify-doc-links` | `./tools/verify-doc-links.sh` | Lychee — broken links | `*.{md,markdown}` |
| 8 | `check-feature-doc` | `./tools/check-feature-doc.sh` | 6 sections + slug + status | `archive/docs/features/*.md` |
| 9 | `songedit-field-coverage` | `./tools/check-songedit-field-coverage.sh` | UI ↔ backend SongEdit | см. files |
| 10 | `endpoint-field-coverage` | `./tools/check-endpoint-field-coverage.sh` | UI ↔ backend all pairs | см. files |

## Конфигурация

```yaml
fail_fast: false   # прогоняем все хуки даже если один упал
default_install_hook_types: [pre-commit]
default_stages: [pre-commit]
```

- `fail_fast: false` — даём **полный отчёт** обо ВСЕХ проблемах, не
  останавливаемся на первой. Удобно для fix-everything-then-commit.
- `default_stages: [pre-commit]` — все хуки только в pre-commit
  (не в commit-msg, manual).

## Связь с CI

CI (GitHub Actions) использует **те же проверки**, но как **jobs**:
- `ktlintCheck`
- `eslint:check` + `prettier --check` (per module)
- `tools/check-kdoc-coverage.sh`
- `tools/check-jsdoc-coverage.sh`
- `tools/check-knowledge-structure.sh` (Pass 340+)
- `tools/check-knowledge-cross-links.sh` (Pass 340+)
- `pre-commit run --all-files`

`pre-commit run --all-files` = одна команда, которая покрывает всё
вышеперечисленное. Рекомендуется запускать **перед** PR.

## Известные TODO

- [ ] **detekt** — раскомментировать после Kotlin 2.2 (см. ADR).
- [ ] **Какие хуки в CI** — полный список jobs в lint.yml.
- [ ] **Pre-commit в web-server-deploy** — нужно ли на проде.

## Связь с другими компонентами

- **CI tools** ([ci-tools.md](ci-tools.md)) — что именно
  запускается.
- **AGENTS.md** — manual pre-commit workflow.
- **Constitution.md** — quality gates.

## Changelog

- **Pass 359** (2026-09-09): Initial. Автор: agent (Karaoke).