# Contract: манифест проверок `config/ci/checks.yml`

**Feature**: 692-selective-ci-checks | **Дата**: 2026-11-01 | **Версия схемы**: `1`

Манифест — **единственный носитель** правила выборочного запуска (FR-006, R-48). CI, локальный
пред-PR прогон и гейт согласованности читают его и не дублируют правило.

---

## 1. Файл и размещение

- Путь: `config/ci/checks.yml` (каталог `config/` уже несёт `ktlint/`, `detekt/`, `frontend/`,
  `knowledge/` — машинные настройки проверок живут там).
- Формат: YAML, парсится PyYAML (`yaml.safe_load`).
- Версия схемы — поле `version`. Поддерживается `1`. Неизвестная версия → код возврата `2`
  («проверка не выполнена»), а не «зелёно» (FR-014).

## 2. Верхний уровень

```yaml
version: 1
unknown_paths: all
groups:
  ssoT:
    members: [ssot-impact, knowledge-structure]
checks:
  - id: backend-build
    title: backend build (Kotlin)
    gate: true
    cost: heavy
    mode: paths
    paths:
      - karaoke-app/**
      - karaoke-web/**
    evidence: "lint.yml:270; settings.gradle.kts:1-2"
```

| Ключ | Тип | Обязательность | Значение |
|---|---|---|---|
| `version` | int | обязательно | `1` |
| `unknown_paths` | str | опционально, default `all` | `all` — путь вне зон запускает все проверки (FR-004); `none` — только совпавшие |
| `groups` | map | опционально | имя → `{members: [id, …]}` |
| `checks` | list | обязательно | список проверок |

## 3. Запись проверки

| Ключ | Тип | Обязательность | Значение |
|---|---|---|---|
| `id` | str | обязательно | machine-readable ключ = **job-id в workflow** (1:1). Допустимые символы: `a-z`, `0-9`, `-` |
| `title` | str | обязательно | человекочитаемое имя (сводки, отчёты) |
| `gate` | bool | опционально, default `true` | входит ли в блокирующий набор |
| `cost` | str | опционально, default `light` | `heavy` \| `light` \| `info` |
| `mode` | str | обязательно | `paths` \| `always` |
| `paths` | list[str] | обязателен при `mode: paths` | зоны интереса (глобы), непустой |
| `evidence` | str | обязателен при `mode: paths` | доказательство зоны: `путь:строка` (FR-001) |

Смысл полей — в [../data-model.md](../data-model.md) (§2–§4).

## 4. Семантика глоба

Git-стиль, матчинг по всему пути файла.

| Глоб | Что матчит | Пример |
|---|---|---|
| `**` | любой набор сегментов, включая ноль | `karaoke-app/**` |
| `*` | любые символы внутри одного сегмента (без `/`) | `deploy/*/Dockerfile*` |
| `dir/**` | всё внутри каталога `dir` | `knowledge/**` |
| `dir` | каталог целиком (равно `dir/**`) | `knowledge` |
| `**/name` | файл `name` в любом каталоге | `**/Dockerfile*` |
| `a/b/c.kt` | конкретный файл | `karaoke-app/build.gradle.kts` |

Правила сопоставления:

1. путь сравнивается относительно корня репозитория, без ведущего `./`;
2. регистр учитывается;
3. регулярные выражения, отрицания (`!`), порядок правил — не поддерживаются;
4. зона проверки — **объединение** её глобов (любой глоб совпал → зона затронута).

## 5. Группы

- `groups.<имя>.members` — список `id` проверок;
- применимость группы = применимость **любого** члена; применяются **все** члены (FR-009);
- проверка входит максимум в одну группу;
- члены группы должны существовать среди `checks[].id`.

## 6. Инварианты (проверяет `tools/check-ci-checks-manifest.sh`)

| # | Инвариант | Код |
|---|---|---|
| M-1 | `version` ∈ {1}; `unknown_paths` ∈ {`all`, `none`} | 1 / 2 |
| M-2 | `checks[].id` уникальны; 1:1 с job-id обеих копий workflow | 1 |
| M-3 | `title` непустой | 1 |
| M-4 | `mode: paths` ⇒ `paths` непустой и `evidence` непустой | 1 |
| M-5 | `mode: always` ⇒ `paths` отсутствует/пуст | 1 |
| M-6 | каждый глоб матчит ≥ 1 tracked-файл (мёртвая зона) | 1 |
| M-7 | `cost` ∈ {`heavy`, `light`, `info`}; `gate: false` ⇒ `cost: info` | 1 |
| M-8 | члены групп существуют; проверка — максимум в одной группе | 1 |
| M-9 | `id` не совпадает со служебными джобами `changes` / `ci-summary` | 1 |
| M-10 | `if:`-условия в двух копиях идентичны по job-id | 1 |
| M-11 | `needs` тяжёлого джоба `karaoke-public-e2e` покрывает все `cost: heavy` (FR-018/FR-019) | 1 |

## 7. Порядок и читаемость

- записи `checks` идут в порядке job-id в workflow — так карта «джоб → зона» читается как
  таблица инвентаря KARAOKE-366;
- каждая запись содержит `evidence` со ссылкой на код проверки — без доказательства зона не
  принимается (FR-001);
- комментарии в YAML допустимы и приветствуются (пояснение «почему зона такая»).

## 8. Пример (фрагмент реальной записи)

```yaml
  - id: karaoke-public-e2e
    title: e2e (karaoke-public)
    gate: true
    cost: heavy
    mode: paths
    paths:
      - karaoke-public/**
      - deploy/karaoke-public/Dockerfile
    evidence: "lint.yml:582,598,604,624; deploy/karaoke-public/Dockerfile:16,29"

  - id: direct-commits-guard
    title: direct commits to master
    gate: true
    cost: light
    mode: always
    evidence: "tools/check-direct-commits.sh:44-45,204"

  - id: baseline-stats
    title: Baseline stats (informational)
    gate: false
    cost: info
    mode: paths
    paths:
      - config/ktlint/baseline-*.xml
      - config/detekt/baseline-*.xml
      - webvue3/.eslint-baseline.json
      - karaoke-public/.eslint-baseline.json
    evidence: "tools/baseline-stats.sh:20-23,70,84,96-97"
```
