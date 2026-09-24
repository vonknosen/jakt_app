import type { LocationSample } from './types';

function requireElement(id: string): HTMLElement {
  const element = document.getElementById(id);
  if (!element) throw new Error(`Elementet #${id} saknas`);
  return element;
}

export function createStatus() {
  const title = requireElement('status-title');
  const accuracy = requireElement('accuracy');
  const speed = requireElement('speed');
  const lastUpdate = requireElement('lastUpdate');
  const pointCount = requireElement('pointCount');

  return {
    update(position: LocationSample, count: number) {
      title.textContent = '📍 GPS aktiv';
      accuracy.textContent = `Noggrannhet: ±${Math.round(position.accuracy)} m`;
      speed.textContent = position.speed !== null && position.speed >= 0
        ? `Hastighet: ${(position.speed * 3.6).toFixed(1)} km/h`
        : 'Hastighet: --';
      // Behåll prototypens visning av mottagningstid.
      lastUpdate.textContent = 'Senast: ' + new Date().toLocaleTimeString('sv-SE');
      pointCount.textContent = 'GPS-punkter: ' + count;
    },
    error(message: string) {
      title.textContent = '⚠️ GPS-fel';
      accuracy.textContent = message;
    },
    unsupported() {
      title.textContent = 'GPS stöds inte';
    },
  };
}
