package dev.votex.client;
import dev.votex.ui.HoverMotion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Real GPU image comparison fixture; never distributed. */
final class GlassHoverProbeScreen extends Screen implements VotexMenuSurface {
 final HoverMotion motion=new HoverMotion();boolean lighting;int pointerX=124,pointerY=94;
 GlassHoverProbeScreen(){super(Component.literal("Rounded hover regression"));}
 public boolean isPauseScreen(){return false;}
 public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){}
 public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){var core=VotexClient.INSTANCE;GlassRender.round(g,100,70,180,48,24,0xb8162234);motion.step(1d/60,true,true,pointerX,pointerY,100,70,180,48,core.theme,true);if(lighting)GlassRender.interaction(g,100,70,180,48,24,core.theme,motion);}
}
