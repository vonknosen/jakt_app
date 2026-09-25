import { Capacitor } from '@capacitor/core';
import { Geolocation } from '@capacitor/geolocation';
import { GPS_OPTIONS } from './config';
import type { LocationSample } from './types';

// Samma gränssnitt för webbläsaren och vanlig positionering i mobilappen.
export function watchLocation(
  onPosition: (position: LocationSample) => void,
  onError: (message: string) => void,
  onUnsupported: () => void,
): () => void {
  if (Capacitor.isNativePlatform()) {
    return watchNativeLocation(onPosition, onError);
  }

  if (!('geolocation' in navigator)) {
    onUnsupported();
    return () => {};
  }

  const watchId = navigator.geolocation.watchPosition(
    ({ coords }) => onPosition({
      latitude: coords.latitude,
      longitude: coords.longitude,
      accuracy: coords.accuracy,
      speed: coords.speed,
    }),
    (error) => {
      console.error(error);
      const messages: Record<number, string> = {
        1: 'GPS-behörighet nekades',
        2: 'GPS-position saknas',
        3: 'GPS tog för lång tid',
      };
      onError(messages[error.code] ?? 'Okänt GPS-fel');
    },
    GPS_OPTIONS,
  );

  return () => navigator.geolocation.clearWatch(watchId);
}

function watchNativeLocation(
  onPosition: (position: LocationSample) => void,
  onError: (message: string) => void,
): () => void {
  let stopped = false;
  let watchId: string | undefined;

  function reportError(error: unknown) {
    console.error(error);
    if (stopped) return;
    const code = typeof error === 'object' && error !== null && 'code' in error
      ? String(error.code)
      : '';
    const messages: Record<string, string> = {
      'OS-PLUG-GLOC-0002': 'GPS-position saknas',
      'OS-PLUG-GLOC-0003': 'GPS-behörighet nekades',
      'OS-PLUG-GLOC-0007': 'Telefonens platstjänster är avstängda',
      'OS-PLUG-GLOC-0008': 'GPS-behörighet nekades',
      'OS-PLUG-GLOC-0009': 'Telefonens platstjänster aktiverades inte',
      'OS-PLUG-GLOC-0010': 'GPS tog för lång tid',
      'OS-PLUG-GLOC-0014': 'Kontrollera telefonens platstjänster',
      'OS-PLUG-GLOC-0015': 'Telefonens platstjänster är inte tillgängliga',
      'OS-PLUG-GLOC-0016': 'Kontrollera telefonens platsinställningar',
      'OS-PLUG-GLOC-0017': 'Telefonens platstjänster är avstängda',
    };
    onError(messages[code] ?? 'Okänt GPS-fel');
  }

  function clearWatch(id: string) {
    void Geolocation.clearWatch({ id }).catch((error: unknown) => console.error(error));
  }

  async function start() {
    const permission = await Geolocation.requestPermissions({ permissions: ['location'] });
    if (stopped) return;
    // Ungefärlig position ska också fungera om användaren väljer det.
    if (permission.location !== 'granted' && permission.coarseLocation !== 'granted') {
      onError('GPS-behörighet nekades');
      return;
    }

    const options = {
      ...GPS_OPTIONS,
      // Preliminära Android-intervall; utvärdera noggrannhet, spårkvalitet och batteri i fält.
      ...(Capacitor.getPlatform() === 'android'
        ? { interval: 2000, minimumUpdateInterval: 1000 }
        : {}),
      enableHighAccuracy: GPS_OPTIONS.enableHighAccuracy && permission.location === 'granted',
    };
    const id = await Geolocation.watchPosition(options, (position, error) => {
      if (stopped) return;
      if (error) {
        reportError(error);
      } else if (position) {
        const { latitude, longitude, accuracy, speed } = position.coords;
        onPosition({ latitude, longitude, accuracy, speed });
      }
    });
    // Avslut kan begäras medan det asynkrona anropet fortfarande pågår.
    if (stopped) clearWatch(id);
    else watchId = id;
  }

  void start().catch(reportError);
  return () => {
    if (stopped) return;
    stopped = true;
    if (watchId !== undefined) clearWatch(watchId);
  };
}
