# Tasks: isVpnActive — несколько «домашних» стран (Pass 427, #151)

**Input**: Design documents from `/specs/427-vpn-multi-home-country/`
- [plan.md](./plan.md) (архитектура, контракты)
- [spec.md](./spec.md) (US1/US2, FR/NFR/SC)

**Format**: `[ID] [P?] [Story?] Description with file path`

---

## Phase 1: Core fix (User Story 1, Priority: P1) 🎯 MVP

- [ ] T001 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt`:
  - Добавить top-level `fun parseHomeCountries(raw: String): Set<String>` —
    split по `,`/`;`/пробельным, trim, uppercase, filter non-empty.
  - Добавить `fun vpnHomeCountries(): Set<String> = parseHomeCountries(Karaoke.vpnHomeCountry)`.
  - `isVpnActive()`: заменить `val homeCountry = ...` на `val homeCountries = vpnHomeCountries()`;
    `val isVpn = homeCountries.isNotEmpty() && country !in homeCountries`;
    лог `homeCountry=${homeCountries.toList().sorted()}`.
  - KDoc/комментарий: список стран, пример `DE,RU`.

- [ ] T002 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`:
  - `vpnHomeCountry`: обновить `description` (список через запятую/пробел, пример `DE,RU`).

**Checkpoint**: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin` — OK.

---

## Phase 2: Согласованность второго места (User Story 2, Priority: P2)

- [ ] T003 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt`
  (`checkLastAlbumYm`, ветка `AlbumSearchResult.Unknown`):
  - Заменить `country != "RU"` на `country.uppercase() !in vpnHomeCountries()`.

**Checkpoint**: compile OK.

---

## Phase 3: Tests

- [ ] T004 [P] Create `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/VpnHomeCountryTest.kt`:
  - `parseHomeCountries("DE,RU")` → {DE,RU}; `"de ru"` → {DE,RU}; `"DE;RU"` → {DE,RU};
    `""`/`" , ; "` → {}.
  - Сравнение: DE∈{DE,RU}=false-ВПН, RU∈{DE,RU}=false-ВПН, NL∉{DE,RU}=true-ВПН;
    legacy RU∈{RU}=false.

- [ ] T005 Run `:karaoke-app:test --tests "*VpnHomeCountryTest"` + full `:karaoke-app:test` — PASS.

**Checkpoint**: тесты зелёные.

---

## Phase 4: Documentation & Knowledge (SSoT)

- [ ] T006 [P] Modify `knowledge/system/utilities.md`:
  - Секция про `isVpnActive`/`vpnHomeCountries` (список стран, пример `DE,RU`).
- [ ] T007 [P] Modify `knowledge/domains/processing/components/karaoke-properties.md`:
  - Задокументировать `vpnHomeCountry` (был пробел): список ISO-кодов.
- [ ] T008 [P] Modify `docs/features/` (если применимо): `vpn-home-country.md`
  или секция в существующем per-feature doc.

**Checkpoint**: `tools/check-knowledge-structure.sh` 9/9; `lint-knowledge.py` exit 0.

---

## Phase 5: Polish & Validation

- [ ] T009 Validation:
  ```bash
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "*VpnHomeCountryTest"
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar --parallel
  bash tools/check-knowledge-structure.sh
  python3 tools/lint-knowledge.py
  ```
- [ ] T010 Write `specs/427-vpn-multi-home-country/report.md`.
- [ ] T011 Commit / push / PR (`[tracker-claim-151]` в сообщении).
- [ ] T012 CI: `gh pr checks` — all PASS.
- [ ] T013 Merge: `gh pr merge --merge` (БЕЗ `--delete-branch`).
- [ ] T014 Tracker: `add-comment 151 --file .../report.md`; `mark-review 151`.
- [ ] T015 Запросить согласие владельца на рестарт `karaoke-app` (nsa-i9) +
  установить `vpnHomeCountry=DE,RU` в настройках.

---

## Dependencies & Execution Order

- Phase 1 → Phase 3 (тесты на helper).
- Phase 2 → Phase 3.
- Phase 4 после 1+2.
- Phase 5 после 4.

### MVP Scope

T001–T005 + T009–T015 достаточно для production-fix; T006–T008 обязательны
(Tier-1 Knowledge SSoT) — в том же PR.

## Ready for implementation

tasks.md complete. Format validated. Dependencies mapped. MVP scope defined.
