package dev.votex.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkspaceLayoutTest {
 @Test void small_windows_keep_readable_type_and_a_complete_scrollable_page(){
  for(int[] physical:new int[][]{{768,480},{800,600},{1280,720},{1600,900},{1920,1080},{3840,2160}})for(double guiScale:new double[]{1,2,3,4}){
   var ui=WorkspaceLayout.measure((int)Math.ceil(physical[0]/guiScale),(int)Math.ceil(physical[1]/guiScale),guiScale);
   assertTrue(ui.bodyPixels(guiScale)>=14,"Small type at "+physical[0]);assertTrue(ui.contentWidth()>=430,"Categories and columns need readable width");
   assertTrue(ui.panelHeight()*ui.scale()<=physical[1]/guiScale);assertTrue(ui.pageHeight()>=540);assertTrue(ui.maxScroll()>=0);
   if(physical[0]==768){assertTrue(ui.compact());assertTrue(ui.maxScroll()>0);}
  }
 }
 @Test void invalid_gui_dimensions_cannot_make_nan_transforms(){assertThrows(IllegalArgumentException.class,()->WorkspaceLayout.measure(0,200,2));assertThrows(IllegalArgumentException.class,()->WorkspaceLayout.measure(400,200,Double.NaN));}
 @Test void continuous_module_rows_scroll_to_the_last_card_without_pagination(){
  int page=WorkspaceLayout.flowPageHeight(620,608,154,14,83,72);
  assertEquals(1317,page);
  assertEquals(697,page-620);
  assertEquals(608,154+(14-1)*83+72-(page-620));
  assertEquals(620,WorkspaceLayout.flowPageHeight(620,608,154,0,83,72));
  assertEquals(620,WorkspaceLayout.flowPageHeight(620,608,154,4,83,72));
 }
 @Test void content_flow_rejects_invalid_geometry(){assertThrows(IllegalArgumentException.class,()->WorkspaceLayout.flowPageHeight(620,621,154,4,83,72));assertThrows(IllegalArgumentException.class,()->WorkspaceLayout.flowPageHeight(620,608,154,-1,83,72));}
}
