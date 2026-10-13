import test from 'node:test';
import assert from 'node:assert/strict';
import { allowedPath, sameOrigin, readLimitedBody } from '../lib/bff-policy.mjs';
test('BFF only forwards known API paths and methods', () => {
  assert.equal(allowedPath('driver/dossier', 'PUT'), true);
  assert.equal(allowedPath('auth/login', 'POST'), true);
  for (const p of ['../auth/login', 'https://evil.test', 'auth/login/extra', 'driver/documents/OTHER']) assert.equal(allowedPath(p, 'POST'), false);
  assert.equal(allowedPath('auth/login', 'GET'), false);
  const id = '00000000-0000-0000-0000-000000000001';
  assert.equal(allowedPath('auth/register-passenger', 'POST'), true);
  assert.equal(allowedPath(`driver/rides/${id}/accept`, 'POST'), true);
  assert.equal(allowedPath(`driver/rides/${id}/delete`, 'POST'), false);
  assert.equal(allowedPath('driver/declarations', 'PUT'), true);
});
test('chunked uploads stop at the byte limit even without Content-Length', async () => {
  let cancelled = false;
  const stream = new ReadableStream({
    start(controller) { controller.enqueue(new Uint8Array(3)); controller.enqueue(new Uint8Array(3)); },
    cancel() { cancelled = true; }
  });
  await assert.rejects(readLimitedBody(stream, 5), RangeError);
  assert.equal(cancelled, true);
  const valid = new ReadableStream({ start(c) { c.enqueue(new Uint8Array([1, 2, 3])); c.close(); } });
  assert.deepEqual(new Uint8Array(await readLimitedBody(valid, 5)), new Uint8Array([1, 2, 3]));
});
test('mutations require the explicitly configured origin', () => {
  assert.equal(sameOrigin('https://zem.test', 'https://zem.test'), true);
  for (const o of [null, 'null', 'http://zem.test', 'https://zem.test.evil.test', 'https://zem.test:444']) assert.equal(sameOrigin(o, 'https://zem.test'), false);
});
