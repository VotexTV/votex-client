package dev.votex.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstallationNoticeLayoutTest {
 @Test void status_keeps_readable_density_and_clears_fixed_controls_at_each_gui_scale(){
  for(int[] size:new int[][]{{768,480},{1280,720},{1920,1080},{3840,2160}})for(int guiScale=1;guiScale<=4;guiScale++){
   int gw=(int)Math.ceil(size[0]/(double)guiScale),gh=(int)Math.ceil(size[1]/(double)guiScale);
   var notice=InstallationNoticeLayout.measure(gw,gh,guiScale,true);var ui=WorkspaceLayout.measure(gw,gh,guiScale);
   assertTrue(notice.scale()*guiScale*11>=14);assertEquals(ui.logicalHeight(),notice.y()+notice.height());
   assertTrue(notice.x()>=0&&notice.x()+notice.width()<=ui.logicalWidth());
   int libraryHeight=Math.min(640,ui.logicalHeight()-32),libraryTop=(ui.logicalHeight()-libraryHeight)/2;
   assertTrue(libraryTop+libraryHeight-16<notice.y(),"Library footer overlaps status");
   var chat=ChatLayout.history(gw,gh,guiScale);assertTrue(chat.top()+chat.height()-14<notice.y());
   for(int[] anchor:new int[][]{{0,0},{gw,gh}}){var popup=ChatLayout.context(gw,gh,guiScale,anchor[0],anchor[1]);assertTrue(popup.top()+63+114+32<notice.y(),"Chat actions overlap status");}
   var saved=new WorkspaceWindow.Saved();saved.width=saved.height=1;saved.x=saved.y=1;
   var window=WorkspaceWindow.restore(saved,ui.logicalWidth(),ui.logicalHeight(),new WorkspaceWindow.Rect(0,0,1000,620));
   assertTrue(window.y()+window.height()-12<=notice.y(),"Maximized workspace footer overlaps status");
   var hud=HudEditorLayout.measure(gw,gh,guiScale);assertTrue(hud.y()+83+29<notice.y());assertTrue(hud.atTop().y()+83+29<notice.y());
   var wheel=EmoteWheelLayout.measure(gw,gh,guiScale);for(int slot=0;slot<8;slot++)assertTrue(wheel.y(slot,1)+wheel.cardHeight()<ui.logicalHeight()-33,"Wheel slot overlaps relocated hint");
   assertTrue(ui.logicalHeight()-33+11<notice.y(),"Wheel hint overlaps status");
  }
 }
 @Test void gameplay_card_and_workspace_ribbon_switch_without_changing_density(){
  var card=InstallationNoticeLayout.measure(384,240,2,false);var ribbon=InstallationNoticeLayout.measure(384,240,2,true);
  assertFalse(card.ribbon());assertTrue(ribbon.ribbon());assertEquals(card.scale(),ribbon.scale());assertEquals(105,card.height());
  assertTrue(card.y()>0&&card.y()+card.height()<card.viewportHeight());assertTrue(card.x()>0&&card.x()+card.width()<card.viewportWidth());
  assertTrue(ribbon.height()<card.height()/4);assertTrue(ribbon.y()>card.y()+card.height());
 }
 @Test void detail_hover_uses_gui_coordinates_and_rejects_every_outside_edge(){
  for(int guiScale=1;guiScale<=4;guiScale++){
   var r=InstallationNoticeLayout.measure((int)Math.ceil(768d/guiScale),(int)Math.ceil(480d/guiScale),guiScale,true);
   assertTrue(r.contains((r.x()+r.width()/2d)*r.scale(),(r.y()+r.height()/2d)*r.scale()));
   assertFalse(r.contains((r.x()-1)*r.scale(),(r.y()+5)*r.scale()));assertFalse(r.contains((r.x()+r.width()+1)*r.scale(),(r.y()+5)*r.scale()));
   assertFalse(r.contains((r.x()+5)*r.scale(),(r.y()-1)*r.scale()));assertFalse(r.contains((r.x()+5)*r.scale(),(r.y()+r.height()+1)*r.scale()));
  }
 }
}
