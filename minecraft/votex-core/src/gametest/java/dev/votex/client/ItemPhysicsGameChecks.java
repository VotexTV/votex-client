package dev.votex.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.votex.config.AtomicJson;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual dropped entities, native model submissions and unchanged server simulation; test source set only. */
public final class ItemPhysicsGameChecks {
 private ItemPhysicsGameChecks(){}
 private record Fixture(ServerLevel level,UUID player,double[] original,int floor,int x,int z,List<Integer> entities,Map<BlockPos,BlockState> blocks){}
 private record View(boolean enabled,boolean hideGui,CameraType camera,int fov){}
 public static Map<String,Object> run(ClientGameTestContext context,Path destination)throws Exception{
  MinecraftServer server=context.computeOnClient(mc->{if(mc.getSingleplayerServer()==null||mc.player==null||!mc.gameDirectory.toString().toLowerCase(Locale.ROOT).contains("clientgametest"))throw new AssertionError("Refusing non-isolated Item Physics world");return mc.getSingleplayerServer();});
  View view=context.computeOnClient(mc->new View(VotexClient.INSTANCE.modules.enabled("item_physics"),mc.gui.hud.isHidden(),mc.options.getCameraType(),mc.options.fov().get()));
  UUID player=context.computeOnClient(mc->mc.player.getUUID());
  Fixture fixture=server(context,server,()->{
   var p=server.getPlayerList().getPlayer(player);if(p==null)throw new AssertionError("Test player absent from integrated server");var level=p.level();int x=p.blockPosition().getX(),z=p.blockPosition().getZ(),floor=p.blockPosition().getY()+3;var blocks=new LinkedHashMap<BlockPos,BlockState>();
   for(int dx=-4;dx<=4;dx++)for(int dz=-3;dz<=6;dz++)for(int dy=0;dy<=5;dy++){BlockPos pos=new BlockPos(x+dx,floor+dy,z+dz);blocks.put(pos,level.getBlockState(pos));level.setBlock(pos,dy==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
   double[] original={p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot()};p.teleportTo(level,x+.5,floor+1.01,z-1.5,Set.of(),0,18,false);
   var ids=new ArrayList<Integer>();for(int i=0;i<3;i++){var item=new ItemEntity(level,x-.6+i*1.1,floor+1.1,z+2.5,new ItemStack(i==0?Items.DIAMOND:i==1?Items.GOLD_INGOT:Items.REDSTONE_BLOCK),0,0,0);item.setNeverPickUp();if(!level.addFreshEntity(item))throw new AssertionError("Could not spawn actual dropped item");ids.add(item.getId());}
   return new Fixture(level,player,original,floor,x,z,ids,blocks);
  });
  try{
   context.runOnClient(mc->{mc.gui.setScreen(null);mc.options.setCameraType(CameraType.FIRST_PERSON);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();mc.options.fov().set(40);EmotePlayer.stop();VotexClient.INSTANCE.modules.config().enabled.put("item_physics",false);});
   context.waitFor(mc->fixture.entities().stream().allMatch(id->mc.level.getEntity(id) instanceof ItemEntity item&&item.onGround()),800);
   context.waitTicks(3);shot(context,destination,"items-vanilla-grounded");
   Map<String,Object> geometry=context.computeOnClient(mc->groundChecks(mc,fixture.entities().getFirst(),fixture.entities().get(2)));
   context.runOnClient(mc->VotexClient.INSTANCE.modules.config().enabled.put("item_physics",true));context.waitTicks(3);shot(context,destination,"items-physics-grounded-flat");
   // Identical server ItemEntity.tick trajectories, advanced on the server thread with each client render mode.
   context.runOnClient(mc->VotexClient.INSTANCE.modules.config().enabled.put("item_physics",false));var vanilla=server(context,server,()->trajectory(fixture));
   context.runOnClient(mc->VotexClient.INSTANCE.modules.config().enabled.put("item_physics",true));var modified=server(context,server,()->trajectory(fixture));
   if(!vanilla.equals(modified))throw new AssertionError("Client render toggle changed server ItemEntity.tick trajectory");
   int airborne=server(context,server,()->{var item=new ItemEntity(fixture.level(),fixture.x()+.5,fixture.floor()+3.2,fixture.z()+2.5,new ItemStack(Items.AMETHYST_SHARD),.04,.12,0);item.setNeverPickUp();if(!fixture.level().addFreshEntity(item))throw new AssertionError("Could not spawn airborne test item");fixture.entities().add(item.getId());return item.getId();});
   context.waitFor(mc->mc.level.getEntity(airborne) instanceof ItemEntity item&&!item.onGround(),400);
   Map<String,Object> tumble=context.computeOnClient(mc->airChecks(mc,airborne));shot(context,destination,"items-physics-airborne-a");context.waitTicks(4);shot(context,destination,"items-physics-airborne-b");
   context.runOnClient(mc->VotexClient.INSTANCE.modules.config().enabled.put("item_physics",false));context.waitTicks(2);shot(context,destination,"items-toggle-restores-vanilla");
   var result=new LinkedHashMap<String,Object>();result.put("nativeGeometry",geometry);result.put("nativeAirborneGeometry",tumble);result.put("serverSimulation",Map.of("method","30 actual server ItemEntity.tick steps under disabled/enabled client render configuration","identicalTrajectories",true,"frames",vanilla));result.put("worldEntities",fixture.entities());result.put("screenshots","real integrated world: vanilla, flat grounded, airborne two frames and disabled restoration");AtomicJson.write(destination.resolve("item-physics-proof.json"),result);return result;
  }finally{
   server(context,server,()->{for(int id:fixture.entities()){var item=fixture.level().getEntity(id);if(item!=null)item.discard();}for(var block:fixture.blocks().entrySet())fixture.level().setBlock(block.getKey(),block.getValue(),3);var p=server.getPlayerList().getPlayer(fixture.player());if(p!=null){var o=fixture.original();p.teleportTo(fixture.level(),o[0],o[1],o[2],Set.of(),(float)o[3],(float)o[4],false);}return true;});
   context.runOnClient(mc->{if(mc.gui.hud.isHidden()!=view.hideGui())mc.gui.hud.toggle();mc.options.setCameraType(view.camera());mc.options.fov().set(view.fov());VotexClient.INSTANCE.modules.config().enabled.put("item_physics",view.enabled());});context.waitTicks(3);
  }
 }
 private static Map<String,Object> groundChecks(Minecraft mc,int flatId,int blockId){
  var item=(ItemEntity)mc.level.getEntity(flatId);var before=entity(item);var renderer=(ItemEntityRenderer)(Object)mc.getEntityRenderDispatcher().getRenderer(item);var state=renderer.createRenderState();renderer.extractRenderState(item,state,1);if(!((ItemPhysicsState)state).votex$grounded()||state.item.isEmpty()||state.item.getModelBoundingBox().getZsize()>.0625)throw new AssertionError("Native flat dropped-item state/ground mixin unavailable");state.ageInTicks=20;
  VotexClient.INSTANCE.modules.config().enabled.put("item_physics",false);Matrix4f vanilla=capture(renderer,state);
  VotexClient.INSTANCE.modules.config().enabled.put("item_physics",true);Matrix4f flat=capture(renderer,state);state.ageInTicks=40;Matrix4f stable=capture(renderer,state);
  if(!same(flat,stable))throw new AssertionError("Grounded Item Physics continued bobbing/spinning");Vector3f normal=flat.transformDirection(new Vector3f(0,0,1)).normalize();if(Math.abs(normal.y())<.95)throw new AssertionError("Actual dropped sprite did not lie flat: "+normal);
  state.ageInTicks=20;VotexClient.INSTANCE.modules.config().enabled.put("item_physics",false);Matrix4f restored=capture(renderer,state);if(!same(vanilla,restored)||same(vanilla,flat))throw new AssertionError("Turning Item Physics off did not restore exact vanilla submission");if(!before.equals(entity(item)))throw new AssertionError("Render submission changed client item entity state");
  var block=(ItemEntity)mc.level.getEntity(blockId);var br=(ItemEntityRenderer)(Object)mc.getEntityRenderDispatcher().getRenderer(block);var bs=br.createRenderState();br.extractRenderState(block,bs,1);VotexClient.INSTANCE.modules.config().enabled.put("item_physics",true);Matrix4f blockPose=capture(br,bs);if(bs.item.getModelBoundingBox().getZsize()<=.0625||Math.abs(blockPose.transformDirection(new Vector3f(0,1,0)).normalize().y())<.95)throw new AssertionError("Block item was incorrectly flattened");
  return Map.of("flatNormalY",normal.y(),"groundPoseStableAcross20Ticks",true,"toggleRestoresExactVanillaMatrix",true,"blockRemainsUpright",true,"entityUnchangedBySubmission",true,"vanillaMatrix",matrix(vanilla),"flatMatrix",matrix(flat));
 }
 private static Map<String,Object> airChecks(Minecraft mc,int id){var item=(ItemEntity)mc.level.getEntity(id);var before=entity(item);var renderer=(ItemEntityRenderer)(Object)mc.getEntityRenderDispatcher().getRenderer(item);var state=renderer.createRenderState();renderer.extractRenderState(item,state,1);if(((ItemPhysicsState)state).votex$grounded())throw new AssertionError("Airborne extraction reported grounded");VotexClient.INSTANCE.modules.config().enabled.put("item_physics",true);state.ageInTicks=10;Matrix4f a=capture(renderer,state);state.ageInTicks=20;Matrix4f b=capture(renderer,state);if(same(a,b))throw new AssertionError("Airborne native submission did not tumble");Vector3f upA=a.transformDirection(new Vector3f(0,1,0)).normalize(),upB=b.transformDirection(new Vector3f(0,1,0)).normalize();if(upA.distance(upB)<.1)throw new AssertionError("Airborne rotation only spun vertically instead of tumbling");if(!before.equals(entity(item)))throw new AssertionError("Air render submission modified an item entity");return Map.of("differentPoseAtTwoAges",true,"tiltedUpAxisChanges",true,"entityUnchangedBySubmission",true,"poseA",matrix(a),"poseB",matrix(b));}
 private static Matrix4f capture(ItemEntityRenderer renderer,ItemEntityRenderState state){var matrices=new ArrayList<Matrix4f>();var collector=(SubmitNodeCollector)Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),new Class<?>[]{SubmitNodeCollector.class},(proxy,method,args)->{if(method.getName().equals("order"))return proxy;if(method.getName().equals("toString"))return "Votex isolated item collector";if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);if(method.getName().equals("equals"))return proxy==args[0];if(args!=null&&method.getName().startsWith("submit"))for(Object arg:args)if(arg instanceof PoseStack pose){matrices.add(new Matrix4f(pose.last().pose()));break;}return null;});PoseStack stack=new PoseStack();renderer.submit(state,stack,collector,new CameraRenderState());if(matrices.isEmpty())throw new AssertionError("Native item model submitted no geometry");if(!same(stack.last().pose(),new Matrix4f()))throw new AssertionError("Item renderer left an unbalanced transform");return matrices.getFirst();}
 private static List<Map<String,Object>> trajectory(Fixture fixture){var item=new ItemEntity(fixture.level(),fixture.x()+3.3,fixture.floor()+5,fixture.z()+4.5,new ItemStack(Items.DIAMOND),.06,.15,.02);item.setNeverPickUp();var result=new ArrayList<Map<String,Object>>();for(int i=0;i<30;i++){item.tick();result.add(entity(item));}return result;}
 private static Map<String,Object> entity(ItemEntity item){return Map.of("position",List.of(item.getX(),item.getY(),item.getZ()),"velocity",List.of(item.getDeltaMovement().x,item.getDeltaMovement().y,item.getDeltaMovement().z),"age",item.getAge(),"grounded",item.onGround(),"count",item.getItem().getCount(),"pickupDelay",item.hasPickUpDelay());}
 private static boolean same(Matrix4f a,Matrix4f b){float[] aa=a.get(new float[16]),bb=b.get(new float[16]);for(int i=0;i<16;i++)if(Math.abs(aa[i]-bb[i])>0.00001)return false;return true;}
 private static List<Float> matrix(Matrix4f value){var result=new ArrayList<Float>();for(float part:value.get(new float[16]))result.add(part);return result;}
 private static <T>T server(ClientGameTestContext context,MinecraftServer server,Callable<T> operation){var future=server.submit(()->{try{return operation.call();}catch(Exception error){throw new RuntimeException(error);}});context.waitFor(mc->future.isDone(),1000);return future.join();}
 private static void shot(ClientGameTestContext context,Path destination,String name){context.waitTicks(2);context.takeScreenshot(TestScreenshotOptions.of("votex-goal-"+name).withDestinationDir(destination).disableCounterPrefix());}
}
