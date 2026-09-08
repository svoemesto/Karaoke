# L2 — Containers

> **Статус**: плейсхолдер. Содержимое будет наполнено в
> следующих спецификациях.

Здесь будет mermaid-диаграмма C4 L2: технические контейнеры внутри
границы Karaoke (`karaoke-app`, `karaoke-web`, `webvue3`,
`karaoke-public`, PostgreSQL, MinIO, MLT-пайплайн, …).

Шаблон:

```mermaid
flowchart TB
  subgraph Karaoke
    App[karaoke-app\nKotlin/Spring]
    Web[karaoke-web\nKotlin/Spring]
    AdminSPA[webvue3\nVue 3]
    PublicSPA[karaoke-public\nVue 3]
    DB[(PostgreSQL)]
    Storage[(MinIO)]
  end
```
