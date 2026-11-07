package dev.votex.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class AtomicJsonTest {
 @TempDir Path dir;
 @Test void replacementSurvivesBriefReaderAndLeavesNoTemporaryFiles()throws Exception{
  Path file=dir.resolve("modules.json");AtomicJson.write(file,Map.of("revision",1));
  var reader=Files.newInputStream(file);var worker=Executors.newSingleThreadExecutor();
  try{var save=worker.submit(()->{AtomicJson.write(file,Map.of("revision",2));return true;});Thread.sleep(75);reader.close();assertTrue(save.get(2,TimeUnit.SECONDS));}
  finally{reader.close();worker.shutdownNow();}
  assertEquals(2,AtomicJson.GSON.fromJson(Files.readString(file),com.google.gson.JsonObject.class).get("revision").getAsInt());
  try(var files=Files.list(dir)){assertEquals(1,files.count());}
 }
}
