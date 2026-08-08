import test from 'node:test';
import assert from 'node:assert/strict';

import makeWASocket, {
  Browsers,
  DisconnectReason,
  downloadMediaMessage,
  fetchLatestWaWebVersion,
  makeCacheableSignalKeyStore,
  useMultiFileAuthState,
} from 'ourin';

test('Ourin Baileys exposes every API used by Hallo Johor', () => {
  const expectedFunctions = {
    makeWASocket,
    downloadMediaMessage,
    fetchLatestWaWebVersion,
    makeCacheableSignalKeyStore,
    useMultiFileAuthState,
  };

  for (const [name, value] of Object.entries(expectedFunctions)) {
    assert.equal(typeof value, 'function', `${name} must be exported as a function`);
  }

  assert.equal(typeof Browsers, 'object');
  assert.equal(typeof DisconnectReason, 'object');
});
