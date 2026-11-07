package dev.votex.ui;

/** A readable elliptical wheel keeps every slot on screen in compact windows. */
public record EmoteWheelLayout(float scale,int width,int height,int centerX,int centerY,int cardWidth,int cardHeight,int radiusX,int radiusY) {
 public static EmoteWheelLayout measure(int guiWidth,int guiHeight,double guiScale){var ui=WorkspaceLayout.measure(guiWidth,guiHeight,guiScale);boolean compact=ui.logicalHeight()<480;int cw=compact?106:132,ch=compact?82:119;return new EmoteWheelLayout(ui.scale(),ui.logicalWidth(),ui.logicalHeight(),ui.logicalWidth()/2,(ui.logicalHeight()-28)/2,cw,ch,Math.min(225,(ui.logicalWidth()-cw-32)/2),Math.min(196,(ui.logicalHeight()-ch-50)/2));}
 public int x(int slot,float progress){return centerX+(int)Math.round(Math.cos(slot*Math.PI/4-Math.PI/2)*radiusX*progress)-cardWidth/2;}
 public int y(int slot,float progress){return centerY+(int)Math.round(Math.sin(slot*Math.PI/4-Math.PI/2)*radiusY*progress)-cardHeight/2;}
 public int labelY(){return Math.min(centerY+75,centerY+radiusY-cardHeight/2-30);}
 public int selected(double guiX,double guiY){double dx=(guiX/scale-centerX)/radiusX,dy=(guiY/scale-centerY)/radiusY,distance=Math.hypot(dx,dy);return distance>.45&&distance<1.4?Math.floorMod((int)Math.floor((Math.atan2(dy,dx)+Math.PI/2+Math.PI/8)/(Math.PI/4)),8):-1;}
}
