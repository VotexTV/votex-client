package dev.votex.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EmoteWheelLayoutTest {
 @Test void all_eight_cards_remain_visible_and_distinct_at_readable_density(){for(int[] size:new int[][]{{768,480},{800,600},{1280,720},{1920,1080},{3840,2160}})for(double gui:new double[]{1,2,3,4}){var ui=EmoteWheelLayout.measure((int)Math.ceil(size[0]/gui),(int)Math.ceil(size[1]/gui),gui);assertTrue(11*ui.scale()*gui>=14);for(int i=0;i<8;i++){int x=ui.x(i,1),y=ui.y(i,1);assertTrue(x>=8&&y>=8);assertTrue(x+ui.cardWidth()<=ui.width()-8&&y+ui.cardHeight()<=ui.height()-25);for(int j=0;j<i;j++)assertFalse(x<ui.x(j,1)+ui.cardWidth()&&x+ui.cardWidth()>ui.x(j,1)&&y<ui.y(j,1)+ui.cardHeight()&&y+ui.cardHeight()>ui.y(j,1),"Wheel cards overlap at "+size[0]+" slot "+i+"/"+j);}}}
 @Test void elliptical_pointer_mapping_selects_the_visible_slot_and_keeps_the_center_neutral(){var ui=EmoteWheelLayout.measure(384,240,2);assertEquals(-1,ui.selected(ui.centerX()*ui.scale(),ui.centerY()*ui.scale()));for(int i=0;i<8;i++)assertEquals(i,ui.selected((ui.x(i,1)+ui.cardWidth()/2d)*ui.scale(),(ui.y(i,1)+ui.cardHeight()/2d)*ui.scale()));}
}
