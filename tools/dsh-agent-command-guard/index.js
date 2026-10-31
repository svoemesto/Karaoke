/**
 * agent-command-guard — перехват опасных команд агента до выполнения.
 *
 * Плагин хоста DeepSeek Harness (DSH). Подписан на событие `tools/pre-execute`
 * и проверяет текст вызова инструмента `bash` ДО его диспетчеризации:
 *   - пишет запись в append-only журнал (NDJSON) — след;
 *   - в режиме `deny` — запрещает вызов;
 *   - в режиме `ask`  — запрашивает одобрение владельца через approval-канал
 *     (тот же, что у эскалаций песочницы; при его отсутствии — запрет).
 *
 * Назначение: не «сделать нарушение невозможным» (текстовое правило обходимо:
 * `d''ocker`, `$(echo docker)`, запуск из скрипта-файла, `podman`, `systemctl`,
 * `kill <pid>`, `docker exec`), а «остановить, спросить и оставить след».
 *
 * Что ловится:
 *   - текстовые правила из политики (`~/.dsh/policies/deny-commands.json`);
 *   - состояниезависимые правила: текущая ветка читается из `<каталог>/.git/HEAD`
 *     (хост-процесс читает файлы вне песочницы агента) — так ловится `git commit`
 *     в master, что по тексту команды неотличимо от коммита в фиче.
 *
 * Границы:
 *   - покрывается только инструмент `bash` (не terminal/persistent-bash/PTC/CUA
 *     и не внутренние действия MCP-серверов);
 *   - согласие владельца по тексту команды НЕ определяется (запуск санкционированной
 *     команды без одобрения — состояние, а не текст).
 *
 * Пути настраиваются конфигом строки профиля или переменными окружения
 * `DSH_GUARD_POLICY` / `DSH_GUARD_JOURNAL`; значения по умолчанию не содержат
 * пути конкретной машины.
 */
import { appendFileSync, mkdirSync, readFileSync, statSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join, resolve } from 'node:path'

export const inject = ['tools']

/** Инструменты, чьи аргументы содержат исполняемую команду. */
const SHELL_TOOLS = new Set(['bash'])

/**
 * Маскирование секретов ДО записи в журнал
 * (Hard Gate Secrets, BR-551 / BR-552 / BR-605: секрет не должен попасть в argv).
 */
const REDACTIONS = [
  [/(PGPASSWORD\s*=\s*)(\S+)/gi, '$1***'],
  [/(--password[= ])(\S+)/gi, '$1***'],
  [/(-u\s+)([^\s:]+):([^\s]+)/g, '$1$2:***'],
  [/(Authorization:\s*(?:Basic|Bearer)\s+)(\S+)/gi, '$1***'],
  [/\b(password|passwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)=(\S+)/gi, '$1=***'],
  [/(X-Api-Key|X-Auth-Token):\s*(\S+)/gi, '$1: ***'],
]

/**
 * Маскировать секреты в тексте команды.
 * @param {string} text
 * @returns {string}
 */
export function redact(text) {
  let out = String(text ?? '')
  for (const [pattern, replacement] of REDACTIONS) out = out.replace(pattern, replacement)
  return out
}

/**
 * Разобрать команду на сегменты по управляющим операторам.
 * Грубая эвристика: оператор внутри кавычек может разрезать команду — это даёт
 * лишние сегменты, но не ложные срабатывания (правило проверяет каждый
 * сегмент независимо).
 * @param {string} command
 * @returns {string[]}
 */
export function splitSegments(command) {
  const text = String(command ?? '')
  const parts = text.split(/(\n|&&|\|\||;)/)
  const segments = []
  for (const part of parts) {
    if (part === undefined || part === '\n' || part === '&&' || part === '||' || part === ';') continue
    for (const piece of part.split('|')) {
      const trimmed = piece.trim()
      if (trimmed) segments.push(trimmed)
    }
  }
  return segments
}

