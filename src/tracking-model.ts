import type { LocationSample } from './types';

export interface TrackingSample extends LocationSample {
  sessionId: string;
  sequence: number;
  measuredAt: number;
  measuredElapsedMs: number;
  receivedAt: number;
  receivedElapsedMs: number;
  screenInteractive: boolean;
  deviceLocked: boolean;
}

export interface TrackingState {
  sessionId: string | null;
  phase: 'idle' | 'starting' | 'running' | 'stopping' | 'stopped';
  message: string;
  startedAt: number;
  stoppedAt: number;
  startedElapsedMs: number;
  stoppedElapsedMs: number;
  nowElapsedMs: number;
  count: number;
  capacity: number;
}

export interface TrackingPage {
  state: TrackingState;
  samples: TrackingSample[];
  nextSequence: number;
  hasMore: boolean;
}

export interface TrackingProvider {
  startTracking(): Promise<TrackingState>;
  stopTracking(): Promise<TrackingState>;
  getTrackingState(): Promise<TrackingState>;
  readSamples(options: { sessionId: string | null; afterSequence: number }): Promise<TrackingPage>;
}

// Transport order uses sequence; map/statistics use monotonic measurement time.
export class TrackingModel {
  sessionId: string | null = null;
  sequence = 0;
  private points: TrackingSample[] = [];

  accept(page: TrackingPage) {
    if (page.state.sessionId !== this.sessionId) {
      this.sessionId = page.state.sessionId;
      this.sequence = 0;
      this.points = [];
    }
    for (const sample of page.samples) {
      if (sample.sessionId !== this.sessionId) throw new Error('Fel testsession i positionsdata.');
      if (sample.sequence <= this.sequence) continue;
      if (sample.sequence !== this.sequence + 1) throw new Error('Lucka i återlästa löpnummer.');
      this.points.push(sample);
      this.sequence = sample.sequence;
    }
    if (page.nextSequence !== this.sequence) throw new Error('Ofullständig återläsning av testspåret.');
  }

  ordered(): TrackingSample[] {
    return [...this.points].sort((a, b) => a.measuredElapsedMs - b.measuredElapsedMs || a.sequence - b.sequence);
  }

  statistics(state: TrackingState) {
    const points = this.ordered();
    let largestGapMs = 0;
    for (let i = 1; i < points.length; i++) {
      largestGapMs = Math.max(largestGapMs, points[i].measuredElapsedMs - points[i - 1].measuredElapsedMs);
    }
    const first = points[0];
    const last = points.at(-1);
    const end = state.stoppedElapsedMs || state.nowElapsedMs;
    return {
      screenOff: points.filter(p => !p.screenInteractive).length,
      locked: points.filter(p => p.deviceLocked).length,
      largestGapMs,
      initialWaitMs: Math.max(0, (first?.measuredElapsedMs ?? end) - state.startedElapsedMs),
      tailGapMs: last ? Math.max(0, end - last.measuredElapsedMs) : null,
    };
  }
}
