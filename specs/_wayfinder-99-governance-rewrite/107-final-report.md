# #107 Final Resolution — no-mp4-mentions guard (R-11)

## ✅ Status: done (PR #484 MERGEABLE)

## Implementation Summary

**Branch**: `388-no-mp4-mentions-guard`
**PR**: https://github.com/svoemesto/Karaoke/pull/484
**Commit**: `c07f278f` (final, после rebase + amend — убран foreign commit `66946300` (JPA) от параллельного PR #483, убраны Pass 372-375 guards)

## Files

1. `tools/check-no-mp4-mentions.sh` (164 строк, chmod +x) — guard для R-11
   (MP4/скачивание запрет по оферте «доступ только онлайн»).
   Grep `\bmp4\b|скачивани` в `webvue3/src/**/*.{vue,js,ts}`,
   `karaoke-public/src/**/*.{vue,js,ts}`, `karaoke-app/src/**/*.kt`,
   `karaoke-web/src/**/*.kt`. Excludes: `*/node_modules/*`, `*/build/*`,
   `*/dist/*`, `*/.git/*`, `*/bootstrap*.min.{js,css}`, `*.test.*`,
   `*.spec.*`, guard и baseline файлы.

2. `tools/check-no-mp4-mentions.baseline` (130 строк) — legacy-исключения
   для legitimate use MP4 как формата/пайплайна. Формат `filename:line`.

3. `.pre-commit-config.yaml` — добавлен hook `no-mp4-mentions-guard`
   (Pass 379, R-11). **Без** JPA-hook (тот уже будет через PR #483).

4. `.github/workflows/lint.yml` — добавлен job `no-mp4-mentions-guard`
   со step `bash tools/check-no-mp4-mentions.sh --quiet`. **Без**
   Pass 372-375 guards и **без** JPA-step.

## Exit codes

- Локальный прогон без baseline: **exit 1** (130 violations найдены).
- Локальный прогон С baseline: **exit 0** (все 130 в baseline).
- Synthetic negative (`mp4` в новом файле): exit 1 + pretty-print.
- Pre-commit: Passed.

## Categories of 130 violations (все legitimate use MP4)

1. **Backend MP4 rendering** (passive infrastructure) — ~25 строк
   (PlayerMp4RenderService, Constants, ApiController, mlt/Consumer).
2. **File names `*.mp4` в model** — ~30 строк (Song.kt, SongOutputFile.kt).
3. **Auto-publish в VK/Telegram** (admin pipeline, не публичное скачивание) —
   ~25 строк (VkApiClient, TelegramApiClient, VkAutoPublishService).
4. **KaraokeProcess.kt / admin-render** — ~10 строк.
5. **Misc backend** (security lists, LLM tools, AlbumCoverFinder) — ~10 строк.
6. **karaoke-public UI текст оферты** (явные фразы «без скачивания») — 4 строки.
7. **webvue3 + karaoke-public player (headless mp4 export, admin)** — ~25 строк.

**Ни одно из них не нарушает оферту** (offer.html — «доступ только онлайн»),
поэтому все идут в baseline.

## CI Status

10/10 SUCCESS (включая **`no-mp4-mentions guard (Pass 379, R-11)`**).

## Hard-gate coverage

8 → **9 из 50 правил** (16% → 18%) — R-11.

## Compliance

- [x] Knowledge-first MUST #0 выполнен.
- [x] R-11 enforce'ится machine-readable.
- [x] Hard-gate coverage 8 → 9.
- [x] CI 7/7 зелёная.

## Process notes (важно для следующих implementation-сессий)

- Branch `388-...` имел **чужой commit `66946300`** (JPA) от параллельного
  PR #483 из-за race condition в shared workspace — решено через rebase
  + amend + force-push вручную (Pass 379 race condition).
- Это **прецедент** для governance-PR #486 «Subagent workspace isolation»
  (каждое субагент → свой `git worktree`).

— Implementation-субагент для OP #107.
