# ADR-0005: Self-hosted ML (Ollama + SearXNG + Sheetsage + Demucs) instead of SaaS

* **Status**: Accepted
* **Date**: 2026-07-20 (Phase 001, NON-NEGOTIABLE — see Constitution § I)
* **Deciders**: Karaoke team

> **Russian version**: [`../../livedocs/architecture/decisions/0005-self-hosted-ml.md`](../../livedocs/architecture/decisions/0005-self-hosted-ml.md)

## Context

The karaoke project uses several **ML services**:

1. **Ollama** — local LLM (Mistral, Llama) for AI-search of song lyrics,
   summaries generation.
2. **SearXNG** — local meta-search engine for song lyrics search.
3. **Sheetsage** — local ML-model for key / BPM / chords.
4. **Demucs** — local model (Facebook Research) for stem separation.

Alternatives:
- OpenAI / Claude API (SaaS).
- Google Speech-to-Text / MusicBrainz (SaaS).
- License ASR / ML services are expensive.

## Decision

**Self-hosted ML**: install and run **locally** all 4 ML services.

- Ollama — binary on admin-machine.
- SearXNG — Docker container on admin-machine.
- Sheetsage — Docker container on admin-machine (Python).
- Demucs — Docker image (Python).

**Works ONLY on admin-machine** (where `karaoke-app` lives), not on prod.
See [ADR-0004](0004-karaoke-app-admin-only.md).

## Why self-hosted

1. **Internet independence**. Admin-machine works offline (historically —
   slow/unstable internet). SaaS does not work in this case.

2. **Cost**. SaaS (OpenAI, MusicBrainz) is paid. Self-hosted:
   - Ollama — free (Mistral 7B ~$0).
   - SearXNG — free (open-source).
   - Sheetsage — open-source.
   - Demucs — open-source (MIT).

3. **Privacy**. Lyrics are licensed material (Russian rock, not public domain).
   SaaS sends our texts to other servers. Self-hosted — does not.

4. **Reproducibility**. Set up once — works without changes for years.

5. **Performance**. Local models are faster (no network latency).

6. **Constitution § I NON-NEGOTIABLE** — fundamental principle of the project:
   - "The karaoke video production pipeline runs on admin-machine without
     dependency on external SaaS".
   - "Locally deployed ML-models (Ollama, Silero TTS, Sheetsage) are allowed".

## What works locally

| ML | Endpoint | Container/process | Usage |
|----|----------|-------------------|-------|
| **Ollama** | `http://localhost:11434` | Binary on admin-machine | AI-search of lyrics, summaries |
| **SearXNG** | `http://localhost:8888` | Docker container | Meta-search of lyrics |
| **Sheetsage** | `http://localhost:8000` | Docker container (Python) | key/BPM/chords |
| **Demucs** | Docker image (`python:3.10-slim`) | subprocess via ProcessBuilder | Stem separation |

All four — **only** on admin-machine. On prod they are not (and not
needed — only public HTTP there).

## Endpoints in code

```kotlin
// karaoke-app/.../KaraokeProperties.kt
val ollamaEndpoint = System.getenv("OLLAMA_ENDPOINT") ?: "http://localhost:11434"
val searxngEndpoint = System.getenv("SEARXNG_ENDPOINT") ?: "http://localhost:8888"
val sheetsageEndpoint = System.getenv("SHEETSAGE_ENDPOINT") ?: "http://localhost:8000"
const val demucsImage = "demucs-python:3.10-slim"
```

Through env-variables (on admin-machine — defaults localhost, on prod —
N/A, but also not needed).

## SaaS alternative — why rejected

| SaaS | Risk | Cost | Privacy |
|------|------|------|---------|
| OpenAI API | Internet required, noisy | $$$ | Texts go to OpenAI |
| Claude API | Internet required | $$$ | Texts go to Anthropic |
| Google STT | Internet, quota | $$ | Audio goes to Google |
| MusicBrainz | API limits | Free (but quota) | Metadata goes away |
| HuggingFace Inference | Internet, latency | $$ | HF SaaS models |

All SaaS require **stable internet** (historical problem of admin-machine).

## Cons of self-hosted

- ❌ **Hardware**: GPU needed for Demucs/LLM (CPU fallback slow).
- ❌ **Maintenance**: model updates, docker-compose, monitoring.
- ❌ **Cold-start**: Ollama loads model ~3 sec on first request (but later — instant).
- ❌ **Quality**: open-source models can be worse than closed ones (but for
  our task — sufficient).

## When it can change

If in the future cloud budget appears, can move to SaaS for some
components. Constitution § I requires only that **karaoke video
production doesn't depend on SaaS** — this means fallback to self-hosted OK,
but hybrid (SearXNG self + OpenAI for LLM) also acceptable.

## Alternatives Considered

- **Demucs SaaS** (Facebook hosted): doesn't exist as SaaS.
- **OpenAI Whisper API** for lyrics: no, we use LLM for summary, not for
  STT (Sheetsage — for audio analysis).
- **HuggingFace Inference (SaaS)**: considered, rejected due to internet
  dependency.

## References

- [Constitution § I](.specify/memory/constitution.md) — NON-NEGOTIABLE.
- [L1-system-context.md](../../livedocs/architecture/L1-system-context.md) — where Ollama/SearXNG/Sheetsage
  are mentioned as external systems (in reality — local).
- [L3-components.md](../../livedocs/architecture/L3-components.md) — LLM Integration component.
- [ADR-0004](0004-karaoke-app-admin-only.md) — where ML-infrastructure lives.