import test from 'node:test';
import assert from 'node:assert/strict';
import { TrackingModel, readTrackingPages } from '../src/tracking-model.ts';

const state = (sessionId = 'a') => ({ sessionId, phase: 'recording', message: '', startedAt: 100,
  stoppedAt: null, startedElapsedMs: 100, stoppedElapsedMs: null, nowElapsedMs: 9000, count: 3 });
const sample = (sequence, measuredElapsedMs, interactive = false, sessionId = 'a') => ({
  sessionId, sequence, latitude: 62, longitude: 17, accuracy: 5, speed: null,
  measuredAt: measuredElapsedMs + 100000, measuredElapsedMs, receivedAt: 110000,
  receivedElapsedMs: 10000, screenInteractive: interactive, deviceLocked: !interactive,
});
const page = (samples, nextSequence, sessionId = 'a') => ({ state: state(sessionId), samples,
  nextSequence, throughSequence: nextSequence, hasMore: false });

test('non-destructive replay does not duplicate; measurement time sorts batched data', () => {
  const model = new TrackingModel();
  const data = page([sample(1, 1000, true), sample(2, 7000), sample(3, 3000)], 3);
  model.accept(data); model.accept(data);
  assert.deepEqual(model.ordered().map(p => p.sequence), [1, 3, 2]);
  assert.deepEqual(model.statistics(state()), { screenOff: 2, locked: 2, largestGapMs: 4000,
    initialWaitMs: 900, tailGapMs: 2000 });
});

test('new session clears old points; a missing sequence is reported', () => {
  const model = new TrackingModel();
  model.accept(page([sample(1, 1000)], 1));
  model.accept(page([sample(1, 2000, false, 'b')], 1, 'b'));
  assert.equal(model.ordered().length, 1);
  assert.equal(model.sessionId, 'b');
  assert.throws(() => model.accept(page([sample(3, 3000, false, 'b')], 3, 'b')), /Lucka/);
});

test('process reset and stopped tail gap cannot masquerade as ongoing tracking', () => {
  const model = new TrackingModel();
  model.accept(page([sample(1, 1000)], 1));
  assert.equal(model.statistics({ ...state(), phase: 'stopped', stoppedElapsedMs: 5000 }).tailGapMs, 4000);
  model.accept(page([], 0, null));
  assert.equal(model.ordered().length, 0);
  assert.equal(model.sessionId, null);
});

// Reopening after a device reboot must never compare the old track with the new boot clock.
test('interrupted session has unknown end, not a gap to the current boot clock', () => {
  const model = new TrackingModel(); model.accept(page([sample(1, 1000)], 1));
  assert.equal(model.statistics({ ...state(), phase: 'interrupted', nowElapsedMs: 5 }).tailGapMs, null);
});

test('finite snapshot reads more than 65 pages and 30000 points without duplicates', async () => {
  const model = new TrackingModel(); const total = 33501; let calls = 0;
  const provider = { async readSamples({ sessionId, afterSequence, throughSequence }) {
    assert.equal(sessionId, 'a'); if (calls++) assert.equal(throughSequence, total);
    const end = Math.min(afterSequence + 500, total);
    return { state: { ...state(), count: total + 100 },
      samples: Array.from({ length: end - afterSequence }, (_, i) => sample(afterSequence + i + 1, afterSequence + i + 1000)),
      nextSequence: end, throughSequence: total, hasMore: end < total };
  } };
  await readTrackingPages(provider, model, 'a');
  assert.equal(calls, 68); assert.equal(model.sequence, total); assert.equal(model.ordered().length, total);
});

test('switching track cancels a pending read before old points can enter the new model', async () => {
  const model = new TrackingModel(); let cancelled = false;
  const provider = { async readSamples() { cancelled = true; return page([sample(1, 1000)], 1); } };
  assert.equal(await readTrackingPages(provider, model, 'a', () => cancelled), null);
  assert.equal(model.sequence, 0);
});

test('non-progressing page fails instead of looping forever', async () => {
  const model = new TrackingModel();
  await assert.rejects(readTrackingPages({ async readSamples() {
    return { ...page([], 0), throughSequence: 5, hasMore: true };
  } }, model, 'a'), /framsteg/);
});
