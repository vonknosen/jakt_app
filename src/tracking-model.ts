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
  phase: 'idle' | 'recording' | 'stopped' | 'interrupted' | 'error';
  message: string;
  startedAt: number;
  stoppedAt: number | null;
  startedElapsedMs: number;
  stoppedElapsedMs: number | null;
  nowElapsedMs: number;
  count: number;

}

export interface TrackingPage {
  state: TrackingState;
  samples: TrackingSample[];
  nextSequence: number;
  hasMore: boolean;
  throughSequence: number;
}

export interface TrackingProvider {
  startTracking(): Promise<TrackingState>;
  stopTracking(): Promise<TrackingState>;
  getTrackingState(): Promise<TrackingState>;
  listSessions(): Promise<{ sessions: TrackingState[]; activeSessionId: string | null; storageError: string | null }>;
  deleteSession(options: { sessionId: string }): Promise<void>;
  readSamples(options: { sessionId: string | null; afterSequence: number; throughSequence?: number }): Promise<TrackingPage>;
}

// Transport order uses sequence; map/statistics use monotonic measurement time.
export class TrackingModel {
  sessionId: string | null = null;
  sequence = 0;
  private points: TrackingSample[] = [];

  reset(sessionId: string | null = null) {
    this.sessionId = sessionId; this.sequence = 0; this.points = []; this.sorted = [];
  }
  private sorted: TrackingSample[] = [];

  accept(page: TrackingPage) {
    if (page.state.sessionId !== this.sessionId) {
      this.sessionId = page.state.sessionId;
      this.sequence = 0;
      this.points = []; this.sorted = [];
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
    if (this.sorted.length !== this.points.length) {
      this.sorted = [...this.points].sort((a, b) => a.measuredElapsedMs - b.measuredElapsedMs || a.sequence - b.sequence);
    }
    return this.sorted;
  }

  statistics(state: TrackingState) {
    const points = this.ordered();
    let largestGapMs = 0;
    for (let i = 1; i < points.length; i++) {
      largestGapMs = Math.max(largestGapMs, points[i].measuredElapsedMs - points[i - 1].measuredElapsedMs);
    }
    const first = points[0];
    const last = points.at(-1);
    const end = state.phase === 'recording' ? state.nowElapsedMs : state.stoppedElapsedMs;
    return {
      screenOff: points.filter(p => !p.screenInteractive).length,
      locked: points.filter(p => p.deviceLocked).length,
      largestGapMs,
      initialWaitMs: first ? Math.max(0, first.measuredElapsedMs - state.startedElapsedMs) : null,
      tailGapMs: last && end !== null ? Math.max(0, end - last.measuredElapsedMs) : null,
    };
  }
}

/** Read a finite snapshot, even while new GPS points arrive. No total track-size/page cap. */
export async function readTrackingPages(
  provider: Pick<TrackingProvider, 'readSamples'>, model: TrackingModel, sessionId: string,
  cancelled: () => boolean = () => false,
): Promise<TrackingState | null> {
  let throughSequence: number | undefined;
  while (!cancelled()) {
    const before = model.sequence;
    const page = await provider.readSamples({ sessionId, afterSequence: before, throughSequence });
    if (cancelled()) return null;
    if (page.state.sessionId !== sessionId) throw new Error('Fel spår i återläsningen.');
    model.accept(page);
    throughSequence ??= page.throughSequence;
    if (page.throughSequence !== throughSequence) throw new Error('Läsgränsen ändrades under återläsningen.');
    if (!page.hasMore) return page.state;
    if (model.sequence <= before) throw new Error('Återläsningen gjorde inga framsteg.');
    // Let selection changes and browser rendering run between pages.
    await new Promise(resolve => setTimeout(resolve, 0));
  }
  return null;
}
