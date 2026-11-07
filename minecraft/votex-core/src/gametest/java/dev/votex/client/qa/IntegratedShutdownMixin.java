package dev.votex.client.qa;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Test-source-set compatibility for MC26.2's new blocking operation before halt(false).
 * Fabric already pumps its phases in Minecraft's server-exit busy wait, but not here.
 * Keep the job on the real server thread while servicing the same test phase scheduler.
 * Never included in the distributed Core or ordinary game profiles.
 */
@Mixin(IntegratedServer.class)
abstract class IntegratedShutdownMixin {
 @WrapOperation(method="halt",at=@At(value="INVOKE",target="Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"))
 private void votexTest$avoidShutdownBarrierDeadlock(IntegratedServer server,Runnable job,Operation<Void> original){
  var mc=Minecraft.getInstance();if(!System.getProperties().containsKey("fabric.client.gametest")||ThreadingImpl.testThread==null||!mc.isSameThread()){original.call(server,job);return;}
  var done=new CompletableFuture<Void>();server.execute(()->{try{job.run();done.complete(null);}catch(Throwable error){done.completeExceptionally(error);}});
  try{
   var pump=Minecraft.class.getDeclaredMethod("postRunTasks");pump.setAccessible(true);int steps=0;long deadline=System.nanoTime()+30_000_000_000L;
   while(!done.isDone()){pump.invoke(mc);steps++;if(System.nanoTime()>deadline)throw new IllegalStateException("QA server shutdown job did not complete within 30 seconds");java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);}
   done.join();System.out.println("VOTEX_QA_SHUTDOWN_PHASE_PUMP "+steps+"; halt job stayed on server thread");
  }catch(ReflectiveOperationException failure){throw new IllegalStateException("Fabric test phase pump is unavailable",failure);}
 }
}
