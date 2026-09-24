import 'leaflet/dist/leaflet.css';
import './styles.css';
import { createMap } from './map';
import { watchLocation } from './location';
import { createTrack } from './track';
import { createStatus } from './status';
import type { LocationSample } from './types';

const map = createMap();
const track = createTrack();
const status = createStatus();
let currentPosition: LocationSample | null = null;

const stopWatching = watchLocation(
  (position) => {
    currentPosition = position;
    const points = track.add(position);
    map.update(position, points);
    status.update(position, points.length);
  },
  status.error,
  status.unsupported,
);

const centerButton = document.getElementById('centerButton');
if (!centerButton) throw new Error('Centreringsknappen saknas');

function centerOnPosition() {
  if (currentPosition !== null) map.center(currentPosition);
}
centerButton.addEventListener('click', centerOnPosition);

// Undvik dubbla bevakningar och kartor vid Vites utvecklingsomladdningar.
if (import.meta.hot) {
  import.meta.hot.dispose(() => {
    stopWatching();
    centerButton.removeEventListener('click', centerOnPosition);
    map.destroy();
  });
}
