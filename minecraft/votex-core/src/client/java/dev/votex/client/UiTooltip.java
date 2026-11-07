package dev.votex.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
/** Deferred, bounded glass help bubble. Never invokes Minecraft's rectangular item tooltip. */
final class UiTooltip {
 private static String requested="";private static int mouseX,mouseY;
 private UiTooltip(){}
 static void begin(){requested="";}
 static void request(String text,int x,int y){requested=text;mouseX=x;mouseY=y;}
 static void render(GuiGraphicsExtractor g,int viewportWidth,int viewportHeight){if(requested.isEmpty())return;int w=Math.min(330,viewportWidth-24);var lines=UiText.font().split(UiText.component(requested),w-26);int count=Math.min(5,lines.size()),h=count*14+22,x=Math.clamp(mouseX+16,12,Math.max(12,viewportWidth-w-12)),y=Math.clamp(mouseY+19,12,Math.max(12,viewportHeight-h-12));g.nextStratum();GlassRender.round(g,x-2,y,w+4,h+5,14,0x39000000);GlassRender.round(g,x,y,w,h,12,0xf11c2d43);GlassRender.border(g,x,y,w,h,12,0x70a6c9ec);for(int i=0;i<count;i++)g.text(UiText.font(),lines.get(i),x+13,y+11+i*14,VotexGlassDesignSystem.ink(0xffe6f0fb),false);requested="";}
}
