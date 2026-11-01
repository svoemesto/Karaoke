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
| `class` | str | опционально, default `check` | `check` \| `infrastructure` — класс записи (см. ниже) |
| `title` | str | обязательно | человекочитаемое имя (сводки, отчёты) |
| `gate` | bool | опционально, default `true` | входит ли в блокирующий набор |
| `cost` | str | опционально, default `light` | `heavy` \| `light` \| `info` |
| `mode` | str | обязательно | `paths` \| `always` |
| `paths` | list[str] | обязателен при `mode: paths` | зоны интереса (глобы), непустой |
| `evidence` | str | обязателен при `mode: paths` | доказательство зоны: `путь:строка` (FR-001) |
| `watch` | list[str] | опционально (только `class: infrastructure`) | пути, которые служебная запись инспектирует; на применимость не влияет (запись и так всегда применима), но входит в объединение зон — иначе файлы самого правила считались бы «неизвестными». **Catch-all запрещён** (`**`, `*`, `/*`, `**/*` — ошибка): иначе объединение зон покрывает всё и неизвестных путей не остаётся никогда |
| `watch_note` | str | обязателен при непустом `watch` | что именно служебная запись инспектирует (обоснование, обязательное для ревью) |
| `known_unmapped` | list[{paths, note}] | необязательно | перечень путей, которые **утверждённо не читает ни одна CI-проверка**; правила — как у `watch` (нет catch-all, есть `note`, нет пересечения с зонами, глоб матчит ≥ 1 tracked-файл) |
| `known_unmapped_enabled` | bool | по умолчанию `false` | **переключатель секции**: включение — одной настройкой (CLI `--known-unmapped` > env `CI_CHECKS_KNOWN_UNMAPPED` > это поле); при `false` поведение строго по FR-004 |

Смысл полей — в [../data-model.md](../data-model.md) (§2–§4).

### 3.1 Два класса записей

Манифест — **полный реестр job-id обеих копий workflow: 35 записей** (31 существующий job-id +
`direct-commits-guard` + `ci-checks-manifest-guard` + `changes` + `ci-summary`). Инвариант
«джоб ↔ запись» **тотальный, без списков исключений** (D-19).

| Класс | `class` | Что это | Зоны | `mode` | Блокирует |
|---|---|---|---|---|---|
| Проверка контура | `check` (default) | 31 существующий job-id + `direct-commits-guard` | `mode: paths` — зона обязательна; `mode: always` — зоны нет | `paths` \| `always` | `gate: true` (по умолчанию) |
| Инфраструктурная | `infrastructure` | `changes`, `ci-summary`, `ci-checks-manifest-guard` | нет | `always` | `changes`/`ci-summary` — `gate: false`; `ci-checks-manifest-guard` — `gate: true` |

Правила класса `infrastructure`: `mode: always`, `paths` пустые, зон нет; `evidence` указывает,
чем реализована служебная запись, и потому допускается;
«мёртвая зона» к ним не применяется. Гейт согласованности (`ci-checks-manifest-guard`) —
инфраструктурная запись с `gate: true`: он запускается **всегда** (дешёвый, а его проверка
«мёртвая зона» зависит от состава tracked-файлов), и его падение блокирует слияние.

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
| `/pattern` | паттерн **привязан к корню** репозитория | `/*` — файлы верхнего уровня |

Правила сопоставления:

1. путь сравнивается относительно корня репозитория, без ведущего `./`;
2. регистр учитывается;
3. регулярные выражения, отрицания (`!`), порядок правил — не поддерживаются;
4. ведущий `/` в паттерне привязывает его к корню репозитория (`/*` — только верхний уровень);
5. зона проверки — **объединение** её глобов (любой глоб совпал → зона затронута).

