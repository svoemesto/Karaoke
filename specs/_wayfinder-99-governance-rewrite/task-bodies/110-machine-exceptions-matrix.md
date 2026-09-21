<!-- description task-тикета #110 (Step B: content rewrite) -->

## Question

Создать **новую секцию «Машинно-специфичные исключения» в AGENTS.md** как
единую матрицу, заменив разрозненные R-39 (AGENTS.md L289-295) и R-40
(constitution.md L342-345, L373-379).

Зачем:
- Из Q4 (Pass 379, #104 resolution): реальный content conflict R-39 vs R-40
  из-за двух матриц для одной машины. Нужна единая.
- AGENTS.md станет canonical host-specific реестром.

**Что должно быть в ответе**:

1. **Удалить** AGENTS.md L289-295 (Pass 282 / nsa-i9 секция).
2. **Удалить** constitution.md L342-345, L373-379 (dev-pc секция), оставить cross-ref
   на AGENTS.md.
3. **Добавить** в AGENTS.md новую секцию (~15-25 строк):

   ```markdown
   ### Hard Gate: Machine-Specific Exceptions (Pass 282)

   **Rule**: Specific machines have specific permissions for rebuilding/restarting containers.

   **Protocol**: Check the machine hostname (`hostname`). Apply the matching row.

   | Hostname | karoke-app rebuild | karaoke-app restart | Any container |
   |---|---|---|---|
   | `nsa-i9` / `nsa` | ✅ без согласия | ❌ только по согласию | ✅ по согласию |
   | `dev-pc` / `dev` | ✅ без согласия | ✅ без согласия | ✅ без согласия |

   **Failure**: Wrong row applied → `bash deploy/do.sh <op>` failed.
   ```

4. Cross-ref из constitution.md на эту секцию.

5. Запустить pre-commit + governance-review (semver bump конституции +).

## Notes

- Зависит от тикета #113 (главный rewrite AGENTS.md) — секция
  должна быть в финальном AGENTS.md.

## Тип

`[wayfinder:task]`.
