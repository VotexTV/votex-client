package dev.votex.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;
import dev.votex.ui.HoverMotion;
import net.minecraft.client.input.MouseButtonEvent;

/** Native input/narration, dedicated sans font, antialiased surfaces and time-based hover. */
public class GlassButton extends Button {
 private final VotexClient core;private final String detail;private final BooleanSupplier selected;
 private float switchPosition=-1;private String icon="";private boolean toggle,nav,primary,iconOnly,sharedSelection;private final HoverMotion interaction=new HoverMotion();private long sampled=System.nanoTime(),emerged,pressed;private int sourceY;
 public GlassButton sharedSelection(){sharedSelection=true;return this;}
 public GlassButton(VotexClient core,int x,int y,int w,int h,String title,String detail,BooleanSupplier selected,OnPress action){super(x,y,w,h,Component.literal(title),action,DEFAULT_NARRATION);this.core=core;this.detail=detail;this.selected=selected;if(title.equals("−")||title.equals("+")){icon=title.equals("−")?"minus":"plus";iconOnly=true;}}
 public GlassButton icon(String id){icon=id;return this;}public GlassButton toggle(){toggle=true;return this;}public GlassButton navigation(){nav=true;return this;}public GlassButton primary(){primary=true;return this;}
 public GlassButton iconOnly(){iconOnly=true;return this;}
 public GlassButton emerge(int anchor,long started){sourceY=anchor;emerged=started;return this;}
 @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
  boolean over=isHoveredOrFocused()&&active&&core.modules.config().glass.hover,on=selected.getAsBoolean();long now=System.nanoTime();float dt=Math.min(.1f,(now-sampled)/1e9f);sampled=now;
  boolean motion=core.modules.config().glass.motion(core.theme,VotexGlassDesignSystem.motion(core));interaction.step(dt*(.5+core.modules.config().glass.speed/100.),over,active,mx,my,getX(),getY(),getWidth(),getHeight(),core.theme,motion);float hover=interaction.hover();
  boolean popping=motion&&emerged!=0&&now-emerged<260_000_000L;if(popping){float t=Math.clamp((now-emerged)/260_000_000f,0,1),s=1-(float)Math.pow(1-t,4)*.13f;g.pose().pushMatrix();g.pose().translate(0,sourceY);g.pose().scale(1,s);g.pose().translate(0,-sourceY);}
  int x=getX(),y=getY(),w=getWidth(),h=getHeight(),r=Math.max(6,Math.min(12,core.theme.radius()));
  boolean press=motion&&core.theme.effects().wobbleEffect()&&pressed!=0&&now-pressed<180_000_000L;if(press){float phase=(now-pressed)/180_000_000f,scale=1-(float)Math.sin(phase*Math.PI)*core.theme.wobble()/5000f;g.pose().pushMatrix();g.pose().translate(x+w/2f,y+h/2f);g.pose().scale(scale);g.pose().translate(-x-w/2f,-y-h/2f);}
  boolean expanding=motion&&core.modules.config().glass.hover&&hover>.001;if(expanding){float k=1+hover*.015f;g.pose().pushMatrix();g.pose().translate(x+w/2f,y+h/2f);g.pose().scale(k);g.pose().translate(-x-w/2f,-y-h/2f);}
  int bg=primary?GlassRender.blend(GlassRender.alpha(core.theme.accent(),.86),GlassRender.blend(core.theme.accent(),core.theme.text(),.25f),hover):on?GlassRender.blend(GlassRender.alpha(core.theme.accent(),.27),GlassRender.alpha(core.theme.accent(),.43),hover):GlassRender.blend(nav?0x00162234:0x8a162234,0xd02b405c,hover);bg=GlassRender.surface(bg,core.theme);
  if(sharedSelection)bg=GlassRender.blend(0x00162234,0x702b405c,hover);
  GlassRender.round(g,x,y,w,h,r,bg);if(!nav||on&&!sharedSelection||hover>.05)GlassRender.border(g,x,y,w,h,r,on?0x996aaaff:GlassRender.blend(0x29ccdeef,0x6bb4d9ff,hover));
  GlassRender.interaction(g,x,y,w,h,r,core.theme,interaction);
  if(isFocused()&&toggle)GlassRender.border(g,x,y,w,h,r,GlassRender.alpha(core.theme.accent(),.7));
  else if(isFocused())g.fill(x+10,y+h-3,x+w-10,y+h-2,GlassRender.alpha(core.theme.accent(),.85));
  boolean parallax=motion&&core.theme.effects().hover()&&(core.theme.effects().lift()>0||core.theme.effects().tilt()>0);if(parallax){float lift=(float)(Math.min(1.4,core.theme.effects().lift()*.017)*hover),tilt=(float)(core.theme.effects().tilt()*.012*interaction.near());g.pose().pushMatrix();g.pose().translate((interaction.x()-.5f)*tilt,-lift+(interaction.y()-.5f)*tilt);}
  int tx=x+(icon.isEmpty()?12:36),available=w-(tx-x)-12-(toggle?30:0),color=active?(primary?GlassRender.foreground(bg,core.theme):core.theme.text()):0xff75849b;
  if(!icon.isEmpty()){boolean only=iconOnly||getMessage().getString().isEmpty();int size=only?(h<32?18:24):16;GlassRender.icon(g,icon,only?x+(w-size)/2:x+12,y+(h-size)/2,size,on?0xff9ccfff:color);}
  if(iconOnly){if(over&&!getMessage().getString().isEmpty())UiTooltip.request(getMessage().getString(),mx,my);}
  else if(w<=32&&icon.isEmpty())UiText.centered(g,getMessage().getString(),x+w/2,y+(h-9)/2,color);
  else{UiText.draw(g,UiText.fit(getMessage().getString(),available),tx,y+(detail.isEmpty()?(h-10)/2:12),color);if(!detail.isEmpty())UiText.draw(g,UiText.fit(detail,available),tx,y+28,0xff9cafc6);}
  if(toggle){if(on)GlassRender.round(g,x+w-42,y+h/2-2,4,4,2,core.theme.accent());if(switchPosition<0||!motion)switchPosition=on?1:0;else switchPosition+=(float)(((on?1:0)-switchPosition)*(1-Math.exp(-dt*20)));int track=on?core.theme.accent():0xff4a586b;GlassRender.round(g,x+w-33,y+(h-13)/2,24,13,6,track);GlassRender.round(g,x+w-31+Math.round(switchPosition*11),y+(h-11)/2,11,11,6,0xfff1f6ff);}
  if(parallax)g.pose().popMatrix();if(expanding)g.pose().popMatrix();if(press)g.pose().popMatrix();if(popping)g.pose().popMatrix();
 }
 @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){if(active&&visible&&isMouseOver(event.x(),event.y()))GlassTransition.capture();boolean accepted=super.mouseClicked(event,twice);if(accepted)pressed=System.nanoTime();return accepted;}
}
