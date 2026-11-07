package dev.votex.performance;
import dev.votex.config.AtomicJson;
import java.nio.file.*;
import java.io.IOException;
public final class PerformanceConfig {
 public int schemaVersion=1,targetFps=120;
 public boolean textCache=true,reusableMotion=true;
 public OptimizationCoordinator.Mode adaptive=OptimizationCoordinator.Mode.DISABLED;
 public static PerformanceConfig read(Path file)throws IOException{
  if(!Files.exists(file))return new PerformanceConfig();if(Files.size(file)>16000)throw new IOException("Performance configuration exceeds limit");
  try{var v=AtomicJson.GSON.fromJson(Files.readString(file),PerformanceConfig.class);if(v==null||v.schemaVersion!=1||v.adaptive==null||v.targetFps<30||v.targetFps>360)throw new IllegalArgumentException();return v;}catch(RuntimeException e){throw new IOException("Invalid performance configuration; original preserved",e);}
 }
}
