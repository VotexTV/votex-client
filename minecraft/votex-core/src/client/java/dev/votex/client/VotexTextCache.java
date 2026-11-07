package dev.votex.client;
import net.minecraft.client.gui.Font;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded width cache only; Minecraft still owns glyph layout, Unicode, style and rendering. */
final class VotexTextCache {
 private static final class Key {Font font;String text;int maxWidth;Key(Font font,String text,int maxWidth){this.font=font;this.text=text;this.maxWidth=maxWidth;}@Override public int hashCode(){return (System.identityHashCode(font)*31+maxWidth)*31+text.hashCode();}@Override public boolean equals(Object other){return other instanceof Key k&&font==k.font&&maxWidth==k.maxWidth&&text.equals(k.text);}}
 // Render-thread-confined lookup probe is never inserted; retained keys are immutable after construction.
 private static final Key LOOKUP=new Key(null,"",0);
 private record Value(String text,int width){}
 private static final Map<Key,Value> CACHE=new LinkedHashMap<>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<Key,Value> e){return size()>256;}};
 static int generation;static long hits,misses;static boolean enabled=true;
 static Value value(Font font,String text,int max){if(!enabled)return measure(font,text,max);LOOKUP.font=font;LOOKUP.text=text;LOOKUP.maxWidth=max;Value v=CACHE.get(LOOKUP);if(v!=null){hits++;return v;}misses++;v=measure(font,text,max);CACHE.put(new Key(font,text,max),v);return v;}
 private static Value measure(Font font,String text,int max){String s=max>0?font.plainSubstrByWidth(text,max):text;return new Value(s,font.width(s));}
 static int width(Font font,String text){return value(font,text,0).width();}
 static String fit(Font font,String text,int max){return value(font,text,max).text();}
 static void clear(){CACHE.clear();LOOKUP.font=null;LOOKUP.text="";hits=misses=0;generation++;}
 static int size(){return CACHE.size();}
}
