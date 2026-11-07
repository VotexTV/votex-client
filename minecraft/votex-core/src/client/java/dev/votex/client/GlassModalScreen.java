package dev.votex.client;
import dev.votex.ui.WorkspaceMotion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
/** Shared modal material motion, with the exact inverse used by every pointer operation. */
public abstract class GlassModalScreen extends Screen implements VotexMenuSurface {
 private float density=1;protected final void useDensity(){density=(float)(1/Math.max(1,Math.min(2,net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale()/1.5)));width=Math.round(width/density);height=Math.round(height/density);}
 private final long opened=System.nanoTime();private final GlassTransition.Origin origin=GlassTransition.origin();private WorkspaceMotion.Frame frame=new WorkspaceMotion.Frame(1,0,0);
 protected GlassModalScreen(Component title){super(title);}
 public WorkspaceMotion.Frame visualFrame(){return frame;}
 @Override public final void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){var core=VotexClient.INSTANCE;boolean motion=core.modules.config().glass.motion(core.theme,core.modules.config().menuAnimations);double duration=700-core.modules.config().glass.speed*3;frame=!motion?new WorkspaceMotion.Frame(1,0,0):origin.valid()?GlassTransition.frame(opened,duration,origin,1,width*density/2.,height*density/2.):WorkspaceMotion.frame(System.nanoTime(),opened,0,true,false,Math.round(width*density),Math.round(height*density),duration);g.pose().pushMatrix();g.pose().translate(frame.x(),frame.y());g.pose().scale(frame.scale()*density);extractModal(g,(int)Math.round(frame.localX(x)/density),(int)Math.round(frame.localY(y)/density),d);g.pose().popMatrix();}
 protected abstract void extractModal(GuiGraphicsExtractor g,int x,int y,float d);
 protected final void extractWidgets(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);}
 private MouseButtonEvent point(MouseButtonEvent e){return new MouseButtonEvent(frame.localX(e.x())/density,frame.localY(e.y())/density,e.buttonInfo());}
 @Override public final boolean mouseClicked(MouseButtonEvent e,boolean twice){return modalClicked(point(e),twice);}
 protected boolean modalClicked(MouseButtonEvent e,boolean twice){return super.mouseClicked(e,twice);}
 @Override public final boolean mouseDragged(MouseButtonEvent e,double dx,double dy){return modalDragged(point(e),dx/frame.scale()/density,dy/frame.scale()/density);}
 protected boolean modalDragged(MouseButtonEvent e,double dx,double dy){return super.mouseDragged(e,dx,dy);}
 @Override public final boolean mouseReleased(MouseButtonEvent e){return modalReleased(point(e));}
 protected boolean modalReleased(MouseButtonEvent e){return super.mouseReleased(e);}
 @Override public final boolean mouseScrolled(double x,double y,double dx,double dy){return modalScrolled(frame.localX(x)/density,frame.localY(y)/density,dx,dy);}
 protected boolean modalScrolled(double x,double y,double dx,double dy){return super.mouseScrolled(x,y,dx,dy);}
}
