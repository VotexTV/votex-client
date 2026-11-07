package dev.votex.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SliderMotionTest {
 @Test void spring_response_is_frame_rate_independent(){var a=new DampedSpring(0);var b=new DampedSpring(0);for(int i=0;i<30;i++)a.step(1,1d/60);for(int i=0;i<72;i++)b.step(1,1d/144);assertEquals(a.value(),b.value(),1e-10);}
 @Test void dragging_reversal_and_release_are_bounded_and_settle(){var m=new SliderMotion();for(int i=0;i<90;i++){m.step(i<45?i/45d:(90-i)/45d,true,1d/120,true);assertTrue(m.scaleX()>=.97&&m.scaleX()<=1.20);assertTrue(m.scaleY()>=.91&&m.scaleY()<=1.10);}for(int i=0;i<300;i++)m.step(.5,false,1d/120,true);assertEquals(1,m.scaleX(),.0001);assertEquals(1,m.scaleY(),.0001);assertEquals(0,m.angle(),.0001);}
 @Test void endpoints_are_exact_and_disabled_motion_is_identity(){assertEquals(0,SliderMotion.valueAt(118,100,236));assertEquals(1,SliderMotion.valueAt(318,100,236));assertEquals(.5,SliderMotion.valueAt(218,100,236));assertEquals(0,SliderMotion.valueAt(-200,100,236));var m=new SliderMotion();m.step(.9,true,.016,false);assertEquals(1,m.scaleX());assertEquals(1,m.scaleY());}
}
