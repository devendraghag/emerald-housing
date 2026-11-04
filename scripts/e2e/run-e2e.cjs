const { setTimeout: delay } = require('node:timers/promises');

const checks = [
  ['Society Service', 'http://society-service:8080/actuator/health/readiness'],
  ['Member Service', 'http://member-service:8080/actuator/health/readiness'],
  ['API Gateway', 'http://api-gateway:8080/actuator/health/readiness'],
];

async function waitFor(url) {
  const deadline = Date.now() + 5 * 60 * 1000;
  let lastError = 'not ready';
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(2500) });
      if (response.ok) return;
      lastError = `HTTP ${response.status}`;
    } catch (error) {
      lastError = error.name === 'TimeoutError' ? 'request timed out' : error.message;
    }
    await delay(2000);
  }
  throw new Error(`${url} did not become ready (${lastError})`);
}

(async () => {
  for (const [name, url] of checks) {
    process.stdout.write(`Waiting for ${name} readiness...\n`);
    await waitFor(url);
    process.stdout.write(`${name} is ready.\n`);
  }
  require('../run-postman-option-b.cjs');
})().catch((error) => {
  process.stderr.write(`E2E startup failed: ${error.message}\n`);
  process.exitCode = 1;
});
