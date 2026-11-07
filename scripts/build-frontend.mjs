import { spawnSync } from 'node:child_process';
import { build } from 'vite';
const result=spawnSync(process.execPath,['node_modules/typescript/bin/tsc','-p','apps/launcher/tsconfig.json'],{stdio:'inherit'});
if(result.status!==0)process.exit(result.status??1);
await build({configFile:'apps/launcher/vite.config.ts'});
