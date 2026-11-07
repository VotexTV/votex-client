package dev.votex.client;
import dev.votex.config.GlassConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.IntConsumer;

/** Local material editor. It never rewrites the external launcher's design or profile file. */
public final class GlassDesignScreen extends GlassModalScreen {
 private final VotexClient core;private final Screen parent;private final List<AbstractWidget> flow=new ArrayList<>(),fixed=new ArrayList<>();
 private float scale;private int left,top,w,h,scroll,maxScroll,viewportTop,viewportBottom;
 public GlassDesignScreen(VotexClient core,Screen parent){super(Component.literal("Votex Glass Studio"));this.core=core;this.parent=parent;}
 @Override public boolean isPauseScreen(){return false;}
 private GlassConfig config(){return core.modules.config().glass;}
 private GlassButton button(int x,int y,int w,String title,Runnable action){return new GlassButton(core,x,y,w,32,title,"",()->false,b->action.run());}
 private void save(){core.engine.saveModules(core.modules);}
 @Override protected void init(){
  flow.clear();fixed.clear();scale=(float)(1/Math.max(1,Math.min(2,minecraft.getWindow().getGuiScale()/1.5)));int lw=Math.round(width/scale),lh=Math.round(height/scale);w=Math.min(760,lw-24);h=lh-24;left=(lw-w)/2;top=12;viewportTop=top+118;viewportBottom=top+h-48;maxScroll=Math.max(0,viewportTop+438-viewportBottom);scroll=Math.min(scroll,maxScroll);
  int bw=(w-48)/3;String[] names={"Crystal Clear","Liquid Bubble","Minimal Glass"};var presets=GlassConfig.Preset.values();
  for(int i=0;i<3;i++){var p=presets[i];fixed.add(button(left+16+i*(bw+8),top+44,bw,names[i],()->{config().preset(p);save();rebuildWidgets();}));}
  fixed.add(button(left+16,top+82,(w-40)/2,"Qualität · "+config().quality,()->{var v=GlassConfig.Quality.values();config().quality=v[(config().quality.ordinal()+1)%v.length];save();rebuildWidgets();}));
  fixed.add(button(left+24+(w-40)/2,top+82,(w-40)/2,"Modus · "+config().mode,()->{var v=GlassConfig.Mode.values();config().mode=v[(config().mode.ordinal()+1)%v.length];save();rebuildWidgets();}));
  int col=(w-40)/2,yy=viewportTop+4-scroll;var c=config();
  slider(0,yy,col,"Glasintensität",c.intensity,v->c.intensity=v);slider(1,yy,col,"Blur-Stärke",c.blur,v->c.blur=v);
  slider(0,yy+56,col,"Lichtbrechung",c.refraction,v->c.refraction=v);slider(1,yy+56,col,"Reflexion",c.reflection,v->c.reflection=v);
  slider(0,yy+112,col,"Rundung",c.roundness,v->c.roundness=v);slider(1,yy+112,col,"Materialdicke",c.thickness,v->c.thickness=v);
  slider(0,yy+168,col,"Transparenz",c.transparency,v->c.transparency=v);slider(1,yy+168,col,"Animationstempo",c.speed,v->c.speed=v);
  flow.add(new GlassButton(core,left+16,yy+224,col,38,"Hover-Effekte","",()->c.hover,b->{c.hover=!c.hover;save();}).toggle());
  flow.add(new GlassButton(core,left+24+col,yy+224,col,38,"Bewegung reduzieren","",()->c.reduceMotion,b->{c.reduceMotion=!c.reduceMotion;save();}).toggle());
  flow.add(new GlassButton(core,left+16,yy+272,col,38,"Transparenz reduzieren","",()->c.reduceTransparency,b->{c.reduceTransparency=!c.reduceTransparency;save();}).toggle());
  flow.add(button(left+24+col,yy+272,col,"Tönung · "+c.tint,()->{String[] colors={"#b9d9ef","#d9c9ed","#bce8da","#eed9b9","#cdd9f2"};int i=Arrays.asList(colors).indexOf(c.tint);c.tint=colors[(i+1)%colors.length];save();rebuildWidgets();}));
  // Full RGB editing: each channel retains the other two, including arbitrary saved colors.
  int tint=c.color();slider(0,yy+320,col,"Tönung Rot",(tint>>16&255)*100/255,v->channel(16,v));slider(1,yy+320,col,"Tönung Grün",(tint>>8&255)*100/255,v->channel(8,v));slider(0,yy+376,col,"Tönung Blau",(tint&255)*100/255,v->channel(0,v));
  flow.add(button(left+24+col,yy+376,col,"HUD-Layouts",()->{save();minecraft.gui.setScreen(new HudProfilesScreen(core,this));}));
  maxScroll=Math.max(0,viewportTop+438-viewportBottom);scroll=Math.min(scroll,maxScroll);
  fixed.add(button(left+w-180,top+h-40,164,"Speichern & zurück",this::onClose));
  for(var widget:flow)addWidget(widget);for(var widget:fixed)addWidget(widget);
 }
 private void channel(int shift,int value){int color=config().color();int c=(color&~(255<<shift))|((int)Math.round(value*2.55)<<shift);config().tint=String.format(Locale.ROOT,"#%06x",c&0xffffff);}
 private void slider(int column,int y,int width,String label,int value,IntConsumer setter){flow.add(new GlassSlider(left+16+column*(width+8),y,width,label,value,setter));}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){}
 @Override protected void extractModal(GuiGraphicsExtractor g,int mx,int my,float d){g.pose().pushMatrix();g.pose().scale(scale);int x=Math.round(mx/scale),y=Math.round(my/scale);GlassRender.panel(g,left,top,w,h,core.theme);UiText.title(g,"Votex Glass Studio",left+18,top+16,core.theme.text(),1.4f);g.enableScissor(left+8,viewportTop,left+w-8,viewportBottom);for(var widget:flow)widget.extractRenderState(g,x,y,d);g.disableScissor();for(var widget:fixed)widget.extractRenderState(g,x,y,d);UiText.draw(g,"Live-Vorschau · lokal im Spiel gespeichert",left+16,top+h-27,core.theme.muted());g.pose().popMatrix();}
 private MouseButtonEvent local(MouseButtonEvent e){return new MouseButtonEvent(e.x()/scale,e.y()/scale,e.buttonInfo());}
 @Override public boolean modalClicked(MouseButtonEvent e,boolean twice){var p=local(e);for(var widget:fixed)if(widget.mouseClicked(p,twice)){setFocused(widget);return true;}if(p.y()>=viewportTop&&p.y()<viewportBottom)for(var widget:flow)if(widget.mouseClicked(p,twice)){setFocused(widget);setDragging(true);return true;}return false;}
 @Override public boolean modalDragged(MouseButtonEvent e,double dx,double dy){return super.modalDragged(local(e),dx/scale,dy/scale);}
 @Override public boolean modalReleased(MouseButtonEvent e){boolean result=super.modalReleased(local(e));save();return result;}
 @Override public boolean modalScrolled(double x,double y,double dx,double dy){scroll=Math.clamp(scroll-(int)(dy*40),0,maxScroll);rebuildWidgets();return true;}
 @Override public void onClose(){save();minecraft.gui.setScreen(parent);}
 @Override public void removed(){save();}
}
