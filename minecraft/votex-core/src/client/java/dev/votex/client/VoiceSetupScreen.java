package dev.votex.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.votex.ui.WorkspaceLayout;
import dev.votex.ui.WorkspaceMotion;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Inactive/unsupported Voice always has an actionable native route with restart staging. */
public final class VoiceSetupScreen extends Screen implements VotexMenuSurface {
 private final VotexClient core;private final Screen parent;private float scale;private int x,y,w,lw,lh;private final long opened=System.nanoTime();private WorkspaceMotion.Frame displayedMotion=new WorkspaceMotion.Frame(1,0,0);
 public VoiceSetupScreen(VotexClient core,Screen parent){super(Component.literal("Voice einrichten"));this.core=core;this.parent=parent;}
 @Override public boolean isPauseScreen(){return false;}
 @Override protected void init(){var layout=WorkspaceLayout.measure(width,height,minecraft.getWindow().getGuiScale());scale=layout.scale();lw=layout.logicalWidth();lh=layout.logicalHeight();w=Math.min(520,lw-32);x=(lw-w)/2;y=(lh-282)/2;addRenderableWidget(new GlassButton(core,x+24,y+177,w-48,40,"Voice in Mods & Shader suchen","",()->false,b->minecraft.gui.setScreen(ModLibraryScreen.openSearch(core,this,"Simple Voice Chat")))).icon("search").primary();addRenderableWidget(new GlassButton(core,x+24,y+227,w-48,32,"Zurück","",()->false,b->onClose()));}
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float d){if(core.modules.config().blurMenus&&core.theme.blur())extractBlurredBackground(g);g.fill(0,0,width,height,0x85050c16);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){displayedMotion=WorkspaceMotion.frame(System.nanoTime(),opened,0,VotexGlassDesignSystem.motion(core),false,lw,lh,VotexGlassDesignSystem.transitionMillis(core));g.pose().pushMatrix();g.pose().scale(scale);g.pose().translate(displayedMotion.x(),displayedMotion.y());g.pose().scale(displayedMotion.scale());GlassRender.panel(g,x,y,w,282,core.theme);GlassRender.icon(g,"voice",x+24,y+24,25,0xffa9d4ff);UiText.title(g,"Voice einrichten",x+24,y+65,core.theme.text(),1.5f);String[] lines=FabricLoader.getInstance().isModLoaded("voicechat")?new String[]{"Diese Voice-Version unterstützt die Votex-Mikrofonfreigabe", "noch nicht. Verwende ihre eigene Voice-Taste oder verwalte", "den Mod in deiner Bibliothek. Deine Installation bleibt", "unverändert; Votex behauptet hier keine Mikrofon-Sperre."}:new String[]{"Simple Voice Chat ist in diesem Spielstart nicht aktiv.","Er kann fehlen, deaktiviert sein oder im sicheren Start fehlen.","Suche die passende Version in Mods & Shader. Du bestätigst", "jede Änderung selbst; sie gilt erst nach dem Spielneustart."};for(int i=0;i<lines.length;i++)UiText.draw(g,UiText.fit(lines[i],w-48),x+24,y+101+i*15,core.theme.muted());super.extractRenderState(g,(int)Math.round(localX(mx)),(int)Math.round(localY(my)),d);g.pose().popMatrix();}
 private double localX(double x){return displayedMotion.localX(x/scale);}private double localY(double y){return displayedMotion.localY(y/scale);}
 private MouseButtonEvent local(MouseButtonEvent e){return new MouseButtonEvent(localX(e.x()),localY(e.y()),e.buttonInfo());}
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){return super.mouseClicked(local(e),twice);}
 @Override public boolean mouseReleased(MouseButtonEvent e){return super.mouseReleased(local(e));}
 @Override public void mouseMoved(double x,double y){super.mouseMoved(localX(x),localY(y));}
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
