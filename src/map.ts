import * as L from 'leaflet';
import { INITIAL_CENTER, INITIAL_ZOOM, POSITION_ZOOM, TILE_URL, USER_NAME } from './config';
import type { Coordinates, LocationSample } from './types';

export function createMap() {
  const map = L.map('map').setView(INITIAL_CENTER, INITIAL_ZOOM);
  L.tileLayer(TILE_URL, {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors',
  }).addTo(map);

  const hunterIcon = L.divIcon({
    className: '',
    html: '<div class="hunter-marker"></div>',
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  });

  let marker: L.Marker | null = null;
  let accuracyCircle: L.Circle | null = null;
  let trackLine: L.Polyline | null = null;
  let firstPosition = true;

  return {
    update(position: LocationSample, points: Coordinates[]) {
      const center: Coordinates = [position.latitude, position.longitude];
      if (trackLine === null) {
        trackLine = L.polyline(points, { weight: 4, opacity: 0.8 }).addTo(map);
      } else {
        trackLine.setLatLngs(points);
      }

      if (marker === null) {
        marker = L.marker(center, { icon: hunterIcon }).addTo(map);
        marker.bindTooltip(USER_NAME, {
          permanent: true,
          direction: 'bottom',
          offset: [0, 14],
          className: 'hunter-name',
        });
      } else {
        marker.setLatLng(center);
      }

      if (accuracyCircle === null) {
        accuracyCircle = L.circle(center, {
          radius: position.accuracy,
          weight: 1,
          fillOpacity: 0.12,
        }).addTo(map);
      } else {
        accuracyCircle.setLatLng(center);
        accuracyCircle.setRadius(position.accuracy);
      }

      if (firstPosition) {
        map.setView(center, POSITION_ZOOM);
        firstPosition = false;
      }
    },
    center(position: LocationSample) {
      map.setView([position.latitude, position.longitude], POSITION_ZOOM);
    },
    destroy() {
      map.remove();
    },
  };
}
