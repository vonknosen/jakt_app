import type { Coordinates } from './types';

export const USER_NAME = 'Mattias';
export const INITIAL_CENTER: Coordinates = [62.39, 17.31];
export const INITIAL_ZOOM = 10;
export const POSITION_ZOOM = 17;
export const TILE_URL = 'https://tile.openstreetmap.de/{z}/{x}/{y}.png';
export const GPS_OPTIONS: PositionOptions = {
  enableHighAccuracy: true,
  maximumAge: 2000,
  timeout: 15000,
};
