package dev.votex.ui;
import dev.votex.config.ModuleRegistry;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkspaceWindowTest {
 @Test void resizing_keeps_opposite_corner_and_remains_reachable(){
  var start=new WorkspaceWindow.Rect(100,80,800,500);
  for(int edge:new int[]{1,2,4,8,5,6,9,10,16}){
   var r=WorkspaceWindow.drag(start,edge,99999,-99999,1200,800);
   assertTrue(r.x()>=8&&r.y()>=8&&r.x()+r.width()<=1192&&r.y()+r.height()<=792);
   if((edge&1)!=0)assertEquals(900,r.x()+r.width());if((edge&4)!=0)assertEquals(580,r.y()+r.height());
   assertTrue(r.width()>=546&&r.height()>=330);
  }
  var resizedViewport=WorkspaceWindow.drag(new WorkspaceWindow.Rect(1000,600,900,600),10,100,100,590,369);
  assertTrue(resizedViewport.x()+resizedViewport.width()<=582);assertTrue(resizedViewport.y()+resizedViewport.height()<=361);
 }
 @Test void saved_window_survives_restart_and_small_monitor()throws Exception{
  var directory=Files.createTempDirectory("votex-window");var path=directory.resolve("modules.json");
  try{var modules=new ModuleRegistry(path);var rect=new WorkspaceWindow.Rect(200,100,900,540);modules.config().workspaceWindow=WorkspaceWindow.remember(rect,1500,900);modules.save();var reloaded=new ModuleRegistry(path);reloaded.load();assertEquals(rect,WorkspaceWindow.restore(reloaded.config().workspaceWindow,1500,900,rect));var small=WorkspaceWindow.restore(reloaded.config().workspaceWindow,590,369,rect);assertTrue(small.x()+small.width()<=582);assertTrue(small.y()+small.height()<=361);}
  finally{Files.deleteIfExists(path);Files.deleteIfExists(directory);}
 }
}