**Зоны проверок «на отсутствие»** (например, `root-junk-guard` ищет мусор в корне репозитория)
выражаются зоной **места, где запрещённый файл мог бы появиться**, а не самим запрещённым именем:
`root-junk-guard` → `/*` (любой файл верхнего уровня). Прямое имя запрещённого файла в зоне
недопустимо: такой глоб не матчит ни одного tracked-файла → «мёртвая зона» (M-6).
Отсутствие запрещённого каталога/файла ловится иначе — такой путь не попадает ни в одну зону,
а значит по `unknown_paths: all` запускается полный набор проверок.

## 5. Группы

- `groups.<имя>.members` — список `id` проверок;
- применимость группы = применимость **любого** члена; применяются **все** члены (FR-009);
- проверка входит максимум в одну группу;
- члены группы должны существовать среди `checks[].id`.

## 6. Инварианты (проверяет `tools/check-ci-checks-manifest.sh`)

| # | Инвариант | Код |
|---|---|---|
| M-1 | `version` ∈ {1}; `unknown_paths` ∈ {`all`, `none`} | 1 / 2 |
| M-2 | `checks[].id` уникальны; **тотальное** соответствие job-id обеих копий workflow (1:1, без исключений) | 1 |
| M-3 | `title` непустой | 1 |
| M-4 | `mode: paths` ⇒ `paths` непустой и `evidence` непустой | 1 |
| M-5 | `mode: always` ⇒ `paths` отсутствует/пуст | 1 |
| M-6 | каждый глоб из `paths` матчит ≥ 1 tracked-файл (мёртвая зона) — **только для записей с `mode: paths`** | 1 |
| M-7 | `cost` ∈ {`heavy`, `light`, `info`}; `gate: false` ⇒ `cost: info` | 1 |
| M-8 | члены групп существуют; проверка — максимум в одной группе | 1 |
| M-9 | `class: infrastructure` ⇒ `mode: always`, `paths` пустые (`evidence` допускается — он указывает, чем реализована служебная запись) | 1 |
| M-10 | `if:`-условия в двух копиях идентичны по job-id | 1 |
| M-11 | `needs` джоба `karaoke-public-e2e` покрывает все записи с `cost: heavy` (FR-018/FR-019) | 1 |
| M-12 | `needs` в двух копиях идентичны по job-id (порядок выполнения одинаков, FR-019) | 1 |
| M-13 | каждый глоб из `watch` матчит ≥ 1 tracked-файл («мёртвая зона»); catch-all паттерны запрещены; при непустом `watch` обязателен `watch_note` | 1 |
| M-14 | секция `known_unmapped`: каждый глоб матчит ≥ 1 tracked-файл, не пересекается ни с одной зоной, catch-all запрещён, у записи есть `note`; гейт печатает размер секции и состояние переключателя | 1 |

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

  - id: ci-checks-manifest-guard
    class: infrastructure
    title: CI checks manifest guard (R-47 п. 3, FR-012/FR-013)
    gate: true
    cost: light
    mode: always
    watch:
      - config/ci/checks.yml
      - tools/ci-checks.py
      - tools/check-ci-checks-manifest.sh
      - tools/fixtures/ci-checks-manifest/**
      - .forgejo/workflows/lint.yml
      - .github/workflows/lint.yml
    evidence: "tools/check-ci-checks-manifest.sh:1"

  - id: changes
    class: infrastructure
    title: changed files → applicable checks
    gate: false
    cost: info
    mode: always
    watch:
      - config/ci/checks.yml
      - tools/ci-checks.py
    evidence: "tools/ci-checks.py --emit-outputs"

  - id: ci-summary
    class: infrastructure
    title: CI summary (applicable checks)
    gate: false
    cost: info
    mode: always
    watch:
      - config/ci/checks.yml
      - tools/ci-checks.py
    evidence: "tools/ci-checks.py --summary"
```

**Инфраструктурные записи не имеют зон** — значит «мёртвая зона» (M-6) к ним не применяется, а
применимость инфраструктурных джобов не зависит от зон: `changes` и `ci-summary` работают всегда,
`ci-checks-manifest-guard` — тоже (см. D-19).
