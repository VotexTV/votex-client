import { useEffect, useState } from 'react';
import { Check, UserRound } from 'lucide-react';
import { GlassButton } from './Glass';
import { normalizeOfflineName } from '../services/offlineIdentity';
import { errorText } from '../services/messages';
export function OfflineIdentity({name, de, onSave}: {name:string; de:boolean; onSave:(name:string)=>Promise<void>}) {
  const [draft,setDraft]=useState(name),[saving,setSaving]=useState(false),[saved,setSaved]=useState(false),[error,setError]=useState('');
  useEffect(()=>{setDraft(name);},[name]);
  return <form className="offline-identity" onSubmit={async e=>{e.preventDefault();if(saving)return;setError('');setSaved(false);try{const next=normalizeOfflineName(draft);setSaving(true);await onSave(next);setDraft(next);setSaved(true);}catch(error){setError(errorText(error));}finally{setSaving(false);}}}>
    <div className="offline-identity-heading"><UserRound size={19}/><div><strong>{de?'Offline-Spielername':'Offline player name'}</strong><p>{de?'Wird beim nächsten Offline-Spielstart verwendet. Dein Microsoft-Konto bleibt unverändert.':'Used on your next offline launch. Your Microsoft account stays unchanged.'}</p></div></div>
    <label htmlFor="account-offline-name">{de?'Minecraft-Spielername':'Minecraft player name'}</label>
    <div className="offline-identity-input"><input id="account-offline-name" value={draft} onChange={e=>{setDraft(e.target.value);setSaved(false);setError('');}} maxLength={16} autoComplete="off" spellCheck={false} disabled={saving} aria-invalid={!!error} aria-describedby="offline-name-feedback"/><GlassButton type="submit" disabled={saving}>{saved&&<Check size={16}/>} {saving?(de?'Speichern …':'Saving …'):(de?'Speichern':'Save')}</GlassButton></div>
    <small id="offline-name-feedback" role={error?'alert':'status'}>{error||(saved?(de?'Gespeichert · gilt ab dem nächsten Offline-Start.':'Saved · applies to your next offline launch.'):(de?'1–16 Zeichen: A–Z, a–z, 0–9 und _.':'1–16 characters: A–Z, a–z, 0–9 and _.'))}</small>
  </form>;
}
