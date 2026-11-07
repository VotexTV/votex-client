package dev.votex.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkspaceMotionTest {
 @Test void visible_edges_inverse_to_the_same_button_during_every_phase(){
  for(int width:new int[]{590,1000,1500})for(float density:new float[]{.65f,1,1.7f})for(int ms:new int[]{0,12,60,140,280,519,700}){
   var frame=WorkspaceMotion.frame(1_000_000_000L+ms*1_000_000L,1_000_000_000L,0,true,false,width,800);
   for(double x:new double[]{8,100,180.25,width-9})for(double y:new double[]{8,60.25,700}){
    assertEquals(x,frame.localX(frame.screenX(x)*density/density),.0001);
    assertEquals(y,frame.localY(frame.screenY(y)*density/density),.0001);
   }
  }
 }
 @Test void disabled_motion_is_identity_and_future_clock_is_clamped(){
  assertEquals(new WorkspaceMotion.Frame(1,0,0),WorkspaceMotion.frame(50,100,0,false,false,1000,800));
  var first=WorkspaceMotion.frame(50,100,0,true,false,1000,800);
  assertEquals(.935f,first.scale(),.00001);
  var closing=WorkspaceMotion.frame(2_000_000_000L,0,1_910_000_000L,true,false,1000,800);
  assertEquals(.975f,closing.scale(),.00001);
  assertEquals(100,closing.localX(closing.screenX(100)),.00001);
 }
}
