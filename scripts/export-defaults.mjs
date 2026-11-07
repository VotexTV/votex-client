import {writeFileSync} from 'node:fs';
import {initialState} from '../packages/shared-config/schema.ts';
writeFileSync(new URL('../packages/shared-config/defaults.json',import.meta.url),JSON.stringify(initialState,null,2)+'\n');
