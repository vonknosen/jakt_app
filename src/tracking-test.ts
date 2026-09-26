import { TestTracking } from './native-tracking';
import { TrackingModel } from './tracking-model';
import type { TrackingSample, TrackingState } from './tracking-model';

/** Only refreshes the UI; Android owns GPS even when these timers are suspended. */
export function setupTrackingTest(render: (points: TrackingSample[], state: TrackingState) => void) {
  const panel = document.createElement('section');
  panel.id = 'tracking-test';
  panel.innerHTML = `
    <div class="tracking-buttons">
      <button id="tracking-start" type="button" disabled>Starta test</button>
      <button id="tracking-stop" type="button" disabled>Stoppa</button>
    </div>
    <div id="tracking-message" role="status">Läser teststatus...</div>
    <div id="tracking-error" role="alert"></div>
    <details open>
      <summary>GPS-teststatistik</summary>
      <div id="tracking-stats"></div>
    </details>
    <small>Endast minne. Ett nytt test ersätter föregående spår. Processavslut raderar testdata.</small>`;
  document.getElementById('status')!.append(panel);
  document.getElementById('status')!.classList.add('with-tracking-test');
  const start = panel.querySelector<HTMLButtonElement>('#tracking-start')!;
  const stop = panel.querySelector<HTMLButtonElement>('#tracking-stop')!;
  const message = panel.querySelector<HTMLElement>('#tracking-message')!;
  const error = panel.querySelector<HTMLElement>('#tracking-error')!;
  const stats = panel.querySelector<HTMLElement>('#tracking-stats')!;
  const model = new TrackingModel();
  let disposed = false;
  let busy = false;
  let loaded = false;
  let latest: TrackingState | undefined;
  // All bridge reads/actions are serialized, including visibility-change refreshes.
  let queue: Promise<void> = Promise.resolve();
  const active = () => latest && ['starting', 'running', 'stopping'].includes(latest.phase);
  function buttons() {
    start.disabled = busy || !loaded || !!active();
    stop.disabled = busy || !loaded || !active() || latest?.phase === 'stopping';
  }
  function showError(reason: unknown) {
    error.textContent = reason instanceof Error ? reason.message : String(reason);
  }
  const seconds = (ms: number) => (ms / 1000).toFixed(1) + ' s';
  async function refresh() {
    if (disposed) return;
    // At most 60 pages fill the 30,000-point buffer. Extra pages allow a concurrent new session.
    let complete = false;
    for (let pageNumber = 0; pageNumber < 65; pageNumber++) {
      const page = await TestTracking.readSamples({ sessionId: model.sessionId, afterSequence: model.sequence });
      if (disposed) return;
      model.accept(page);
      latest = page.state;
      if (!page.hasMore) { complete = true; break; }
    }
    if (!complete || !latest) throw new Error('Återläsningen blev inte klar. Öppna appen igen.');
    loaded = true;
    const points = model.ordered();
    render(points, latest);
    message.textContent = latest.message;
    const values = model.statistics(latest);
    const first = points[0];
    const last = points.at(-1);
    const time = (stamp: number) => new Date(stamp).toLocaleTimeString('sv-SE');
    stats.replaceChildren(...[
      `Session: ${latest.sessionId?.slice(0, 8) ?? 'ingen'}`,
      `Återlästa punkter: ${points.length} / ${latest.count} (max ${latest.capacity})`,
      `Mottagna med släckt skärm: ${values.screenOff}`,
      `Mottagna med låst telefon: ${values.locked}`,
      `Största lucka mellan mätningar: ${points.length > 1 ? seconds(values.largestGapMs) : '--'}`,
      `Väntan på första mätning: ${latest.sessionId ? seconds(values.initialWaitMs) : '--'}`,
      `Tid utan ny mätning vid slut/nu: ${values.tailGapMs === null ? '--' : seconds(values.tailGapMs)}`,
      `Mättider: ${first && last ? time(first.measuredAt) + '–' + time(last.measuredAt) : '--'}`,
      `Senast mottagen native: ${points.length ? time(points.reduce((a, b) => a.sequence > b.sequence ? a : b).receivedAt) : '--'}`,
    ].map(text => { const line = document.createElement('div'); line.textContent = text; return line; }));
  }
  function enqueue(action: () => Promise<void>) {
    queue = queue.then(async () => {
      if (disposed) return;
      try { await action(); } catch (reason) { showError(reason); }
      finally { if (!disposed) buttons(); }
    });
  }
  function runAction(action: () => Promise<TrackingState>) {
    if (busy) return;
    busy = true;
    error.textContent = '';
    buttons();
    enqueue(async () => {
      try { await action(); }
      finally { busy = false; await refresh(); }
    });
  }
  start.addEventListener('click', () => runAction(() => TestTracking.startTracking()));
  stop.addEventListener('click', () => runAction(() => TestTracking.stopTracking()));
  let refreshPending = false;
  function requestRefresh() {
    if (disposed || document.hidden || refreshPending || busy) return;
    refreshPending = true;
    enqueue(async () => { try { await refresh(); } finally { refreshPending = false; } });
  }
  document.addEventListener('visibilitychange', requestRefresh);
  window.addEventListener('focus', requestRefresh);
  const timer = window.setInterval(requestRefresh, 2000);
  requestRefresh();
  return () => {
    disposed = true;
    window.clearInterval(timer);
    document.removeEventListener('visibilitychange', requestRefresh);
    window.removeEventListener('focus', requestRefresh);
    panel.remove();
    // Do not stop the native session when the WebView/HMR is destroyed.
  };
}
