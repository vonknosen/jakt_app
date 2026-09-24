export type Coordinates = [latitude: number, longitude: number];

export interface LocationSample {
  latitude: number;
  longitude: number;
  accuracy: number;
  speed: number | null;
}
