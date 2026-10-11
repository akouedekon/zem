import test from 'node:test';
import assert from 'node:assert/strict';
import { allowedPath, sameOrigin } from '../lib/bff-policy.mjs';
test('BFF only forwards known API paths and methods', () => {
  assert.equal(allowedPath('driver/dossier', 'PUT'), true);
  assert.equal(allowedPath('auth/login', 'POST'), true);
  for (const p of ['../auth/login', 'https://evil.test', 'auth/login/extra', 'driver/documents/OTHER']) assert.equal(allowedPath(p, 'POST'), false);
  assert.equal(allowedPath('auth/login', 'GET'), false);
});
test('mutations require the explicitly configured origin', () => {
  assert.equal(sameOrigin('https://zem.test', 'https://zem.test'), true);
  for (const o of [null, 'null', 'http://zem.test', 'https://zem.test.evil.test', 'https://zem.test:444']) assert.equal(sameOrigin(o, 'https://zem.test'), false);
});
