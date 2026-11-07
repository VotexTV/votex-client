package dev.votex.ui;
import dev.votex.config.ThemeConfig;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class HoverMotionTest {
 @TempDir Path dir;
 @Test void light_position_tracks_abrupt_cursor_moves_on_the_same_frame()throws Exception{var t=theme("\"proximityEffect\":true,\"proximity\":100,\"speed\":0");var m=new HoverMotion();m.step(1d/240,true,true,8,54,0,0,160,60,t,true);assertEquals(.05,m.x(),.000001);assertEquals(.9,m.y(),.000001);m.step(1d/240,true,true,152,6,0,0,160,60,t,true);assertEquals(.95,m.x(),.000001);assertEquals(.1,m.y(),.000001);}
 private ThemeConfig theme(String fields)throws Exception{Path file=dir.resolve("theme.json");Files.writeString(file,"{\"schemaVersion\":1,"+fields+"}");return ThemeConfig.read(file);}
 private void step(HoverMotion m,ThemeConfig t,double dt,boolean over){m.step(dt,over,true,80,30,0,0,160,60,t,true);}
 @Test void has_the_same_response_at_different_frame_rates()throws Exception{
  var t=theme("\"proximityEffect\":true,\"proximity\":90,\"speed\":45,\"stiffness\":42,\"damping\":55");var a=new HoverMotion();var b=new HoverMotion();
  for(int i=0;i<60;i++)step(a,t,1d/60,true);for(int i=0;i<144;i++)step(b,t,1d/144,true);
  assertEquals(a.hover(),b.hover(),.00001);assertEquals(a.near(),b.near(),.00001);assertTrue(a.hover()>.99);
  for(int i=0;i<60;i++)step(a,t,1d/60,false);for(int i=0;i<144;i++)step(b,t,1d/144,false);assertEquals(a.hover(),b.hover(),.00001);assertEquals(0,a.hover(),.001);
 }
 @Test void reversal_remains_continuous_and_bounded()throws Exception{
  var t=theme("\"proximityEffect\":true,\"proximity\":100");var m=new HoverMotion();float previous=0;
  for(int i=0;i<1200;i++){step(m,t,1d/240,i%140<70);assertTrue(m.hover()>=0&&m.hover()<=1);assertTrue(Math.abs(m.hover()-previous)<.07);previous=m.hover();}
 }
 @Test void live_off_switches_clear_stale_hover_and_mouse_light_immediately()throws Exception{
  var m=new HoverMotion();var on=theme("\"proximityEffect\":true,\"proximity\":90");for(int i=0;i<60;i++)step(m,on,1d/60,true);assertTrue(m.near()>.9);
  step(m,theme("\"hover\":false,\"proximityEffect\":true,\"proximity\":90"),1d/60,true);assertEquals(0,m.hover());assertEquals(0,m.near());
  var reduced=theme("\"reducedMotion\":true,\"proximityEffect\":true,\"proximity\":90");step(m,reduced,1d/60,true);assertEquals(1,m.hover());assertEquals(0,m.near());assertFalse(reduced.motion());
 }
 @Test void zero_wobble_does_not_disable_the_independent_hover_transition()throws Exception{var t=theme("\"wobble\":0");var m=new HoverMotion();step(m,t,1d/60,true);assertTrue(m.hover()>0&&m.hover()<1);}
 @Test void imported_material_and_motion_controls_are_retained_and_validated()throws Exception{
  var t=theme("\"glassEffect\":false,\"hover\":false,\"glowEffect\":false,\"reflectionEffect\":false,\"wobbleEffect\":false,\"speed\":82,\"stiffness\":71,\"damping\":44,\"lift\":90,\"tilt\":55,\"shadow\":0");
  assertFalse(t.blur());assertEquals(250,t.panel()>>>24);assertEquals(0,t.effects().glowAmount());assertEquals(0,t.effects().reflectionAmount());assertEquals(82,t.effects().speed());assertEquals(55,t.effects().tilt());assertEquals(t.effects(),t.withPanel(0xff111111).effects());
  assertThrows(Exception.class,()->theme("\"hover\":\"false\""));assertThrows(Exception.class,()->theme("\"proximity\":101"));assertThrows(Exception.class,()->theme("\"glassEffect\":false,\"transparency\":101"));
 }
}
