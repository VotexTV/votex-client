package dev.votex.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Fixed geometry over the actual world, used only for pixel comparisons of real GPU effects. */
final class GlassProbeScreen extends Screen implements VotexMenuSurface {
 GlassProbeScreen(){super(Component.literal("Glass GPU calibration"));}
 public boolean isPauseScreen(){return false;}
 public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){}
 public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){GlassRender.panel(g,40,35,width-80,height-90,VotexClient.INSTANCE.theme);}
}
