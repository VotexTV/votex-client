package dev.votex.client;

import dev.votex.ui.InstallActivity;
import dev.votex.ui.InstallationNoticeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Global non-interactive glass feedback survives closing the library. */
final class InstallationFeedback {
 static final InstallActivity ACTIVITY=new InstallActivity();
 private InstallationFeedback(){}
 static void render(GuiGraphicsExtractor g){render(g,-1,-1);}
 static InstallationNoticeLayout layout(){var mc=Minecraft.getInstance();return InstallationNoticeLayout.measure(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),mc.getWindow().getGuiScale(),mc.gui.screen() instanceof VotexMenuSurface);}
 static void render(GuiGraphicsExtractor g,int mouseX,int mouseY){
  long now=System.nanoTime();ACTIVITY.expire(now);var item=ACTIVITY.snapshot();if(item==null)return;
  var mc=Minecraft.getInstance();var core=VotexClient.INSTANCE;if(core==null)return;
  var layout=layout();int width=layout.width(),height=layout.height(),x=layout.x(),y=layout.y();
  boolean motion=VotexGlassDesignSystem.motion(core),working=item.phase().equals("working"),ready=item.phase().equals("ready");
  float enter=motion?1-(float)Math.pow(1-Math.clamp((now-item.changed())/350_000_000d,0,1),4):1;
  g.nextStratum();g.pose().pushMatrix();g.pose().scale(layout.scale());g.pose().translate(0,(1-enter)*(layout.ribbon()?height:25));
  if(layout.ribbon()){
   int color=ready?0xff79e1b8:working?core.theme.accent():0xffffb48c;
   GlassRender.round(g,x,y,width,height,6,GlassRender.alpha(core.theme.panel(),.98));GlassRender.border(g,x,y,width,height,6,GlassRender.alpha(color,.42));
   if(working&&motion){g.pose().pushMatrix();g.pose().translate(x+10,y+8);g.pose().rotate((now%1_400_000_000L)/1_400_000_000f*(float)(Math.PI*2));GlassRender.icon(g,"refresh",-5,-5,10,color);g.pose().popMatrix();}else GlassRender.icon(g,ready?"check":working?"cube":"close",x+5,y+3,10,color);
   String title=working?item.title():ready?"Für den nächsten Start bereit":"Installation fehlgeschlagen",percent=working&&item.progress()>=0?Math.round(item.progress()*100)+" %":"";
   int reserved=percent.isEmpty()?8:UiText.width(percent)+14;
   UiText.draw(g,UiText.fit(title+" · "+item.detail(),width-24-reserved),x+21,y+3,core.theme.text());if(!percent.isEmpty())UiText.draw(g,percent,x+width-UiText.width(percent)-7,y+3,core.theme.muted());
   progress(g,item,x+5,y+height-2,width-10,color,now,motion);
   if(enter>.98&&layout.contains(mouseX,mouseY)){UiTooltip.begin();UiTooltip.request(title+" · "+item.detail(),Math.round(mouseX/layout.scale()),Math.round(mouseY/layout.scale()));UiTooltip.render(g,layout.viewportWidth(),layout.viewportHeight());}
   g.pose().popMatrix();return;
  }
  GlassRender.popover(g,x,y,width,height,core.theme);int color=ready?0xff79e1b8:working?core.theme.accent():0xffffb48c;
  GlassRender.round(g,x+15,y+19,41,41,20,GlassRender.blend(0x403e536c,color,.15f));
  if(working&&motion){g.pose().pushMatrix();g.pose().translate(x+35.5f,y+39.5f);g.pose().rotate((now%1_400_000_000L)/1_400_000_000f*(float)(Math.PI*2));GlassRender.icon(g,"refresh",-11,-11,22,color);g.pose().popMatrix();}else GlassRender.icon(g,ready?"check":working?"cube":"close",x+25,y+29,21,color);
  UiText.draw(g,UiText.fit(working?item.title():ready?"Für den nächsten Start bereit":"Installation fehlgeschlagen",width-91),x+69,y+19,core.theme.text());
  var lines=UiText.font().split(UiText.component(item.detail()),width-89);for(int i=0;i<Math.min(2,lines.size());i++)g.text(UiText.font(),lines.get(i),x+69,y+39+i*14,VotexGlassDesignSystem.ink(core.theme.muted()),false);
  progress(g,item,x+69,y+77,width-90,color,now,motion);if(working&&item.progress()>=0)UiText.draw(g,Math.round(item.progress()*100)+" % · aktuelle Datei",x+69,y+88,core.theme.muted());
  g.pose().popMatrix();
 }
 private static void progress(GuiGraphicsExtractor g,InstallActivity.State item,int x,int y,int track,int color,long now,boolean motion){int height=Minecraft.getInstance().gui.screen() instanceof VotexMenuSurface?2:4;GlassRender.round(g,x,y,track,height,2,0x40567291);if(item.progress()>=0)GlassRender.round(g,x,y,Math.max(1,(int)(track*item.progress())),height,2,color);else if(item.phase().equals("working")){int segment=Math.max(16,track/4),shift=motion?(int)((now%1_500_000_000L)/1_500_000_000d*(track-segment)):0;GlassRender.round(g,x+shift,y,motion?segment:track,height,2,color);}}
}
