import { describe,it,expect } from 'vitest';
import { initialState, stateSchema } from '../../../../packages/shared-config/schema';
import { withOfflineUsername,normalizeOfflineName } from './offlineIdentity';
describe('offline account name',()=>{
 it('persists a normalized name without changing any other state',()=>{const next=withOfflineUsername(initialState,'  Votex_42  ');expect(next.offlineUsername).toBe('Votex_42');expect(initialState.offlineUsername).toBe('VotexPlayer');expect({...next,offlineUsername:initialState.offlineUsername}).toEqual(initialState);expect(stateSchema.parse(JSON.parse(JSON.stringify(next))).offlineUsername).toBe('Votex_42');});
 it('rejects values Minecraft cannot use',()=>{for(const value of ['', 'a'.repeat(17),'Name Here','<user>','Spielerä'])expect(()=>normalizeOfflineName(value)).toThrow();expect(normalizeOfflineName('a')).toBe('a');});
});
