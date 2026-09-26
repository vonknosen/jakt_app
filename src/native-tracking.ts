import { registerPlugin } from '@capacitor/core';
import type { TrackingProvider } from './tracking-model';

export const TestTracking = registerPlugin<TrackingProvider>('TestTracking');
