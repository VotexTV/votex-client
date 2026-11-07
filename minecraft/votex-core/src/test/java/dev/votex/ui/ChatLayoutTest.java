package dev.votex.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatLayoutTest {
 @Test void chat_and_context_keep_readable_physical_density_at_each_gui_scale(){
  for(int[] size:new int[][]{{768,480},{1280,720},{1920,1080},{3840,2160}})for(int scale:new int[]{1,2,3,4}){
   int w=(int)Math.ceil(size[0]/(double)scale),h=(int)Math.ceil(size[1]/(double)scale);var chat=ChatLayout.history(w,h,scale);
   assertTrue(chat.scale()*scale*11>=14);assertTrue(chat.width()*chat.scale()<=w);assertTrue(chat.height()*chat.scale()<=h);assertTrue(chat.listHeight()>=150);
   for(double x:new double[]{0,w/2d,w})for(double y:new double[]{0,h/2d,h}){var menu=ChatLayout.context(w,h,scale,x,y);assertTrue(menu.left()>=12&&menu.top()>=12);assertTrue(menu.left()+menu.width()<=menu.viewportWidth()-12);assertTrue(menu.top()+menu.height()<=menu.viewportHeight()-12);assertTrue(menu.scale()*scale*11>=14);}
  }
 }
 @Test void row_spacing_clipping_and_scroll_map_only_visible_message_surfaces(){
  var chat=ChatLayout.history(640,360,2);double x=chat.listLeft()+8,y=chat.listTop();
  assertEquals(0,chat.rowAt(x,y,0,60));assertEquals(-1,chat.rowAt(x,y+51,0,60));assertEquals(1,chat.rowAt(x,y+56,0,60));
  assertEquals(-1,chat.rowAt(x,y-1,0,60));assertEquals(-1,chat.rowAt(x,y+chat.listHeight(),0,60));assertEquals(-1,chat.rowAt(chat.listLeft()+chat.listWidth(),y,0,60));
  assertEquals(1,chat.rowAt(x,y,60.5,60));assertEquals(-1,chat.rowAt(x,y,54.5,60));assertEquals(-1,chat.rowAt(x,y,0,0));
 }
 @Test void thumb_drag_reaches_both_ends_and_empty_lists_cannot_scroll(){
  var chat=ChatLayout.history(640,360,2);assertEquals(0,chat.maxScroll(0));assertEquals(0,chat.scrollFromThumb(chat.listTop(),0,80));
  assertEquals(chat.maxScroll(80),chat.scrollFromThumb(chat.listTop()+chat.listHeight(),0,80));assertEquals(chat.listTop()+chat.listHeight()-chat.thumbHeight(80),chat.thumbY(chat.maxScroll(80),80),.001);
 }
}
