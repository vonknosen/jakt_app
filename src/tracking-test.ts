import { TestTracking } from './native-tracking';
import { TrackingModel, readTrackingPages } from './tracking-model';
import type { TrackingSample, TrackingState } from './tracking-model';

const labels: Record<TrackingState['phase'], string> = {
  idle: 'Inga spår', recording: 'Pågår', stopped: 'Stoppat och sparat', interrupted: 'Avbrutet', error: 'Fel',
};
const emptyState: TrackingState = { sessionId: null, phase: 'idle', message: 'Inga sparade spår.',
  startedAt: 0, stoppedAt: null, startedElapsedMs: 0, stoppedElapsedMs: null, nowElapsedMs: 0, count: 0 };

/** UI reads durable snapshots; its timers never drive GPS or disk writes. */
export function setupTrackingTest(render: (points: TrackingSample[], state: TrackingState) => void) {
  const panel = document.createElement('section');
  panel.id = 'tracking-test';
  panel.innerHTML = `
    <div class="tracking-buttons">
      <button id="tracking-start" type="button" disabled>Starta nytt spår</button>
      <button id="tracking-stop" type="button" disabled>Stoppa och spara</button>
    </div>
    <label for="tracking-sessions">Sparade spår (tid · status · punkter)</label>
    <select id="tracking-sessions" aria-label="Sparade spår"></select>
    <button id="tracking-delete" type="button" disabled>Radera valt spår</button>
    <div id="tracking-message" role="status">Läser sparade spår...</div>
    <div id="tracking-error" role="alert"></div>
    <details><summary>GPS-teststatistik</summary><div id="tracking-stats"></div></details>
    <small>Sparas endast på telefonen. Nya spår bevarar tidigare spår. Ingen automatisk återstart efter processavslut.</small>`;
  const status = document.getElementById('status')!;
  status.append(panel); status.classList.add('with-tracking-test');
  const start = panel.querySelector<HTMLButtonElement>('#tracking-start')!;
  const stop = panel.querySelector<HTMLButtonElement>('#tracking-stop')!;
  const remove = panel.querySelector<HTMLButtonElement>('#tracking-delete')!;
  const select = panel.querySelector<HTMLSelectElement>('#tracking-sessions')!;
  const message = panel.querySelector<HTMLElement>('#tracking-message')!;
  const error = panel.querySelector<HTMLElement>('#tracking-error')!;
  const stats = panel.querySelector<HTMLElement>('#tracking-stats')!;
  const model = new TrackingModel();
  let disposed = false, busy = false, loaded = false, refreshPending = false;
  let generation = 0;
  let selected: string | null = null, activeId: string | null = null;
  let latest: TrackingState = emptyState;
  let drawn = '';
  let queue: Promise<void> = Promise.resolve();
  const seconds = (ms: number | null) => ms === null ? '--' : (ms / 1000).toFixed(1) + ' s';
  const time = (stamp: number) => new Date(stamp).toLocaleTimeString('sv-SE');
  function setDisabled(element: HTMLButtonElement | HTMLSelectElement, disabled: boolean) {
    if (element.disabled !== disabled) element.disabled = disabled;
  }
  function setText(element: HTMLElement, text: string) {
    if (element.textContent !== text) element.textContent = text;
  }
  function buttons() {
    setDisabled(start, busy || !loaded || activeId !== null);
    setDisabled(stop, busy || !loaded || activeId === null);
    setDisabled(remove, busy || !loaded || !selected || selected === activeId || latest.phase === 'recording');
    setDisabled(select, busy);
  }
  function showError(reason: unknown) { error.textContent = reason instanceof Error ? reason.message : String(reason); }
  function show() {
    const points = model.ordered();
    const signature = `${selected}/${model.sequence}/${latest.phase}`;
    if (signature !== drawn) { render(points, latest); drawn = signature; }
    setText(message, latest.message);
    const values = model.statistics(latest), first = points[0], last = points.at(-1);
    const lines = [
      `Session: ${selected ?? 'ingen'}`,
      `Återlästa sparade punkter: ${points.length} / ${latest.count}`,
      `Mottagna med släckt skärm: ${values.screenOff}`,
      `Mottagna med låst telefon: ${values.locked}`,
      `Största lucka mellan mätningar: ${points.length > 1 ? seconds(values.largestGapMs) : '--'}`,
      `Väntan på första mätning: ${seconds(values.initialWaitMs)}`,
      `Tid utan ny mätning vid slut/nu: ${seconds(values.tailGapMs)}`,
      `Mättider: ${first && last ? time(first.measuredAt) + '–' + time(last.measuredAt) : '--'}`,
    ];
    lines.forEach((text, index) => {
      let line = stats.children[index] as HTMLElement | undefined;
      if (!line) { line = document.createElement('div'); stats.append(line); }
      setText(line, text);
    });
  }
  async function refresh() {
    const token = generation;
    const result = await TestTracking.listSessions();
    if (disposed || token !== generation) return;
    activeId = result.activeSessionId;
    if (result.storageError) error.textContent = result.storageError;
    if (!result.sessions.some(s => s.sessionId === selected)) selected = activeId ?? result.sessions[0]?.sessionId ?? null;
    // Keep option nodes intact: replacing them makes Android's open picker flicker.
    const existing = new Map(Array.from(select.options, option => [option.value, option]));
    result.sessions.forEach((s, index) => {
      const id = s.sessionId!;
      const option = existing.get(id) ?? document.createElement('option');
      if (option.value !== id) option.value = id;
      setText(option, `${new Date(s.startedAt).toLocaleString('sv-SE')} · ${labels[s.phase]} · ${s.count}`);
      if (select.options[index] !== option) select.insertBefore(option, select.options[index] ?? null);
      existing.delete(id);
    });
    existing.forEach(option => option.remove());
    if (select.value !== (selected ?? '')) select.value = selected ?? '';
    const changedSession = model.sessionId !== selected;
    if (changedSession) model.reset(selected);
    if (selected) {
      if (!loaded || changedSession) setText(message, 'Läser sparade punkter...');
      const state = await readTrackingPages(TestTracking, model, selected, () => disposed || token !== generation);
      if (!state) return;
      latest = state;
    } else latest = emptyState;
    loaded = true; show();
  }
  function enqueue(action: () => Promise<void>) {
    queue = queue.then(async () => {
      if (disposed) return;
      try { await action(); } catch (reason) { showError(reason); }
      finally { if (!disposed) buttons(); }
    });
  }
  function runAction(action: () => Promise<void>) {
    if (busy) return;
    busy = true; generation++; error.textContent = ''; buttons();
    enqueue(async () => {
      try { await action(); }
      finally { busy = false; await refresh(); }
    });
  }
  start.addEventListener('click', () => runAction(async () => {
    const state = await TestTracking.startTracking(); selected = state.sessionId;
  }));
  stop.addEventListener('click', () => runAction(async () => {
    message.textContent = 'Stoppar och slutför sparningen...';
    const state = await TestTracking.stopTracking(); selected = state.sessionId;
  }));
  remove.addEventListener('click', () => {
    const id = selected;
    if (!id || !window.confirm('Radera det valda spåret och alla dess punkter från telefonen?')) return;
    runAction(async () => { await TestTracking.deleteSession({ sessionId: id }); selected = null; });
  });
  select.addEventListener('change', () => {
    selected = select.value || null; generation++;
    enqueue(refresh);
  });
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
    disposed = true; generation++; window.clearInterval(timer);
    document.removeEventListener('visibilitychange', requestRefresh); window.removeEventListener('focus', requestRefresh);
    panel.remove();
  };
}
