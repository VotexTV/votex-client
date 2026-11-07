package dev.votex.client;

import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/** Actual local staging/queue and failure, in the isolated QA game directory only. */
final class InstallationGameChecks {
 static Map<String,Object> run(ClientGameTestContext c,Path images)throws Exception{
  Path root=c.computeOnClient(mc->mc.gameDirectory.toPath());
  if(!root.toString().toLowerCase(java.util.Locale.ROOT).contains("clientgametest"))throw new AssertionError("Not a QA directory");
  var originalQueue=ModLibraryService.readQueue(root).deepCopy();
  Path file=root.resolve("votex-feedback-fixture.zip");try(var zip=new ZipOutputStream(Files.newOutputStream(file))){zip.putNextEntry(new ZipEntry("shaders/test.vsh"));zip.write("// Original QA shader fixture; never enabled\nvoid main() {}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
  c.getInput().resizeWindow(1280,720);c.waitTicks(3);
  c.runOnClient(mc->{mc.gui.setScreen(new ModLibraryScreen(VotexClient.INSTANCE,null));set(mc.gui.screen(),"kind","shader");});
  c.waitFor(mc->!(boolean)field(mc.gui.screen(),"busy"),300);
  var gate=new CountDownLatch(1);var blocker=ModLibraryService.async(()->{if(!gate.await(15,TimeUnit.SECONDS))throw new AssertionError("QA IO gate timeout");return true;});
  try{
   c.runOnClient(mc->mc.gui.screen().onFilesDrop(java.util.List.of(file)));c.waitTicks(3);
   c.runOnClient(mc->{if(!InstallationFeedback.ACTIVITY.snapshot().phase().equals("working"))throw new AssertionError("No installation feedback while queued");});
   shot(c,images,"install-working-library");
   c.runOnClient(mc->mc.gui.setScreen(null));gate.countDown();blocker.get(2,TimeUnit.SECONDS);
   c.waitFor(mc->InstallationFeedback.ACTIVITY.snapshot()!=null&&InstallationFeedback.ACTIVITY.snapshot().phase().equals("ready"),300);c.waitTicks(7);
   var queue=ModLibraryService.readQueue(root);if(queue.size()!=originalQueue.size()+1)throw new AssertionError("Import not durably queued");var action=queue.asList().stream().map(com.google.gson.JsonElement::getAsJsonObject).filter(item->item.get("filename").getAsString().equals(file.getFileName().toString())).findFirst().orElseThrow(()->new AssertionError("Feedback fixture is not in queue"));for(var prior:originalQueue)if(!queue.asList().contains(prior))throw new AssertionError("Import overwrote an existing queued action");
   var acceptedQueue=queue.deepCopy();
   if(Files.exists(root.resolve("shaderpacks").resolve(file.getFileName())))throw new AssertionError("Loaded game was hot-modified");
   if(!Files.isRegularFile(root.resolve("config/votex/addon-staging").resolve(action.get("sha512").getAsString()+".bin")))throw new AssertionError("Success without staged bytes");
   shot(c,images,"install-ready-after-close");
   c.getInput().resizeWindow(768,480);c.waitTicks(3);c.runOnClient(mc->{VotexClient.INSTANCE.modules.config().menuAnimations=false;mc.gui.setScreen(new ModLibraryScreen(VotexClient.INSTANCE,null));set(mc.gui.screen(),"kind","shader");});
   c.waitFor(mc->!(boolean)field(mc.gui.screen(),"busy"),300);
   Path invalid=root.resolve("invalid-feedback-fixture.zip");Files.writeString(invalid,"not a zip");c.runOnClient(mc->mc.gui.screen().onFilesDrop(java.util.List.of(invalid)));
   c.waitFor(mc->InstallationFeedback.ACTIVITY.snapshot()!=null&&InstallationFeedback.ACTIVITY.snapshot().phase().equals("error"),300);c.waitTicks(3);shot(c,images,"install-failure-compact-reduced-motion");
   if(!ModLibraryService.readQueue(root).equals(acceptedQueue))throw new AssertionError("Failed import altered queue");
   c.runOnClient(mc->{VotexClient.INSTANCE.modules.config().menuAnimations=true;mc.gui.setScreen(null);});
   return Map.of("actualImportQueued",true,"stagedBytesPresent",true,"noHotInstall",true,"completionSurvivedScreenClose",true,"invalidZipFailedWithoutQueueMutation",true,"reducedMotionCompactCapture",true,"networkDownloads","none; original local QA ZIP","microphone","not opened");
  }finally{gate.countDown();}
 }
 private static void shot(ClientGameTestContext c,Path p,String name){c.takeScreenshot(TestScreenshotOptions.of("votex-goal-"+name).withDestinationDir(p).disableCounterPrefix());}
 private static Object field(Object o,String key){try{var f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
 private static void set(Object o,String key,Object v){try{var f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,v);}catch(Exception e){throw new AssertionError(e);}}
}
