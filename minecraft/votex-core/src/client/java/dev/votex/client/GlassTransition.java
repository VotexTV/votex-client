package dev.votex.client;
import dev.votex.ui.WorkspaceMotion;
import net.minecraft.client.Minecraft;
/** Trigger is captured before navigation; only visual coordinates morph, input uses its inverse. */
public final class GlassTransition {
 private static long trigger;private static double x,y;
 public static void capture(){var mc=Minecraft.getInstance();var w=mc.getWindow();trigger=System.nanoTime();x=mc.mouseHandler.xpos()*w.getGuiScaledWidth()/Math.max(1,w.getScreenWidth());y=mc.mouseHandler.ypos()*w.getGuiScaledHeight()/Math.max(1,w.getScreenHeight());}
 public record Origin(double x,double y,boolean valid){}
 public static Origin origin(){return new Origin(x,y,System.nanoTime()-trigger<250_000_000L);}
 public static WorkspaceMotion.Frame frame(long opened,double millis,Origin origin,double uiScale,double cx,double cy){double t=Math.clamp((System.nanoTime()-opened)/(millis*1e6),0,1),e=1-Math.pow(1-t,4),s=.32+.68*e;return new WorkspaceMotion.Frame((float)s,(float)((origin.x()/uiScale*(1-e)+cx*e)-cx*s),(float)((origin.y()/uiScale*(1-e)+cy*e)-cy*s));}
}