/**
 * Разобрать команду на сегменты, отслеживая текущий каталог (`cd`).
 * Каталог нужен состояниезависимым правилам: ветка читается из `.git/HEAD`
 * ТОГО каталога, в котором реально выполнится `git commit` — иначе коммит
 * в worktree фичи выглядел бы как коммит в master.
 * @param {string} command
 * @param {string} workdir
 * @returns {Array<{ text: string, dir: string }>}
 */
export function splitSegmentsWithDir(command, workdir) {
  const parts = String(command ?? '').split(/(\n|&&|\|\||;)/)
  const segments = []
  let currentDir = workdir || process.cwd()
  for (const part of parts) {
    if (part === undefined || part === '\n' || part === '&&' || part === '||' || part === ';') continue
    for (const piece of part.split('|')) {
      const trimmed = piece.trim()
      if (!trimmed) continue
      const cd = trimmed.match(/^cd\s+['"]?([^'"\s]+)['"]?$/)
      if (cd) {
        currentDir = cd[1].startsWith('/') ? cd[1] : resolve(currentDir, cd[1])
        continue
      }
      segments.push({ text: trimmed, dir: currentDir })
    }
  }
  return segments
}

/**
 * Прочитать имя текущей ветки из `.git/HEAD` рабочего каталога.
 * Поддерживает обычный репозиторий и worktree (`.git` — файл с `gitdir:`).
 * @param {string} workdir
 * @returns {string|null}
 */
export function resolveGitBranch(workdir) {
  if (!workdir) return null
  try {
    const dotGit = join(workdir, '.git')
    const stat = statSync(dotGit)
    let gitDir = dotGit
    if (stat.isFile()) {
      const content = readFileSync(dotGit, 'utf8')
      const match = content.match(/gitdir:\s*(.+)/)
      if (!match) return null
      gitDir = resolve(workdir, match[1].trim())
    } else if (!stat.isDirectory()) {
      return null
    }
    const head = readFileSync(join(gitDir, 'HEAD'), 'utf8').trim()
    const branch = head.match(/^ref:\s*refs\/heads\/(.+)$/)
    return branch ? branch[1] : null
  } catch {
    return null
  }
}

/**
 * Загрузить политику запретов (JSON):
 * { "version": 1, "rules": [ { "id", "severity", "pattern", "reason", "mode",
 *                               "state", "branchEquals", "workdirPrefix" } ] }
 * Отсутствующий или битый файл — пустой набор правил (fail-open по правилам;
 * ошибка записи в журнал не отменяет решение).
 * @param {string} policyPath
 * @returns {Array<object>}
 */
export function loadPolicy(policyPath) {
  try {
    const parsed = JSON.parse(readFileSync(policyPath, 'utf8'))
    const rules = Array.isArray(parsed?.rules) ? parsed.rules : []
    return rules
      .filter(rule => rule && typeof rule.pattern === 'string')
      .map(rule => {
        try {
          return {
            id: String(rule.id ?? rule.pattern),
            severity: rule.severity ?? 'medium',
            reason: rule.reason ?? '',
            mode: rule.mode === 'deny' || rule.mode === 'ask' ? rule.mode : null,
            state: rule.state ?? null,
            branchEquals: rule.branchEquals ?? null,
            workdirPrefix: rule.workdirPrefix ?? null,
            pathContains: rule.pathContains ?? null,
            regex: new RegExp(rule.pattern, 'i'),
          }
        } catch {
          return null
        }
      })
      .filter(Boolean)
  } catch {
    return []
  }
}

/**
 * Сопоставить команду с политикой.
 * @param {string} command
 * @param {Array<object>} rules
 * @param {{ workdir?: string }} [context]
 * @returns {{id:string, severity:string, reason:string, mode:string|null, segment:string}|null}
 */
