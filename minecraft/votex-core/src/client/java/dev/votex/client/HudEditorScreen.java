package dev.votex.client;
import dev.votex.config.ModuleRegistry;
import dev.votex.ui.HudEditorLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;
/** Drag individual elements; scale, surface opacity and accent persist per element. */
public final class HudEditorScreen extends Screen implements VotexMenuSurface {
 private final VotexClient core;private int dragging=-1,selected=-1;private double offsetX,offsetY;private int guideX=-1,guideY=-1;private boolean snap=true,dockAbove;private HudEditorLayout layout;
 public HudEditorScreen(VotexClient core){super(Component.literal("Votex HUD bearbeiten"));this.core=core;}
 @Override public boolean isPauseScreen(){return false;}
 @Override protected void init(){
  layout=HudEditorLayout.measure(width,height,minecraft.getWindow().getGuiScale());
  if(dockAbove)layout=layout.atTop();
  if(selected>=0&&!enabled(selected))selected=-1;
  int x=layout.x()+12,y=layout.y(),space=layout.width()-24;
  int headerControl=26,profilesWidth=Math.min(86,space/4);
  int nav=x+space-profilesWidth-2*headerControl-12;
  control(nav,y+7,headerControl,24,"‹","",b->cycle(-1));
  control(nav+headerControl+4,y+7,headerControl,24,"›","",b->cycle(1));
  control(x+space-profilesWidth,y+7,profilesWidth,24,"Layouts","",b->minecraft.gui.setScreen(new HudProfilesScreen(core,this)));
  int column=(space-12)/3;
  control(x,y+83,column,29,"Ausrichten","",b->snap=!snap,()->snap).toggle();
  control(x+column+6,y+83,column,29,dockAbove?"Leiste unten":"Leiste oben","",b->{dockAbove=!dockAbove;rebuildWidgets();});
  control(x+2*(column+6),y+83,space-2*(column+6),29,"Speichern","",b->onClose()).primary();
  if(selected<0)return;
  int unit=(space-20)/5;
  control(x,y+40,unit,30,"−","",b->adjustScale(-10));
  control(x+unit+5,y+40,unit,30,"+","",b->adjustScale(10));
  control(x+2*(unit+5),y+40,unit,30,"Stil","",b->minecraft.gui.setScreen(new ModuleSettingsScreen(core,this,ModuleRegistry.HUD_MODULES.get(selected))));
  control(x+3*(unit+5),y+40,unit,30,"Mitte","",b->place(selected,(width-size(selected,true))/2,(height-size(selected,false))/2));
  control(x+4*(unit+5),y+40,space-4*(unit+5),30,"Reset","",b->core.modules.config().positions.remove(ModuleRegistry.HUD_MODULES.get(selected).id()));
 }
 private GlassButton control(int x,int y,int w,int h,String label,String hint,net.minecraft.client.gui.components.Button.OnPress action){
  return control(x,y,w,h,label,hint,action,()->false);
 }
 private GlassButton control(int x,int y,int w,int h,String label,String hint,net.minecraft.client.gui.components.Button.OnPress action,java.util.function.BooleanSupplier state){
  return addRenderableWidget(new GlassButton(core,x,y,w,h,label,hint,state,action));
 }
 private boolean enabled(int index){return core.modules.enabled(ModuleRegistry.HUD_MODULES.get(index).id());}
 private void cycle(int direction){
  int count=ModuleRegistry.HUD_MODULES.size(),start=selected<0?(direction>0?-1:0):selected;
  for(int step=1;step<=count;step++){
   int index=Math.floorMod(start+step*direction,count);
   if(enabled(index)){
    selected=index;rebuildWidgets();
    return;
   }
  }
 }
 private ModuleRegistry.Appearance appearance(){return core.modules.config().appearance.computeIfAbsent(ModuleRegistry.HUD_MODULES.get(selected).id(),id->new ModuleRegistry.Appearance());}
 private void adjustScale(int delta){var a=appearance();a.scale=Math.clamp(a.scale+delta,50,200);}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
  g.fill(0,0,width,height,0x28060b12);core.hud.render(g,true);g.nextStratum();
  if(selected>=0&&enabled(selected)){
   int left=renderedPosition(selected,true),top=renderedPosition(selected,false);
   int right=left+size(selected,true),bottom=top+size(selected,false);
   int color=core.theme.accent();
   g.fill(left,top,right,top+1,color);g.fill(left,bottom-1,right,bottom,color);
   g.fill(left,top,left+1,bottom,color);g.fill(right-1,top,right,bottom,color);
  }
  if(guideX>=0)g.fill(guideX,0,guideX+1,height,0xffff444f);
  if(guideY>=0)g.fill(0,guideY,width,guideY+1,0xffff444f);
  g.nextStratum();g.pose().pushMatrix();g.pose().scale(layout.scale());
  GlassRender.popover(g,layout.x(),layout.y(),layout.width(),layout.height(),core.theme);
  String title=selected<0?"Anzeige auswählen":ModuleRegistry.HUD_MODULES.get(selected).name()+" · "+appearance().scale+" %";
  UiText.draw(g,UiText.fit(title,layout.width()-184),layout.x()+12,layout.y()+14,core.theme.text());
  if(selected<0)UiText.draw(g,UiText.fit("Anklicken oder mit ‹ und › wählen. Pfeiltasten verschieben.",layout.width()-24),layout.x()+12,layout.y()+48,core.theme.muted());
  super.extractRenderState(g,Math.round(x/layout.scale()),Math.round(y/layout.scale()),d);g.pose().popMatrix();
 }
 private MouseButtonEvent local(MouseButtonEvent e){return new MouseButtonEvent(e.x()/layout.scale(),e.y()/layout.scale(),e.buttonInfo());}
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){if(super.mouseClicked(local(e),twice))return true;if(layout.contains(e.x(),e.y()))return true;if(e.button()==0)for(int i=ModuleRegistry.HUD_MODULES.size()-1;i>=0;i--){if(!core.modules.enabled(ModuleRegistry.HUD_MODULES.get(i).id()))continue;int x=renderedPosition(i,true),y=renderedPosition(i,false);if(e.x()>=x&&e.x()<x+size(i,true)&&e.y()>=y&&e.y()<y+size(i,false)){dragging=i;selected=i;offsetX=e.x()-x;offsetY=e.y()-y;rebuildWidgets();return true;}}return false;}
 private record Alignment(int position,int guide){}
 private int size(int index,boolean horizontal){return horizontal?Math.min(core.hud.width(index),Math.max(1,width-8)):core.hud.height(index);}
 private int limit(int index,boolean horizontal){return Math.max(4,(horizontal?width:height)-size(index,horizontal)-4);}
 private int renderedPosition(int index,boolean horizontal){var p=core.hud.position(index);return Math.clamp(horizontal?p.x:p.y,4,limit(index,horizontal));}
 private Alignment align(int position,boolean horizontal){
  Alignment best=new Alignment(position,-1);
  int extent=size(dragging,horizontal),distance=5;
  for(int i=0;i<ModuleRegistry.HUD_MODULES.size();i++){
   if(i==dragging||!core.modules.enabled(ModuleRegistry.HUD_MODULES.get(i).id()))continue;
   int other=renderedPosition(i,horizontal),otherExtent=size(i,horizontal);
   for(int anchor=0;anchor<3;anchor++){
    int guide=other+otherExtent*anchor/2,target=guide-extent*anchor/2;
    int delta=Math.abs(target-position);
    if(delta<distance&&target>=4&&target<=limit(dragging,horizontal)){
     best=new Alignment(target,guide);
     distance=delta;
    }
   }
  }
  return best;
 }
 private void place(int index,int x,int y){
  var position=new ModuleRegistry.Position(Math.clamp(x,4,limit(index,true)),Math.clamp(y,4,limit(index,false)));
  position.viewWidth=width;position.viewHeight=height;
  core.modules.config().positions.put(ModuleRegistry.HUD_MODULES.get(index).id(),position);
 }
 @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
  if(dragging<0)return super.mouseDragged(local(e),dx/layout.scale(),dy/layout.scale());
  int x=(int)Math.round(e.x()-offsetX),y=(int)Math.round(e.y()-offsetY);
  var horizontal=snap?align(x,true):new Alignment(x,-1);
  var vertical=snap?align(y,false):new Alignment(y,-1);
  guideX=horizontal.guide();guideY=vertical.guide();
  place(dragging,horizontal.position(),vertical.position());
  return true;
 }
 @Override public boolean mouseReleased(MouseButtonEvent e){
  if(dragging<0)return super.mouseReleased(local(e));
  dragging=-1;guideX=guideY=-1;
  return true;
 }
 @Override public boolean keyPressed(KeyEvent event){
  if(dragging<0&&(event.key()==GLFW.GLFW_KEY_PAGE_UP||event.key()==GLFW.GLFW_KEY_PAGE_DOWN)){
   cycle(event.key()==GLFW.GLFW_KEY_PAGE_UP?-1:1);
   return true;
  }
  if(selected>=0&&dragging<0){
   int dx=0,dy=0;
   switch(event.key()){
    case GLFW.GLFW_KEY_LEFT -> dx=-1;
    case GLFW.GLFW_KEY_RIGHT -> dx=1;
    case GLFW.GLFW_KEY_UP -> dy=-1;
    case GLFW.GLFW_KEY_DOWN -> dy=1;
    default -> {return super.keyPressed(event);}
   }
   place(selected,renderedPosition(selected,true)+dx,renderedPosition(selected,false)+dy);
   return true;
  }
  return super.keyPressed(event);
 }
 @Override public void onClose(){core.engine.saveModules(core.modules);minecraft.gui.setScreen(new VotexScreen(core,"MODULES"));}
}
