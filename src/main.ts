import 'leaflet/dist/leaflet.css';
import './styles.css';
import { Capacitor } from '@capacitor/core';
import { createMap } from './map';
import { watchLocation } from './location';
import { createTrack } from './track';
import { createStatus } from './status';
import { setupTrackingTest } from './tracking-test';
import type { LocationSample } from './types';

const map = createMap();
const track = createTrack();
const status = createStatus();
let currentPosition: LocationSample | null = null;
let stopWatching: () => void;

if (Capacitor.getPlatform() === 'android') {
  // Android PoC owns the only location request. Never start a second Geolocation watch,
  // including when a WebView is recreated during an existing native session.
  status.tracking('Ingen testspårning startad');
  let shownSession: string | null = null;
  stopWatching = setupTrackingTest((points, state) => {
    const changedSession = shownSession !== state.sessionId;
    if (changedSession) {
      map.clear(); currentPosition = null; shownSession = state.sessionId;
      status.reset();
    }
    const last = points.at(-1);
    if (last) {
      currentPosition = last;
      map.update(last, points.map(p => [p.latitude, p.longitude]));
      status.update(last, points.length, last.measuredAt);
      if (changedSession && state.phase !== 'recording') map.fitTrack();
    }
    const titles = { idle: 'Inga sparade spår', recording: 'GPS-spårning pågår', stopped: 'Stoppat och sparat', interrupted: 'Avbrutet spår', error: 'Spårning med fel' };
    status.tracking(titles[state.phase]);
  });
} else {
  stopWatching = watchLocation(
    (position) => {
      currentPosition = position;
      const points = track.add(position);
      map.update(position, points);
      status.update(position, points.length);
    }, status.error, status.unsupported,
  );
}

const centerButton = document.getElementById('centerButton');
if (!centerButton) throw new Error('Centreringsknappen saknas');
function centerOnPosition() {
  if (currentPosition !== null) map.center(currentPosition);
}
centerButton.addEventListener('click', centerOnPosition);

if (import.meta.hot) {
  import.meta.hot.dispose(() => {
    stopWatching();
    centerButton.removeEventListener('click', centerOnPosition);
    map.destroy();
  });
}
