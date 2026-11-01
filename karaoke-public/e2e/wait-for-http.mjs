#!/usr/bin/env node
/**
 * Ждёт готовности HTTP-сервера (e2e-контейнера karaoke-public) до таймаута.
 *
 * Зачем (KARAOKE-380): в CI ожидание готовности nginx было заменено на
 * `sleep 1`. nginx к этому моменту ещё не успевал подняться, Playwright стартовал
 * «в пустоту» — и прогон падал не по существу. Кроме того, адрес собирался через
 * `ip route` (`ip: command not found` в CI-образе → exit 127), т.е. падал ещё и по
 * отсутствующей утилите.
 *
 * Использование:
 *   node e2e/wait-for-http.mjs <url> [timeoutSec]
 *
 * Коды возврата:
 *   0 — сервер ответил (любой код < 500: значит, nginx уже обслуживает запрос);
 *   1 — за отведённое время готов не стал;
 *   2 — неверный вызов (не задан URL).
 */

const url = process.argv[2];
const timeoutSec = Number(process.argv[3] ?? 60);

if (!url) {
  console.error('usage: wait-for-http.mjs <url> [timeoutSec]');
  process.exit(2);
}

const deadline = Date.now() + timeoutSec * 1000;
let lastError = 'нет ответа';
let attempt = 0;

while (Date.now() < deadline) {
  attempt += 1;
  try {
    const response = await fetch(url, { redirect: 'follow' });
    // 4xx — это тоже ответ сервера: контейнер жив и обслуживает запрос.
    // 5xx — nginx ещё перезагружается, пробуем дальше.
    if (response.status < 500) {
      console.log(`[wait-for-http] готов за ${attempt} попыток(и): ${url} → HTTP ${response.status}`);
      process.exit(0);
    }
    lastError = `HTTP ${response.status}`;
  } catch (error) {
    lastError = error instanceof Error ? error.message : String(error);
  }
  await new Promise((resolve) => setTimeout(resolve, 1000));
}

console.error(`[wait-for-http] НЕ ГОТОВ за ${timeoutSec}s: ${url} (последняя ошибка: ${lastError})`);
process.exit(1);
