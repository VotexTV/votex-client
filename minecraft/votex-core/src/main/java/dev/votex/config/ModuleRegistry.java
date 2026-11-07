package dev.votex.config;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
public final class ModuleRegistry {
 static final int MAX_CONFIG_BYTES=256_000; // Eight complete 28-widget layouts plus current HUD state.
 public record Module(String id,String name,String description,String category,boolean runtimeToggle,String keybind){}
 public static final List<Module> MODULES=List.of(
  new Module("fps","Bildrate","FPS und Frame-Pacing auf einen Blick","HUD",true,""),
  new Module("cps","Klickrate","Linke und rechte Mausklicks pro Sekunde","HUD",true,""),
  new Module("frametime","Framezeit","Gemessener Abstand der gerenderten Frames","HUD",true,""),
  new Module("coordinates","Koordinaten","Deine aktuelle Blockposition","HUD",true,""),
  new Module("clock","Uhrzeit","Die lokale Uhrzeit","HUD",true,""),
  new Module("memory","Arbeitsspeicher","Aktuell belegter JVM-Heap","HUD",true,""),
  new Module("direction","Kompass","Blickrichtung und Himmelsrichtung","HUD",true,""),
  new Module("speed","Geschwindigkeit","Tatsächliches horizontales und vertikales Tempo in Blöcken pro Sekunde","HUD",true,""),
  new Module("keystrokes","Tastenanzeige","Deine belegten Bewegungs- und Sprungtasten","HUD",true,""),
  new Module("ping","Verbindung","Latenz zum aktuellen Server","HUD",true,""),
  new Module("armor","Rüstung","Getragene Items, Rüstungsslots und verbleibende Haltbarkeit","HUD",true,""),
  new Module("effects","Statuseffekte","Aktive Effekte und Restdauer","HUD",true,""),
  new Module("health","Gesundheit","Herzen und Absorption","HUD",true,""),
  new Module("food","Hunger","Nahrung und Sättigung","HUD",true,""),
  new Module("experience","Erfahrung","Level und Fortschritt zum nächsten Level","HUD",true,""),
  new Module("durability","Werkzeug","Haltbarkeit des Gegenstands in deiner Hand","HUD",true,""),
  new Module("biome","Biom","Name des aktuellen Bioms","HUD",true,""),
  new Module("dimension","Dimension","Name der aktuellen Dimension","HUD",true,""),
  new Module("light","Light Level","Blocklicht am Boden: grün geschützt, gelb nachts, rot aktuell dunkel genug","Grafik",true,""),
  new Module("day","Welttag","Vergangene Tage dieser Welt","HUD",true,""),
  new Module("session","Spielzeit","Zeit in der aktuellen Verbindung","HUD",true,""),
  new Module("players","Spieler","Spieler, die der Server in deiner Spielerliste anzeigt","HUD",true,""),
  new Module("weather","Wetter","Regen, Gewitter oder klarer Himmel","HUD",true,""),
  new Module("chunk","Chunk","Chunk-Koordinaten und Position im Chunk","HUD",true,""),
  new Module("target","Zielentfernung","Entfernung zum anvisierten Block oder Wesen","HUD",true,""),
  new Module("world_time","Weltzeit","Uhrzeit im Tag-Nacht-Zyklus","HUD",true,""),
  new Module("movement","Bewegung","Sprinten, Schleichen, Schwimmen und Fliegen","HUD",true,""),
  new Module("air","Atemluft","Verbleibende Atemluft unter Wasser","HUD",true,""),
  new Module("item_physics","Item Physics","Gegenstände liegen am Boden und drehen sich im Flug","Grafik",true,""),
  new Module("chat","Chat Studio","Verlauf durchsuchen, kopieren, antworten und Duplikate bündeln","Interface",true,"STRG + F"));
 public static final List<Module> HUD_MODULES=MODULES.stream().filter(m->m.category().equals("HUD")).toList();
 public static final class Position {public int x,y,viewWidth,viewHeight;public Position(){}public Position(int x,int y){this.x=x;this.y=y;}}
 public static final class Appearance {public int scale=100,background=70,glassIntensity=100;public boolean accent=true;public Appearance(){} }
 public static final class Config {public Map<String,HudLayouts.Layout> layouts=new LinkedHashMap<>();public GlassConfig glass=new GlassConfig();public dev.votex.ui.WorkspaceWindow.Saved workspaceWindow=new dev.votex.ui.WorkspaceWindow.Saved();public int schemaVersion=1;public Map<String,Boolean> enabled=new LinkedHashMap<>(Map.of("fps",true,"coordinates",false,"clock",false,"chat",true));public Map<String,Position> positions=new LinkedHashMap<>();public Map<String,Appearance> appearance=new LinkedHashMap<>();public boolean cosmeticsVisible=true,capeVisible=true,menuAnimations=true,blurMenus=true,chatStackDuplicates=true,chatSearch=true,chatContextMenu=true;public int x=12,y=12,scale=100;public int lightOverlayRadius=8;public boolean lightRisksOnly=false,itemAirTumble=true;public String selectedSkin="";}
 private final Path path;private Config config=new Config();
 public ModuleRegistry(Path path){this.path=path;}
 public void load()throws IOException{if(!Files.exists(path))return;if(Files.size(path)>MAX_CONFIG_BYTES)throw new IOException("Modules configuration is too large");try{Config c=AtomicJson.GSON.fromJson(Files.readString(path),Config.class);if(c==null||c.schemaVersion!=1||c.enabled==null||c.x<0||c.x>10000||c.y<0||c.y>10000||c.scale<75||c.scale>150)throw new IllegalArgumentException("Invalid modules configuration");for(var m:MODULES){if(!c.enabled.containsKey(m.id())||c.enabled.get(m.id())==null)c.enabled.put(m.id(),m.id().equals("chat"));}if(c.positions==null)c.positions=new LinkedHashMap<>();for(var p:c.positions.values())if(p==null||p.x<0||p.x>10000||p.y<0||p.y>10000)throw new IllegalArgumentException("Invalid HUD position");if(c.appearance==null)c.appearance=new LinkedHashMap<>();for(var a:c.appearance.values())if(a==null||a.scale<50||a.scale>200||a.background<0||a.background>100||a.glassIntensity<0||a.glassIntensity>100)throw new IllegalArgumentException("Invalid HUD appearance");if(c.workspaceWindow==null)c.workspaceWindow=new dev.votex.ui.WorkspaceWindow.Saved();if(!c.workspaceWindow.valid())throw new IllegalArgumentException("Invalid workspace window");if(c.layouts==null)c.layouts=new LinkedHashMap<>();if(c.layouts.size()>8)throw new IllegalArgumentException("Too many HUD layouts");for(var l:c.layouts.values())HudLayouts.validate(l);if(c.glass==null)c.glass=new GlassConfig();if(!c.glass.valid())throw new IllegalArgumentException("Invalid glass preferences");config=c;}catch(RuntimeException e){throw new IOException("Invalid modules configuration; original file preserved",e);}}
 public boolean enabled(String id){return Boolean.TRUE.equals(config.enabled.get(id));}
 public void toggle(String id)throws IOException{if(MODULES.stream().noneMatch(m->m.id().equals(id)))throw new IllegalArgumentException("Unknown module");config.enabled.put(id,!enabled(id));save();}
 public Config config(){return config;}
 public void save()throws IOException{AtomicJson.write(path,config);}
}

