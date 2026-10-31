#!/usr/bin/env node
/**
 * test-guard.mjs — офлайн-проверка матчера agent-command-guard на реальных
 * командах агента.
 *
 * Вход: commands.jsonl — реальные вызовы bash из журналов сессий DSH
 *        (собирается скриптом extract-commands.sh).
 * Выход: статистика и образцы срабатываний, обязательные срабатывания,
 *        ложные срабатывания, то, что по замыслу не ловится, обходные
 *        варианты и проверка маскирования секретов.
 *
 * Использование:
 *   bash tools/dsh-agent-command-guard/extract-commands.sh /tmp/commands.jsonl 200
 *   node tools/dsh-agent-command-guard/test-guard.mjs \
 *        tools/dsh-agent-command-guard/policy/deny-commands.json /tmp/commands.jsonl
 */
import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { matchCommand, loadPolicy, redact, resolveGitBranch } from './index.js'

const HERE = dirname(fileURLToPath(import.meta.url))
const POLICY = process.argv[2] ?? join(HERE, 'policy', 'deny-commands.json')
const COMMANDS = process.argv[3] ?? '/tmp/commands.jsonl'
const WORKDIR = process.env.DSH_GUARD_TEST_WORKDIR ?? process.cwd()

const rules = loadPolicy(POLICY)
const policyVersion = JSON.parse(readFileSync(POLICY, 'utf8')).version
console.log(`Правил загружено: ${rules.length}   версия политики: ${policyVersion}`)
console.log(`Ветка рабочего каталога ${WORKDIR}: ${resolveGitBranch(WORKDIR)}`)

const lines = readFileSync(COMMANDS, 'utf8').split('\n').filter(Boolean)
const perRule = new Map()
let matched = 0
for (const line of lines) {
  let cmd
  try { cmd = JSON.parse(line) } catch { continue }
  const hit = matchCommand(cmd.command, rules, { workdir: WORKDIR })
  if (hit) {
    matched++
    const bucket = perRule.get(hit.id) ?? []
    bucket.push(cmd.command.replace(/\n/g, ' ⏎ ').slice(0, 160))
    perRule.set(hit.id, bucket)
  }
}
console.log(`\nКоманд разобрано: ${lines.length}; совпало правил: ${matched} (${((matched / Math.max(lines.length, 1)) * 100).toFixed(3)}%)`)
for (const [id, list] of [...perRule.entries()].sort((a, b) => b[1].length - a[1].length)) {
  console.log(`\n=== ${id}: ${list.length} ===`)
  list.slice(0, 5).forEach(command => console.log('   ', command))
}

// Состояниезависимое правило проверяется на временных репозиториях: ветка
// берётся из .git/HEAD рабочего каталога, поэтому результат не должен зависеть
// от того, на какой ветке лежит сам репозиторий проекта.
function tempRepo(branch) {
  const dir = mkdtempSync(join(tmpdir(), 'agent-guard-test-'))
  execFileSync('git', ['init', '-q', '-b', branch, dir])
  return dir
}

console.log('\n— Обязательные срабатывания —')
const MUST_CATCH = [
  ['обход deploy/do.sh (compose)', "docker compose -f docker-compose-app.yml up -d --force-recreate karaoke-app"],
  ['force-push в master', 'git push --force github origin/master:master'],
  ['--no-verify', 'git commit -m "fix" --no-verify'],
  ['снятие branch protection', 'gh api -X DELETE repos/svoemesto/Karaoke/branches/master/protection'],
  ['прямой restart контейнера', 'docker restart karaoke-app'],
  ['docker stop с опциями', 'docker stop -t 30 karaoke-app'],
  ['git push в master (refspec)', 'git push origin master'],
]
let mustOk = 0
for (const [name, cmd] of MUST_CATCH) {
  const hit = matchCommand(cmd, rules, { workdir: WORKDIR })
  if (hit) mustOk++
  console.log(`${hit ? 'ПОЙМАН ' : 'ПРОПУЩЕН'} | ${name} → ${hit?.id ?? '—'}`)
}

