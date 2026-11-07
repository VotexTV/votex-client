package dev.votex.client;

import net.minecraft.client.gui.font.TextRenderable;

/** Sampling belongs to each extracted draw, never to Minecraft's shared GUI atlas.
 * The scope covers only Votex's high-resolution glass/icon sprites and catalogue logos. Pixel-art
 * skins, items, world textures and vanilla text retain their original sampling.
 */
public final class UiSampling {
 private static final ThreadLocal<Integer> surfaces=ThreadLocal.withInitial(()->0);
 private static long smoothGlyphs,smoothSurfaces;
 private UiSampling(){}
 public static void beginSurface(){surfaces.set(surfaces.get()+1);}
 public static void endSurface(){int depth=surfaces.get();if(depth<=0)throw new IllegalStateException("Unbalanced UI sampling scope");surfaces.set(depth-1);}
 public static boolean surface(){if(surfaces.get()==0)return false;smoothSurfaces++;return true;}
 public static boolean font(TextRenderable glyph){boolean own=glyph instanceof SmoothUiFont.SmoothGlyph;if(own)smoothGlyphs++;return own;}
 public static long smoothGlyphs(){return smoothGlyphs;}
 public static long smoothSurfaces(){return smoothSurfaces;}
 public static boolean surfaceScopeClosed(){return surfaces.get()==0;}
}
