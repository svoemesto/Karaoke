# Karaoke Project Guidelines

## Project Overview
"Karaoke" (svoemesto) is a self-hosted pipeline for automated karaoke video production.

## Key Documentation Files
This project has two main documentation files that contain detailed technical information:

1. **DEVELOPMENT.md** — Main development guide with:
   - Project structure and modules
   - Build and deployment commands
   - Architecture notes and key invariants
   - Common pitfalls and solutions

2. **docs/architecture-notes-archive.md** — Detailed history of features and bug fixes:
   - Chronological records of implemented features
   - Debugging notes and troubleshooting guides
   - Technical decisions and their rationale

## Working with Documentation
When you need to understand:
- How the project is structured → read DEVELOPMENT.md
- Why a specific feature works the way it does → check docs/architecture-notes-archive.md
- Common issues and their fixes → both files contain relevant information

**Important:** Before making significant changes, consult these files to understand the existing patterns and avoid known pitfalls.

## Tech Stack
- Backend: Kotlin/Spring Boot, Gradle, JDK 17
- Frontend: Vue 3 + Vite (webvue3 for admin, karaoke-public for public site)
- Database: PostgreSQL
- Storage: MinIO
- Video rendering: MLT framework (melt CLI)
- Audio processing: Demucs, ffmpeg, Sheetsage

## Development Workflow
- All build/deploy commands are in `deploy/do.sh`
- Always run commands from the `deploy/` directory
- Check DEVELOPMENT.md for specific command syntax and common issues
- The project uses a dual-database sync system (LOCAL ↔ SERVER)

## Code Style
- Follow existing patterns in the codebase
- Use nullable types for database columns that allow NULL
- Avoid `is*` prefix for boolean fields in DTOs (Jackson serialization issue)
- Always URL-encode query parameters with special characters
