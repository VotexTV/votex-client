package dev.votex.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.votex.ui.WorkspaceLayout;
import dev.votex.ui.WorkspaceMotion;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Explicit device consent; no screen-open, hover or dismiss action grants permission. */
public final class VoiceConsentScreen extends Screen implements VotexMenuSurface {
 private final VotexClient core;private final Screen parent;private float scale;private int x,y,w,lw,lh;private final long opened=System.nanoTime();private WorkspaceMotion.Frame displayedMotion=new WorkspaceMotion.Frame(1,0,0);
 public VoiceConsentScreen(VotexClient core,Screen parent){super(Component.literal("Dein Mikrofon. Deine Entscheidung."));this.core=core;this.parent=parent;}
 @Override public boolean isPauseScreen(){return false;}
 @Override protected void init(){var layout=WorkspaceLayout.measure(width,height,minecraft.getWindow().getGuiScale());scale=layout.scale();lw=layout.logicalWidth();lh=layout.logicalHeight();w=Math.min(520,lw-32);x=(lw-w)/2;y=(lh-282)/2;
  addRenderableWidget(new GlassButton(core,x+24,y+177,w-48,40,"Mikrofon aktivieren und Voice öffnen","",()->false,b->{if(!VoiceMicGate.allowAndOpen(parent))rebuildWidgets();})).icon("voice").primary();
  addRenderableWidget(new GlassButton(core,x+24,y+227,w-48,32,"Später","",()->false,b->onClose()));
 }
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){if(core.modules.config().blurMenus&&core.theme.blur())extractBlurredBackground(g);g.fill(0,0,width,height,0x85050c16);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){displayedMotion=WorkspaceMotion.frame(System.nanoTime(),opened,0,VotexGlassDesignSystem.motion(core),false,lw,lh,VotexGlassDesignSystem.transitionMillis(core));g.pose().pushMatrix();g.pose().scale(scale);g.pose().translate(displayedMotion.x(),displayedMotion.y());g.pose().scale(displayedMotion.scale());GlassRender.panel(g,x,y,w,282,core.theme);GlassRender.icon(g,"voice",x+24,y+24,25,0xffa9d4ff);UiText.title(g,"Dein Mikrofon. Deine Entscheidung.",x+24,y+66,core.theme.text(),1.15f);String[] lines={"Erst mit deiner Zustimmung darf Voice dein Mikrofon öffnen.","Du bleibst zunächst stumm. Geräte und Sprechmodus wählst", "du anschließend in Voice. Die Freigabe kannst du jederzeit", "unter Votex → Einstellungen wieder ausschalten."};for(int i=0;i<lines.length;i++)UiText.draw(g,UiText.fit(lines[i],w-48),x+24,y+101+i*15,core.theme.muted());super.extractRenderState(g,(int)Math.round(localX(mx)),(int)Math.round(localY(my)),delta);g.pose().popMatrix();}
 private double localX(double x){return displayedMotion.localX(x/scale);}private double localY(double y){return displayedMotion.localY(y/scale);}
 private MouseButtonEvent local(MouseButtonEvent event){return new MouseButtonEvent(localX(event.x()),localY(event.y()),event.buttonInfo());}
 @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){return super.mouseClicked(local(event),twice);}
 @Override public boolean mouseReleased(MouseButtonEvent event){return super.mouseReleased(local(event));}
 @Override public void mouseMoved(double x,double y){super.mouseMoved(localX(x),localY(y));}
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
