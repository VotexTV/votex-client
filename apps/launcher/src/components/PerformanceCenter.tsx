import {useEffect,useState,useRef} from "react";
import {Activity,RefreshCw,Gauge} from "lucide-react";
import {GlassPanel,GlassButton,Toggle} from "./Glass";
import {Select} from "./Select";
import {call,native} from "../services/desktop";
type Config={schemaVersion:1;textCache:boolean;reusableMotion:boolean;adaptive:"DISABLED"|"CONSERVATIVE"|"BALANCED"|"AGGRESSIVE";targetFps:number};
type Report={created:string;scenario:string;frames:null|{samples:number;averageFps:number;low1Fps:number|null;low01Fps:number|null;p50Ms:number;p95Ms:number;p99Ms:number;maxMs:number};environment:{minecraft:string;java:string;processors:number;mods:string[]};votex:Record<string,number>;systemLast:{heapUsedBytes:number;nonHeapBytes:number;gcCount:number;gcTimeMs:number;windowActive?:boolean;effectiveFpsLimit?:number};truncated:boolean};
export function PerformanceCenter({profileId,profileName,act}:{profileId:string;profileName:string;act:(fn:()=>Promise<unknown>)=>unknown}){
 const [data,setData]=useState<{config:Config;report:Report|null}|null>(null);
 const currentProfile=useRef(profileId);currentProfile.current=profileId;
 const reload=async()=>{if(native){const result=await call<{config:Config;report:Report|null}>("performance_status",{profileId});if(currentProfile.current===profileId)setData(result);}};
 useEffect(()=>{setData(null);void act(reload);},[profileId]);
 const save=(patch:Partial<Config>)=>{if(!data)return;const config={...data.config,...patch};void act(async()=>{await call("performance_save",{profileId,value:config});if(currentProfile.current===profileId)setData({...data,config});});};
 const r=data?.report,f=r?.frames;
 return <GlassPanel className="engine-center">
  <div className="section-head"><div><small>VOTEX PERFORMANCE ENGINE</small><h2><Gauge size={21}/> Performance Center</h2><p>Native Optimierungen und lokale Messungen für „{profileName}“.</p></div><GlassButton disabled={!native} onClick={()=>void act(reload)}><RefreshCw size={15}/> Aktualisieren</GlassButton></div>
  <div className="engine-modules">
   <div><strong>Native HUD-Oberfläche</strong><p>Minecraft-GUI ohne Browser im Spiel. Keine zweite Chunk-Verwaltung neben Sodium.</p></div>
   {data&&<><Toggle label="Textbreiten zwischenspeichern" value={data.config.textCache} onChange={v=>save({textCache:v})}/><Toggle label="Emote-Puffer wiederverwenden" value={data.config.reusableMotion} onChange={v=>save({reusableMotion:v})}/>
   <label className="form-stack">Adaptive Votex-Effekte<Select value={data.config.adaptive} onChange={e=>save({adaptive:e.target.value as Config["adaptive"]})}><option value="DISABLED">Aus · volle gewählte Qualität</option><option value="CONSERVATIVE">Konservativ</option><option value="BALANCED">Ausgewogen</option><option value="AGGRESSIVE">Aggressiv</option></Select></label><p>Optional: verringert bei anhaltender Last nur die Aktualisierungsrate beweglicher Votex-Accessoires. Minecraft-Sichtweite und Simulation bleiben in deiner Hand.</p></>}
  </div>
  {f&&r?<><div className="game-overview">{[["Ø BILDRATE",f.averageFps,"FPS"],["1 % LOW",f.low1Fps,"FPS"],["P95 FRAMEZEIT",f.p95Ms,"ms"],["P99 FRAMEZEIT",f.p99Ms,"ms"]].map(([name,value,unit])=><div key={String(name)}><small>{name}</small><strong>{typeof value==="number"?value.toFixed(1):"—"}<em>{unit}</em></strong></div>)}</div><p>{f.samples.toLocaleString("de-DE")} Frames · {r.scenario} · {new Date(r.created).toLocaleString("de-DE")} · Java {r.environment.java} · {r.environment.processors} CPU-Threads{r.truncated?" · Aufzeichnung erreichte das Sample-Limit":""}</p><p>Heap zuletzt: {((r.systemLast?.heapUsedBytes??0)/1048576).toFixed(0)} MB · GC insgesamt seit JVM-Start: {r.systemLast?.gcCount??"—"} / {r.systemLast?.gcTimeMs??"—"} ms. GPU- und Chunk-Zeiten sind in dieser Version nicht instrumentiert. {r.systemLast?.windowActive===false?"Das Spielfenster war bei der letzten Probe im Hintergrund. ":""}{r.systemLast?.effectiveFpsLimit?`Zuletzt wirksames FPS-Limit: ${r.systemLast.effectiveFpsLimit}.`:""}</p></>:<div className="engine-empty"><Activity size={26}/><strong>Noch keine Messung für dieses Profil</strong><p>Öffne im Spiel Votex mit der rechten Umschalttaste oder über Escape → Votex Client → Performance → „30 s Benchmark“. Nach 10 Sekunden Aufwärmen beginnt die Messung. Schließe das Menü für eine normale Spielmessung; anschließend hier aktualisieren.</p></div>}
  <p>Messdaten bleiben lokal im Profil unter config/votex/benchmarks. Ein einzelner Lauf belegt keinen Vergleichsvorteil gegenüber anderen Clients.</p>
 </GlassPanel>;
}
