package dev.votex.config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
public final class AtomicJson {
 public static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private AtomicJson(){}
 public static void write(Path path,Object value)throws IOException{
  Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp-"+UUID.randomUUID());
  try{Files.writeString(temp,GSON.toJson(value),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);replace(temp,path);}finally{Files.deleteIfExists(temp);}
 }
 /** Windows readers/virus scanners can briefly deny replacement. Retry only that
  * transient failure, on the caller's IO worker, without deleting the old file. */
 private static void replace(Path temp,Path path)throws IOException{
  for(int attempt=0;;attempt++){
   try{try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}return;}
   catch(AccessDeniedException e){if(attempt>=9)throw e;try{Thread.sleep(25);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Interrupted while replacing configuration",interrupted);}}
  }
 }
}
