package dev.votex.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.function.BooleanSupplier;
import dev.votex.ui.HoverMotion;

/** Real authored model thumbnail with a persistent equipment state. */
final class WardrobeCard extends Button {
 private final VotexClient core;private final Wardrobe.Item item;private final BooleanSupplier equipped;private final HoverMotion interaction=new HoverMotion();private long sampled=System.nanoTime();
 WardrobeCard(VotexClient core,int x,int y,int w,int h,Wardrobe.Item item,BooleanSupplier selected,OnPress press){super(x,y,w,h,Component.literal(item.name()),press,DEFAULT_NARRATION);this.core=core;this.item=item;this.equipped=selected;}
 @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
  long now=System.nanoTime();float dt=Math.min(.1f,(now-sampled)/1e9f);sampled=now;boolean on=equipped.getAsBoolean();interaction.step(dt,isHoveredOrFocused(),active,mx,my,getX(),getY(),getWidth(),getHeight(),core.theme,VotexGlassDesignSystem.motion(core));
  int x=getX(),y=getY(),w=getWidth(),h=getHeight();GlassRender.round(g,x,y,w,h,12,GlassRender.surface(GlassRender.blend(on?GlassRender.alpha(core.theme.accent(),.35):0x8a111e2e,0xc52a415c,interaction.hover()),core.theme));GlassRender.border(g,x,y,w,h,12,on?GlassRender.alpha(core.theme.accent(),.74):0x32b7d3f0);GlassRender.interaction(g,x,y,w,h,12,core.theme,interaction);
  if(item.slot().equals("skin")){Identifier texture=Wardrobe.head(core,item.id());if(texture!=null){int size=Math.min(w-34,h-50);g.pose().pushMatrix();g.pose().translate(x+(w-size)/2,y+13);g.pose().scale(size/8f);g.blit(RenderPipelines.GUI_TEXTURED,texture,0,0,8,8,8,8,64,64);g.blit(RenderPipelines.GUI_TEXTURED,texture,0,0,40,8,8,8,64,64);g.pose().popMatrix();}}
  else if(item.id().startsWith("ingame-")){Identifier texture=Wardrobe.capeThumbnail(core,item.id());if(texture!=null){int imageH=h-48,imageW=imageH*10/16;g.pose().pushMatrix();g.pose().translate(x+(w-imageW)/2,y+8);g.pose().scale(imageW/10f,imageH/16f);g.blit(RenderPipelines.GUI_TEXTURED,texture,0,0,12,1,10,16,64,32);g.pose().popMatrix();}}
  else if(!item.id().startsWith("custom-")&&!item.slot().equals("emote")&&!item.slot().equals("look")){Identifier texture=Wardrobe.preview(item.id());if(texture!=null){float fit=Math.min((w-12)/384f,(h-38)/300f);g.pose().pushMatrix();g.pose().translate(x+(w-384*fit)/2,y+3+(h-38-300*fit)/2);g.pose().scale(fit);g.blit(RenderPipelines.GUI_TEXTURED,texture,0,0,0,0,384,300,384,300);g.pose().popMatrix();}}
  else GlassRender.icon(g,item.slot().equals("emote")?"emotes":item.slot().equals("look")?"cosmetics":"cube",x+w/2-19,y+(h-40)/2-16,38,0xffb6d9ff);
  UiText.draw(g,UiText.fit(item.name(),w-18),x+9,y+h-27,core.theme.text());UiText.draw(g,item.slot().equals("skin")?"Skin auswählen":on?"Ausgerüstet":"Kostenlos · Anprobieren",x+9,y+h-13,on?0xffa3ceff:core.theme.muted());
  if(isFocused())GlassRender.border(g,x,y,w,h,12,GlassRender.alpha(core.theme.accent(),.85));
  if(on){GlassRender.round(g,x+w-24,y+8,16,16,8,GlassRender.alpha(core.theme.accent(),.8));GlassRender.icon(g,"check",x+w-22,y+10,12,0xffffffff);}
  if(isHoveredOrFocused()&&!item.description().isEmpty())UiTooltip.request(item.description(),mx,my);
 }
}
