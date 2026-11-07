package dev.votex.client;
import dev.votex.config.AtomicJson;
import dev.votex.performance.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import java.lang.management.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import jdk.jfr.Recording;

/** Render-thread counters, opt-in bounded captures, one bounded IO worker. No GPU/renderer ownership. */
public final class VotexPerformanceEngine implements AutoCloseable {
 public static VotexPerformanceEngine INSTANCE;
 public final PerformanceConfig config;
 private final Path directory;
 private final OptimizationCoordinator coordinator=new OptimizationCoordinator();
 private final ThreadMXBean threads=ManagementFactory.getThreadMXBean();
 private final MemoryMXBean memory=ManagementFactory.getMemoryMXBean();
 private final OperatingSystemMXBean os=ManagementFactory.getOperatingSystemMXBean();
 private final List<GarbageCollectorMXBean> collectors=ManagementFactory.getGarbageCollectorMXBeans();
 private final List<BufferPoolMXBean> buffers=ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"Votex diagnostics IO");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private long previous,frameStart,cpuStart,lastTick,cpuPrevious=-1,cpuWall,allocationPrevious=-1;
 private long hudNs,emoteNs,cosmeticNs,hudCalls,emoteCalls,cosmeticCalls,renderCpuNs;
 private double smoothedMs;
 private Capture capture;
 private Recording recording;
 private volatile String status="Bereit · keine Messung",lastFile="";
 private Map<String,Object> system=Map.of();
 private record Capture(long warmupUntil,long end,long[] frames,int[] count,List<Map<String,Object>> system,String scenario,Map<String,Object> environment){}
 public VotexPerformanceEngine(Path dir){directory=dir;PerformanceConfig loaded;try{loaded=PerformanceConfig.read(dir.resolve("performance.json"));}catch(Exception e){loaded=new PerformanceConfig();VotexClient.LOG.warning(e.getMessage());}config=loaded;INSTANCE=this;VotexTextCache.enabled=config.textCache;}
 public void frameBegin(){long now=System.nanoTime();frameStart=now;if(previous!=0){long interval=now-previous;smoothedMs=smoothedMs==0?interval/1e6:smoothedMs*.95+interval/1e6*.05;Capture c=capture;if(c!=null&&now>=c.warmupUntil&&now<=c.end&&c.count[0]<c.frames.length)c.frames[c.count[0]++]=interval;}previous=now;cpuStart=0;if(measuring()&&threads.isCurrentThreadCpuTimeSupported())cpuStart=threads.getCurrentThreadCpuTime();}
 public void frameEnd(){if(capture!=null&&cpuStart>0)renderCpuNs+=Math.max(0,threads.getCurrentThreadCpuTime()-cpuStart);}
 public boolean measuring(){return capture!=null&&System.nanoTime()>=capture.warmupUntil;}
 public long timer(){return measuring()?System.nanoTime():0;}
 public void hud(long start){if(start!=0){hudNs+=System.nanoTime()-start;hudCalls++;}}
 public void emote(long start){if(start!=0){emoteNs+=System.nanoTime()-start;emoteCalls++;}}
 public void cosmetic(long start){if(start!=0){cosmeticNs+=System.nanoTime()-start;cosmeticCalls++;}}
 public String status(){return status;}
 public String lastFile(){return lastFile;}
 public double frameMs(){return smoothedMs;}
 public int adaptiveTier(){return coordinator.tier();}
 public boolean animateCosmetics(long tick){return adaptiveTier()==0||tick%(adaptiveTier()==1?2:3)==0;}
 public Map<String,Object> system(){return system;}
 public List<Map<String,Object>> modules(){var loader=FabricLoader.getInstance();return List.of(module("native-hud",true,"Minecraft GUI · ohne Browser"),module("text-width-cache",config.textCache,"256 Einträge · Reload/Languages invalidieren"),module("reusable-emote-buffers",config.reusableMotion,"Lokaler Spieler · keine Simulation"),module("adaptive-votex-effects",config.adaptive!=OptimizationCoordinator.Mode.DISABLED,"Nur optionale Votex-Effekte"),module("sodium-owner",loader.isModLoaded("sodium"),"Chunk- und GPU-Verwaltung bleibt bei Sodium"),module("iris",loader.isModLoaded("iris"),"Shader-Mod erkannt; keine Shader-Manipulation"));}
 private Map<String,Object> module(String id,boolean active,String note){return Map.of("id",id,"enabled",active,"supportedMinecraft","26.2","initialization","available","note",note);}
 public void tick(boolean diagnosticsOpen){long now=System.nanoTime();if(now-lastTick<1_000_000_000L)return;lastTick=now;
  var client=Minecraft.getInstance();if(config.adaptive==OptimizationCoordinator.Mode.DISABLED||client.isWindowActive())coordinator.observe(smoothedMs,OptimizationCoordinator.targetFrameMs(config.targetFps,client.getFramerateLimitTracker().getFramerateLimit(),client.getWindow().getRefreshRate(),client.options.enableVsync().get()),config.adaptive,now/1_000_000_000L);
  if(capture!=null||diagnosticsOpen){system=systemSample(now);if(capture!=null&&capture.system.size()<610)capture.system.add(system);}
  if(capture!=null){status=now<capture.warmupUntil?"Aufwärmen · "+((capture.warmupUntil-now)/1_000_000_000L+1)+" s":"Messung · "+((capture.end-now)/1_000_000_000L+1)+" s";if(now>=capture.end)finish();}
 }
 private Map<String,Object> systemSample(long now){Map<String,Object> v=new LinkedHashMap<>();v.put("seconds",now/1e9);var heap=memory.getHeapMemoryUsage();v.put("heapUsedBytes",heap.getUsed());v.put("heapCommittedBytes",heap.getCommitted());v.put("heapMaxBytes",heap.getMax());v.put("nonHeapBytes",memory.getNonHeapMemoryUsage().getUsed());
  long count=0,ms=0;for(var gc:collectors){count+=Math.max(0,gc.getCollectionCount());ms+=Math.max(0,gc.getCollectionTime());}v.put("gcCount",count);v.put("gcTimeMs",ms);for(var pool:buffers)if(pool.getName().equals("direct"))v.put("directBufferBytes",pool.getMemoryUsed());
  if(os instanceof com.sun.management.OperatingSystemMXBean extended){long cpu=extended.getProcessCpuTime();if(cpuPrevious>=0&&now>cpuWall)v.put("processCpuPercent",100d*(cpu-cpuPrevious)/(now-cpuWall)/os.getAvailableProcessors());cpuPrevious=cpu;cpuWall=now;v.put("availablePhysicalBytes",extended.getFreeMemorySize());}
  if(threads instanceof com.sun.management.ThreadMXBean extended&&extended.isThreadAllocatedMemorySupported()&&extended.isThreadAllocatedMemoryEnabled()){long bytes=extended.getThreadAllocatedBytes(Thread.currentThread().threadId());if(allocationPrevious>=0)v.put("renderThreadAllocationBytesPerSecond",bytes-allocationPrevious);allocationPrevious=bytes;}
  var client=Minecraft.getInstance();v.put("windowActive",client.isWindowActive());v.put("effectiveFpsLimit",client.getFramerateLimitTracker().getFramerateLimit());
  return v;
 }
 public boolean start(String scenario,int seconds){if(capture!=null)return false;int length=Math.clamp(seconds,30,600);long now=System.nanoTime();Map<String,Object> env=new LinkedHashMap<>();var mc=Minecraft.getInstance();env.put("minecraft","26.2");env.put("harness",("1".equals(System.getenv("VOTEX_PERFORMANCE_TEST"))||"1".equals(System.getenv("VOTEX_COSMETICS2_TEST")))?"fabric-client-gametest-accelerated":"normal-client");env.put("java",System.getProperty("java.version"));env.put("os",os.getName());env.put("processors",os.getAvailableProcessors());env.put("width",mc.getWindow().getWidth());env.put("height",mc.getWindow().getHeight());env.put("renderDistance",mc.options.renderDistance().get());env.put("simulationDistance",mc.options.simulationDistance().get());env.put("fpsLimit",mc.options.framerateLimit().get());env.put("vsync",mc.options.enableVsync().get());env.put("modules",modules());env.put("mods",FabricLoader.getInstance().getAllMods().stream().map(m->m.getMetadata().getId()+"@"+m.getMetadata().getVersion()).sorted().toList());
  capture=new Capture(now+10_000_000_000L,now+(length+10L)*1_000_000_000L,new long[120000],new int[1],new ArrayList<>(),scenario,env);hudNs=emoteNs=cosmeticNs=hudCalls=emoteCalls=cosmeticCalls=renderCpuNs=0;cpuStart=0;allocationPrevious=cpuPrevious=-1;status="Aufwärmen · 10 s";return true;
 }
 public void finish(){Capture c=capture;if(c==null)return;capture=null;long h=hudNs,e=emoteNs,m=cosmeticNs,hc=hudCalls,ec=emoteCalls,mc=cosmeticCalls,cpu=renderCpuNs;Map<String,Object> telemetry=Map.of("hudSubmissionNs",h,"hudCalls",hc,"emotePoseNs",e,"emoteCalls",ec,"cosmeticSubmissionNs",m,"cosmeticCalls",mc,"renderThreadCpuNs",cpu,"textCacheHits",VotexTextCache.hits,"textCacheMisses",VotexTextCache.misses);
  submit(()->{try{Map<String,Object> report=new LinkedHashMap<>();report.put("schemaVersion",1);report.put("created",Instant.now().toString());report.put("scenario",c.scenario);report.put("environment",c.environment);report.put("frames",c.count[0]>0?FrameStatistics.of(c.frames,c.count[0]):null);report.put("frameIntervalsNs",Arrays.copyOf(c.frames,c.count[0]));report.put("truncated",c.count[0]==c.frames.length);report.put("system",c.system);report.put("votex",telemetry);report.put("unavailable",List.of("gpuFrameTime","chunkBuildTime","chunkUploadTime","totalEntityRenderTime"));String name="benchmark-"+System.currentTimeMillis()+".json";AtomicJson.write(directory.resolve("benchmarks").resolve(name),report);AtomicJson.write(directory.resolve("performance-latest.json"),report);lastFile=name;status="Messung gespeichert · "+c.count[0]+" Frames";}catch(Exception error){status="Export fehlgeschlagen: "+error.getClass().getSimpleName();VotexClient.LOG.warning(status);}});
 }
 public String bottleneck(){if(system.get("heapMaxBytes") instanceof Long max&&max>0&&system.get("heapUsedBytes") instanceof Long used&&used>max*.85)return "Speicherdruck · Heap über 85 %";if(hudCalls>0&&hudNs/(double)hudCalls>2_000_000)return "HUD-Arbeit erhöht · gemessene Submission";return "Unbestimmt · GPU/Chunk-Timings nicht verfügbar";}
 public void reloadConfig(){submit(()->{try{var next=PerformanceConfig.read(directory.resolve("performance.json"));Minecraft.getInstance().execute(()->{config.textCache=next.textCache;config.reusableMotion=next.reusableMotion;config.adaptive=next.adaptive;config.targetFps=next.targetFps;VotexTextCache.enabled=config.textCache;});}catch(Exception e){VotexClient.LOG.warning("Performance configuration reload rejected: "+e);}});}
 public void save(){VotexTextCache.enabled=config.textCache;var snapshot=AtomicJson.GSON.toJsonTree(config);submit(()->{try{AtomicJson.write(directory.resolve("performance.json"),snapshot);}catch(Exception e){VotexClient.LOG.warning("Performance settings could not be saved: "+e);}});}
 public void saveModules(dev.votex.config.ModuleRegistry modules){var snapshot=AtomicJson.GSON.toJsonTree(modules.config());submit(()->{try{AtomicJson.write(directory.resolve("modules.json"),snapshot);}catch(Exception e){VotexClient.LOG.warning("HUD settings could not be saved: "+e);}});}
 public void saveWardrobe(com.google.gson.JsonObject preferences){var snapshot=preferences.deepCopy();submit(()->{try{AtomicJson.write(directory.resolve("ingame-look.json"),snapshot);}catch(Exception e){VotexClient.LOG.warning("Garderobe konnte nicht gespeichert werden: "+e);}});}
 public void saveLastServer(com.google.gson.JsonObject record){var snapshot=record.deepCopy();String nonce=System.getProperty("votex.launchNonce","");submit(()->{try{AtomicJson.write(directory.resolve("last-server.json"),snapshot);if(nonce.matches("[a-f0-9-]{36}"))AtomicJson.write(directory.resolve("startup.json"),Map.of("schemaVersion",1,"nonce",nonce,"stage","world-joined"));}catch(Exception e){VotexClient.LOG.warning("Letzter Server konnte nicht gespeichert werden: "+e);}});}
 private void submit(Runnable job){try{worker.execute(job);}catch(RejectedExecutionException e){status="Speicherwarteschlange voll · bitte erneut versuchen";VotexClient.LOG.warning(status);}}
 public boolean jfr(){try{if(recording!=null){Recording r=recording;recording=null;r.stop();Path out=directory.resolve("benchmarks/recording-"+System.currentTimeMillis()+".jfr");submit(()->{try(r){Files.createDirectories(out.getParent());r.dump(out);status="JFR lokal gespeichert";}catch(Exception e){status="JFR-Export fehlgeschlagen";}});return false;}recording=new Recording(jdk.jfr.Configuration.getConfiguration("profile"));recording.setMaxAge(java.time.Duration.ofMinutes(5));recording.setMaxSize(64*1024*1024);recording.start();status="JFR aktiv · maximal 5 Minuten / 64 MB";return true;}catch(Exception e){status="JFR nicht verfügbar: "+e.getClass().getSimpleName();return false;}}
 public void clearWorld(){previous=0;smoothedMs=0;VotexTextCache.clear();}
 @Override public void close(){finish();if(recording!=null){recording.close();recording=null;}worker.shutdown();try{if(!worker.awaitTermination(3,TimeUnit.SECONDS))VotexClient.LOG.warning("Diagnostics IO still completing");}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
