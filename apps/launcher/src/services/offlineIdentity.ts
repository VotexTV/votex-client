import type { AppState } from '../../../../packages/shared-config/schema';
export function normalizeOfflineName(value: string): string {
  const name = value.trim();
  if (!/^[A-Za-z0-9_]{1,16}$/.test(name)) throw new Error('Offline-Namen müssen 1–16 Zeichen haben und nur Buchstaben, Zahlen oder _ enthalten.');
  return name;
}
export function withOfflineUsername(state: AppState, value: string): AppState {
  return { ...state, offlineUsername: normalizeOfflineName(value) };
}
