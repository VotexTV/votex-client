package dev.votex.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InstallActivityTest {
 @Test void completion_requires_current_operation_and_unknown_sizes_stay_indeterminate(){var a=new InstallActivity();long old=a.begin("First",0),id=a.begin("Second",1);a.progress(id,"Download",100,0);assertEquals(-1,a.snapshot().progress());a.finish(old,true,"Wrong",2);assertEquals("working",a.snapshot().phase());a.progress(id,"Download",150,100);assertEquals(1,a.snapshot().progress());a.finish(id,false,"Checksum failed",3);assertEquals("error",a.snapshot().phase());a.finish(id,true,"False success",4);assertEquals("error",a.snapshot().phase());}
 @Test void terminal_card_outlives_menu_and_expires_without_any_pending_timer(){var a=new InstallActivity();long id=a.begin("Shader",0);a.expire(99_000_000_000L);assertNotNull(a.snapshot());a.finish(id,true,"Staged",100_000_000_000L);a.expire(107_000_000_000L);assertNotNull(a.snapshot());a.expire(109_000_000_000L);assertNull(a.snapshot());}
}
