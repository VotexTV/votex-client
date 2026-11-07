package dev.votex.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiColorTest {
 @Test void opaque_material_preserves_translucent_color_instead_of_turning_it_into_solid_accent(){
  assertEquals(0xff313a5e,UiColor.opaqueOver(0x456e9fff,0xff1b1522));
  assertEquals(0xff1b1522,UiColor.opaqueOver(0x006e9fff,0xff1b1522));
  assertEquals(0xff6e9fff,UiColor.opaqueOver(0xff6e9fff,0xff1b1522));
 }
 @Test void preferred_text_is_preserved_when_legible(){assertEquals(0xfff7faff,UiColor.foreground(0xff101729,0xfff7faff));assertEquals(0xff101729,UiColor.foreground(0xffc8d5f4,0xff101729));}
 @Test void primary_text_stays_legible_across_light_dark_and_colored_accents(){
  for(int red=0;red<=255;red+=17)for(int green=0;green<=255;green+=17)for(int blue=0;blue<=255;blue+=17){
   int bg=0xff000000|red<<16|green<<8|blue;int text=UiColor.foreground(bg,0xfff7faff);
   assertTrue(UiColor.contrast(bg,text)>=4.5,"Insufficient contrast for "+Integer.toHexString(bg));
  }
 }
}
