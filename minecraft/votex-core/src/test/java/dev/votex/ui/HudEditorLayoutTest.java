package dev.votex.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HudEditorLayoutTest {
 @Test void tools_keep_the_same_physical_positions_when_minecraft_gui_scale_changes(){
  for(int[] size:new int[][]{{768,480},{1024,576},{1280,720},{1920,1080},{3840,2160}}){
   var reference=HudEditorLayout.measure(size[0],size[1],1);
   for(double guiScale:new double[]{2,3,4}){var actual=HudEditorLayout.measure((int)Math.ceil(size[0]/guiScale),(int)Math.ceil(size[1]/guiScale),guiScale);assertEquals(reference.x()*reference.scale(),actual.x()*actual.scale()*guiScale,4);assertEquals(reference.y()*reference.scale(),actual.y()*actual.scale()*guiScale,4);assertTrue(actual.controlsX()>=actual.x()+12);assertTrue(actual.controlsX()+424<=actual.x()+actual.width()-12);assertTrue(11*actual.scale()*guiScale>=14);}
  }
 }
 @Test void toolbar_hit_test_uses_gui_coordinates_without_reinterpreting_hud_positions(){var layout=HudEditorLayout.measure(384,240,2);assertTrue(layout.contains((layout.x()+8)*layout.scale(),(layout.y()+8)*layout.scale()));assertFalse(layout.contains((layout.x()+8)*layout.scale(),(layout.y()-2)*layout.scale()));assertFalse(layout.contains((layout.x()-2)*layout.scale(),(layout.y()+8)*layout.scale()));assertFalse(layout.atTop().contains((layout.x()+8)*layout.scale(),(layout.y()+8)*layout.scale()));assertTrue(layout.atTop().contains((layout.x()+8)*layout.scale(),20*layout.scale()));}
}
