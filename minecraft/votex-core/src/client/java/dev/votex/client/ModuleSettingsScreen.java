package dev.votex.client;
import dev.votex.config.ModuleRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Immediate Votex features only. Fabric JAR lifecycle remains in ModLibraryScreen. */
public final class ModuleSettingsScreen extends GlassModalScreen {
 private final VotexClient core;private final Screen parent;private final ModuleRegistry.Module module;private int x,y,w;
 public ModuleSettingsScreen(VotexClient core,Screen parent,ModuleRegistry.Module module){super(Component.literal(module.name()));this.core=core;this.parent=parent;this.module=module;}
 @Override public boolean isPauseScreen(){return false;}
 @Override protected void init(){useDensity();w=Math.min(420,width-24);x=(width-w)/2;y=Math.max(8,(height-278)/2);addRenderableWidget(new GlassButton(core,x+16,y+55,w-32,32,"Sofort aktiv","",()->core.modules.enabled(module.id()),b->{try{core.modules.toggle(module.id());}catch(Exception e){core.error("Modul konnte nicht gespeichert werden",e);}}).toggle());
  if(module.category().equals("HUD")){var a=core.modules.config().appearance.computeIfAbsent(module.id(),id->new ModuleRegistry.Appearance());addRenderableWidget(new GlassSlider(x+16,y+98,w-32,"Transparenz",100-a.background,v->a.background=100-v));addRenderableWidget(new GlassSlider(x+16,y+151,w-32,"Glasintensität",a.glassIntensity,v->a.glassIntensity=v));addRenderableWidget(new GlassButton(core,x+16,y+205,(w-40)/2,30,"Größe −","",()->false,b->{a.scale=Math.max(50,a.scale-10);}));addRenderableWidget(new GlassButton(core,x+24+(w-40)/2,y+205,(w-40)/2,30,"Größe +","",()->false,b->{a.scale=Math.min(200,a.scale+10);}));}
  var settings=core.modules.config();
  if(module.id().equals("light")){
   int radius=Math.clamp(settings.lightOverlayRadius,4,12);
   addRenderableWidget(new GlassButton(core,x+16,y+98,w-32,40,"Reichweite: "+radius+" Blöcke","Klicken für 4, 8 oder 12 Blöcke",()->false,b->{settings.lightOverlayRadius=radius>=12?4:radius+4;rebuildWidgets();}));
   addRenderableWidget(new GlassButton(core,x+16,y+149,w-32,39,"Nur mögliche Spawnstellen","Grüne Felder ausblenden",()->settings.lightRisksOnly,b->settings.lightRisksOnly=!settings.lightRisksOnly).toggle());
  }
  if(module.id().equals("item_physics")){
   addRenderableWidget(new GlassButton(core,x+16,y+98,w-32,42,"Drehung im Flug","Bodenpose bleibt aktiv",()->settings.itemAirTumble,b->settings.itemAirTumble=!settings.itemAirTumble).toggle());
  }
  addRenderableWidget(new GlassButton(core,x+16,y+244,w-32,28,"Speichern & zurück","",()->false,b->onClose()));}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){}
 @Override protected void extractModal(GuiGraphicsExtractor g,int mx,int my,float d){GlassRender.panel(g,x,y,w,282,core.theme);UiText.title(g,module.name(),x+16,y+14,core.theme.text(),1.3f);UiText.draw(g,UiText.fit(module.description(),w-32),x+16,y+35,core.theme.muted());extractWidgets(g,mx,my,d);}
 @Override public void onClose(){core.engine.saveModules(core.modules);minecraft.gui.setScreen(parent);}
 @Override public void removed(){core.engine.saveModules(core.modules);}
}
