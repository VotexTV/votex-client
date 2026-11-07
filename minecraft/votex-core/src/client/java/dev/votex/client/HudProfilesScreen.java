package dev.votex.client;
import dev.votex.config.HudLayouts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Eight named local HUD slots, distinct from game installation profiles. */
public final class HudProfilesScreen extends GlassModalScreen {
 private final VotexClient core;private final Screen parent;private int selected=1,x,y,w;private String status="Wähle ein Layout. Speichern überschreibt nur diesen Platz.";
 public HudProfilesScreen(VotexClient c,Screen parent){super(Component.literal("HUD-Layouts"));core=c;this.parent=parent;}
 @Override public boolean isPauseScreen(){return false;}
 @Override protected void init(){useDensity();w=Math.min(440,width-24);x=(width-w)/2;y=Math.max(12,(height-258)/2);int col=(w-40)/2;for(int i=1;i<=8;i++){final int slot=i;String name="Layout "+i;addRenderableWidget(new GlassButton(core,x+16+(i-1)%2*(col+8),y+42+(i-1)/2*33,col,28,name, "",()->selected==slot,b->{selected=slot;rebuildWidgets();}).icon(core.modules.config().layouts.containsKey(name)?"check":"hud"));}int bw=(w-48)/3;addRenderableWidget(new GlassButton(core,x+16,y+186,bw,32,"Speichern","",()->false,b->{HudLayouts.save(core.modules.config(),"Layout "+selected);core.engine.saveModules(core.modules);status="Layout "+selected+" gespeichert";rebuildWidgets();}));var load=new GlassButton(core,x+24+bw,y+186,bw,32,"Laden","",()->false,b->{HudLayouts.load(core.modules.config(),"Layout "+selected);core.engine.saveModules(core.modules);status="Layout "+selected+" geladen";});load.active=core.modules.config().layouts.containsKey("Layout "+selected);addRenderableWidget(load);addRenderableWidget(new GlassButton(core,x+32+2*bw,y+186,bw,32,"Zurück","",()->false,b->onClose()));}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){}
 @Override protected void extractModal(GuiGraphicsExtractor g,int mx,int my,float d){GlassRender.panel(g,x,y,w,248,core.theme);UiText.title(g,"Deine HUD-Layouts",x+16,y+16,core.theme.text(),1.2f);extractWidgets(g,mx,my,d);UiText.draw(g,UiText.fit(status,w-32),x+16,y+230,core.theme.muted());}
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
