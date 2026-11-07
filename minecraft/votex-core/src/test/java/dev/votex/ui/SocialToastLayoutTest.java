package dev.votex.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SocialToastLayoutTest {
 @Test void invitation_stays_readable_bounded_and_above_native_hotbar(){
  for(int[] size:new int[][]{{768,480},{1280,720},{1920,1080},{3840,2160}})for(int gui=1;gui<=4;gui++)for(int lines=1;lines<=3;lines++){
   int gw=(int)Math.ceil(size[0]/(double)gui),gh=(int)Math.ceil(size[1]/(double)gui);var l=SocialToastLayout.measure(gw,gh,gui,lines);
   assertTrue(l.scale()*gui*11>=14);assertTrue(l.x()>=12);assertTrue(l.x()+l.width()<=l.viewportWidth()-12);
   assertTrue(l.y()>=12);assertTrue((l.y()+l.height())*l.scale()<=gh-58+.01);assertTrue(l.y()+l.height()<=l.viewportHeight());
  }
 }
}
