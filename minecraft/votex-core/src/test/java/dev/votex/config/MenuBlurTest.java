package dev.votex.config;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MenuBlurTest {
 @TempDir Path dir;
 private ThemeConfig theme(double blur,String fields)throws Exception{Path path=dir.resolve("theme.json");Files.writeString(path,"{\"schemaVersion\":1,\"blur\":"+blur+fields+"}");return ThemeConfig.read(path);}
 @Test void actual_exported_strength_controls_the_kernel_instead_of_a_boolean_gate()throws Exception{
  int previous=-1;for(double amount:new double[]{0,.1,10,20,42,70,100}){int radius=theme(amount,"").menuBlurRadius(0,true,true);assertTrue(radius>=previous&&radius<=10);if(amount>0)assertTrue(radius>0);previous=radius;}
  assertEquals(0,theme(0,"").menuBlurRadius(8,true,true));assertEquals(1,theme(.1,"").menuBlurRadius(8,true,true));assertEquals(4,theme(42,"").menuBlurRadius(8,true,true));assertEquals(10,theme(100,"").menuBlurRadius(0,true,true));
 }
 @Test void every_vanilla_screen_keeps_its_own_setting_independent_of_theme_and_module_switch()throws Exception{
  for(int vanilla:new int[]{0,3,10})for(double amount:new double[]{0,42,100})for(boolean enabled:new boolean[]{false,true})assertEquals(vanilla,theme(amount,",\"glassEffect\":false").menuBlurRadius(vanilla,false,enabled));
 }
 @Test void performance_and_user_off_switches_remove_the_effect_without_overwriting_the_export()throws Exception{
  assertEquals(0,theme(90,",\"glassEffect\":false").menuBlurRadius(7,true,true));var active=theme(90,"");assertEquals(0,active.menuBlurRadius(7,true,false));assertEquals(9,active.menuBlurRadius(7,true,true));
  Path path=dir.resolve("theme.json");String before=Files.readString(path);active.menuBlurRadius(7,true,false);assertEquals(before,Files.readString(path));assertEquals(90,active.effects().blur());
 }
 @Test void reduced_motion_retains_static_glass_and_live_reload_replaces_the_strength()throws Exception{
  var light=theme(20,",\"reducedMotion\":true");var strong=theme(90,",\"reducedMotion\":true");assertFalse(light.motion());assertEquals(2,light.menuBlurRadius(5,true,true));assertEquals(9,strong.menuBlurRadius(5,true,true));
 }
}
