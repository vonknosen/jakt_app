import test from 'node:test';
import assert from 'node:assert/strict';
import { TrackingModel } from '../src/tracking-model.ts';

const state = (sessionId = 'a') => ({ sessionId, phase: 'running', message: '', startedAt: 100,
  stoppedAt: 0, startedElapsedMs: 100, stoppedElapsedMs: 0, nowElapsedMs: 9000, count: 3, capacity: 30000 });
const sample = (sequence, measuredElapsedMs, interactive = false, sessionId = 'a') => ({
  sessionId, sequence, latitude: 62, longitude: 17, accuracy: 5, speed: null,
  measuredAt: measuredElapsedMs + 100000, measuredElapsedMs, receivedAt: 110000,
  receivedElapsedMs: 10000, screenInteractive: interactive, deviceLocked: !interactive,
});
const page = (samples, nextSequence, sessionId = 'a') => ({ state: state(sessionId), samples,
  nextSequence, hasMore: false });

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
