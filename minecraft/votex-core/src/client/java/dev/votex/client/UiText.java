package dev.votex.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import dev.votex.client.mixin.FontAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Dedicated, oversampled OFL typeface. Vanilla chat and world text keep their font. */
public final class UiText {
 public static final Style STYLE=Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("votex_client","sans"))).withoutShadow();
 private static Font font,heading;
 private static final FontDescription HEADING=new FontDescription.Resource(Identifier.fromNamespaceAndPath("votex_client","sans_heading"));
 private static final Map<String,Component> CACHE=new LinkedHashMap<>(256,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Component> e){return size()>512;}};
 private UiText(){}
 private static Font create(FontDescription selected){Font.Provider source=((FontAccessor)Minecraft.getInstance().font).votex$provider();return new SmoothUiFont(new Font.Provider(){public GlyphSource glyphs(FontDescription description){return source.glyphs(description.equals(FontDescription.DEFAULT)?selected:description);}public EffectGlyph effect(){return source.effect();}});}
 public static Font font(){if(font==null)font=create(STYLE.getFont());return font;}
 private static Font heading(){if(heading==null)heading=create(HEADING);return heading;}
 public static int titleWidth(String value){return heading().width(value);}
 public static Component component(String value){return CACHE.computeIfAbsent(value,s->Component.literal(s).withStyle(STYLE));}
 /** UI-only copy. Preserve message colors/events and explicit custom fonts; never rewrite signed originals. */
 public static Component presentation(Component source){var display=Component.empty();source.visit((style,text)->{var font=style.getFont();display.append(Component.literal(text).withStyle((font.equals(FontDescription.DEFAULT)?style.withFont(STYLE.getFont()):style).withoutShadow()));return Optional.empty();},Style.EMPTY);return display;}
 public static int width(String value){return font().width(value);}
 public static String fit(String value,int width){if(width<=0)return "";if(width(value)<=width)return value;int ellipsis=width("…");if(ellipsis>width)return "";return font().plainSubstrByWidth(value,width-ellipsis)+"…";}
 public static void draw(GuiGraphicsExtractor g,String value,int x,int y,int color){g.text(font(),value,x,y,VotexGlassDesignSystem.ink(color),false);}
 public static void centered(GuiGraphicsExtractor g,String value,int x,int y,int color){draw(g,value,x-width(value)/2,y,color);}
 public static void title(GuiGraphicsExtractor g,String value,int x,int y,int color,float scale){g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale);g.text(heading(),value,0,0,VotexGlassDesignSystem.ink(color),false);g.pose().popMatrix();}
 public static void clear(){CACHE.clear();font=null;heading=null;}
}