let mustNotOk = 0
{
  const masterDir = tempRepo('master')
  const featureDir = tempRepo('feature/example')
  const inMaster = matchCommand('git commit -m "правка"', rules, { workdir: masterDir })
  const inFeature = matchCommand('git commit -m "правка"', rules, { workdir: featureDir })
  if (inMaster) mustOk++
  console.log(`${inMaster ? 'ПОЙМАН ' : 'ПРОПУЩЕН'} | git commit В МАСТЕРЕ (состояние) → ${inMaster?.id ?? '—'}`)
  const clean = inFeature === null
  if (clean) mustNotOk++
  console.log(`${clean ? 'ЧИСТО  ' : 'ЛОЖНОЕ '} | git commit в ветке фичи (состояние) → ${inFeature?.id ?? '—'}`)
}

console.log('\n— Ложные срабатывания, которых быть НЕ должно —')
const MUST_NOT = [
  ['git push фичи + gh pr create --base master', 'git push -u origin 608-living-docs && gh pr create --base master --title "docs"'],
  ['git log по master', 'git log --oneline -1 origin/master'],
  ['git checkout master (не коммит)', 'git checkout master -q && git pull github master -q'],
  ['gh api чтение protection', 'gh api repos/svoemesto/Karaoke/branches/master/protection'],
  ['docker compose в чужом проекте', 'cd /tmp/other-project && docker compose up -d'],
  ['git commit в worktree фичи', 'cd /tmp/other-worktree && git commit -q -m "fix"'],
  ['grep по тексту правила', 'grep -nE "docker (restart|stop|start|compose)" tools/check-container-restart.sh'],
]
for (const [name, cmd] of MUST_NOT) {
  const hit = matchCommand(cmd, rules, { workdir: WORKDIR })
  const ok = hit === null
  if (ok) mustNotOk++
  console.log(`${ok ? 'ЧИСТО  ' : 'ЛОЖНОЕ '} | ${name} → ${hit?.id ?? '—'}`)
}

console.log('\n— По замыслу НЕ определяется по тексту (состояние, а не текст) —')
for (const [name, cmd] of [
  ['deploy/do.sh без согласия владельца', 'cd /home/nsa/Karaoke/deploy && bash do.sh stop_app && bash do.sh start_app'],
  ['do.sh build_app без согласия', 'cd /home/nsa/Karaoke/deploy && ./do.sh build_app'],
]) {
  const hit = matchCommand(cmd, rules, { workdir: WORKDIR })
  console.log(`${hit ? 'ПОЙМАН ' : 'НЕ ВИДНО'} | ${name}`)
}

console.log('\n— Обходные варианты (текстовое правило обходимо) —')
for (const [name, cmd] of [
  ["d''ocker", "d''ocker restart karaoke-app"],
  ['$(echo docker)', '$(echo docker) restart karaoke-app'],
  ['скрипт-файл', 'bash /tmp/restart-karaoke.sh'],
  ['podman', 'podman restart karaoke-app'],
  ['systemctl', 'systemctl restart docker'],
  ['kill pid', 'kill $(cat /tmp/pid)'],
  ['docker exec', 'docker exec karaoke-app kill -HUP 1'],
  ['env-обёртка', 'env DOCKER_HOST=unix:///var/run/docker.sock docker restart karaoke-app'],
]) {
  const hit = matchCommand(cmd, rules, { workdir: WORKDIR })
  console.log(`${hit ? 'ПОЙМАН ' : 'ПРОПУЩЕН'} | ${name}`)
}

console.log('\n— Маскирование —')
console.log(redact('PGPASSWORD=hunter2 mysql -u admin:s3cr3t --password=abc123'))

console.log(`\nИтог: обязательных срабатываний ${mustOk}/${MUST_CATCH.length}; чистых из ложных ${mustNotOk}/${MUST_NOT.length}; совпадений на реальных командах ${matched} из ${lines.length}`)
