---
id: local-0012
title: "Plane как трекер проекта; перенос с локального OpenProject"
status: Draft
supersedes: null
superseded_by: null
date: 2026-10-27
modules:
  - system/infra
tags:
  - tracker
  - plane
  - openproject
  - wayfinder
---

# ADR-local-0012: Plane как трекер проекта; перенос с локального OpenProject

## Context & Problem

Трекер проекта — локальный OpenProject на `nsa-i9:8080`. Он не переживает
переезд на сервер, не даёт рабочие пространства для нескольких организаций и
требует отдельной инфраструктуры. Для команд нужен общий серверный трекер с
изоляцией по организациям.

## Decision | Решение

1. **Трекер — Plane Community Edition v1.4.2** на infra-сервере
   (`tracker.sm-karaoke.ru`). Рабочие пространства (`workspace`) = организации:
   `svoemesto` и `dub2000`.
2. **Пользователи**: `nsa-svoemesto` (instance admin) и `ivan`; вход по email и
   паролю, регистрация закрывается после переноса.
3. **Миграция данных**: work packages OpenProject (проект `karaoke`) переносятся
   в Plane-проект рабочего пространства `svoemesto` с сохранением комментариев и,
   по возможности, вложений. Маппинг: статусы → `New / In progress / Done /
   Cancelled`, типы → labels, parent/child → sub-issues.
4. **Инструменты и процесс** переключаются на Plane: `tools/tracker.sh`,
   проверка привязки спеки к issue, `AGENTS.md`, конвенции wayfinder.
5. **OpenProject** после переноса — read-only на месяц, затем выключение.

## Rationale | Обоснование

* **Оставить OpenProject**: отклонено — локальная машина, нет серверного
  доступа и рабочих пространств.
* **Jira/YouTrack**: отклонено — внешние платные сервисы при курсе на
  собственный хостинг.
* **Переносить только открытые задачи**: пока не решено; история нужна
  wayfinder-картам и отчётам, поэтому базовый вариант — полный перенос.

## Consequences | Последствия

1. **Статус**: Draft — данные ещё не перенесены; текущий трекер остаётся
   OpenProject.
2. **Запрет**: не выключать OpenProject до завершения сверки перенесённых
   задач.
3. **Известное ограничение**: Plane CE — лимит 12 пользователей на workspace.
4. **Required Update** после миграции: `knowledge/system/infra/tracker.md`,
   `AGENTS.md` (workflow), `docs/tracker-setup.md`, конвенции wayfinder.
