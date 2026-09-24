import type { Coordinates, LocationSample } from './types';

// Tillfälligt minne, precis som i prototypen. Ingen beständig lagring ännu.
export function createTrack() {
  const points: Coordinates[] = [];
  return {
    add(position: LocationSample): Coordinates[] {
      points.push([position.latitude, position.longitude]);
      return points;
    },
  };
}