export function matchCommand(command, rules, context = {}) {
  const workdir = context.workdir ?? ''
  const segments = splitSegmentsWithDir(command, workdir)

  for (const rule of rules ?? []) {
    if (rule.workdirPrefix && !segments.some(segment => segment.dir.startsWith(rule.workdirPrefix))) continue
    if (rule.pathContains && !segments.some(segment => segment.dir.includes(rule.pathContains))) continue

    for (const segment of segments) {
      if (rule.state === 'git-branch') {
        if (!rule.regex.test(segment.text)) continue
        const branch = resolveGitBranch(segment.dir)
        if (branch && rule.branchEquals && branch === rule.branchEquals) {
          return { id: rule.id, severity: rule.severity, reason: rule.reason, mode: rule.mode, segment: segment.text }
        }
        continue
      }
      if (rule.regex.test(segment.text)) {
        return { id: rule.id, severity: rule.severity, reason: rule.reason, mode: rule.mode, segment: segment.text }
      }
    }
  }
  return null
}

/**
 * Однострочная запись в append-only журнал. Ошибка записи не отменяет решение.
 * @param {string} journalPath
 * @param {object} record
 */
function writeJournal(journalPath, record) {
  try {
    mkdirSync(dirname(journalPath), { recursive: true, mode: 0o700 })
    appendFileSync(journalPath, `${JSON.stringify(record)}\n`, { mode: 0o600 })
  } catch (error) {
    try {
      process.stderr.write(`[agent-command-guard] journal write failed: ${error?.message ?? error}\n`)
    } catch { /* stderr недоступен — продолжаем */ }
  }
}

/**
 * @param {object} ctx контекст плагина DSH
 * @param {{ mode?: 'deny'|'ask', journalPath?: string, policyPath?: string, logAll?: boolean }} config
 * @returns {() => void} функция снятия подписки
 */
export function apply(ctx, config) {
  const defaultMode = config?.mode === 'deny' ? 'deny' : 'ask'
  const policyPath = config?.policyPath
    ?? process.env.DSH_GUARD_POLICY
    ?? join(homedir(), '.dsh', 'policies', 'deny-commands.json')
  const journalPath = config?.journalPath
    ?? process.env.DSH_GUARD_JOURNAL
    ?? join(homedir(), '.dsh', 'ledger', 'agent-commands.ndjson')
  const logAll = config?.logAll !== false

  let cachedRules = []
  let cachedMtime = -1
  const rules = () => {
    try {
      const mtime = statSync(policyPath).mtimeMs
      if (mtime !== cachedMtime) {
        cachedRules = loadPolicy(policyPath)
        cachedMtime = mtime
      }
    } catch {
      cachedRules = []
    }
    return cachedRules
  }

  return ctx.on('tools/pre-execute', async (exec, next) => {
    if (!SHELL_TOOLS.has(exec.name)) return next()

    const args = exec.arguments
    const command = args && typeof args.command === 'string' ? args.command : null
    if (command === null) return next()

    const workdir = typeof args.workdir === 'string' ? args.workdir : ''
    const hit = matchCommand(command, rules(), { workdir })
    const effectiveMode = hit?.mode ?? defaultMode
    const verdict = hit ? effectiveMode : 'allow'

    if (hit || logAll) {
      writeJournal(journalPath, {
        ts: new Date().toISOString(),
        tool: exec.name,
        workdir: workdir || undefined,
        command: redact(command),
        rule: hit?.id ?? null,
        segment: hit?.segment ? redact(hit.segment).slice(0, 400) : undefined,
        severity: hit?.severity ?? null,
        verdict,
        mode: effectiveMode,
        test: false,
      })
    }

    if (!hit) return next()

    const reason = `Запрещённая команда по правилу «${hit.id}»${hit.reason ? `: ${hit.reason}` : ''}. Требуется явное одобрение владельца (AGENTS.md, Hard Gates).`
    const displayReason = {
      en: `Blocked by agent-command-guard: rule "${hit.id}". ${hit.reason ?? ''}`.trim(),
      ru: `Заблокировано правилом «${hit.id}»: ${hit.reason ?? ''}`.trim(),
    }

    if (effectiveMode === 'deny') return { kind: 'deny', reason }
    return { kind: 'ask', reason, displayReason }
  })
}
