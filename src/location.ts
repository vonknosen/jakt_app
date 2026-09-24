import { GPS_OPTIONS } from './config';
import type { LocationSample } from './types';

// Webbläsarpositionering tills plattformarnas GPS införs i nästa etapp.
export function watchLocation(
  onPosition: (position: LocationSample) => void,
  onError: (message: string) => void,
  onUnsupported: () => void,
): () => void {
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
