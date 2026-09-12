import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { chunkEntries } from '../src/components/manual-entry-utils.ts';

const componentSource = readFileSync(
  new URL('../src/components/AddWordListModal.tsx', import.meta.url),
  'utf8',
);

test('large JSON imports are split into API-sized batches', () => {
  const entries = Array.from({ length: 3537 }, (_, index) => ({ word: `word-${index}` }));
  const batches = chunkEntries(entries);

  assert.deepEqual(batches.map((batch) => batch.length), [1000, 1000, 1000, 537]);
  assert.deepEqual(batches.flat(), entries);
  assert.match(componentSource, /chunkEntries\(entries\)/);
});
