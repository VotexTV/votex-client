package dev.votex.client;

import com.google.gson.*;
import dev.votex.config.AtomicJson;
import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerModelType;

/** Exact wide-to-slim UV fixture conversion; never edits user skin assets. */
final class SkinModelGameChecks {
 static Path prepare(Path directory)throws Exception{
  Path fixture=directory.resolve("skin-model-qa");Files.createDirectories(fixture.resolve("assets"));
  BufferedImage source;
  try(var input=SkinModelGameChecks.class.getResourceAsStream("/assets/votex_client_test/skins/studio.png")){source=ImageIO.read(input);}
  ImageIO.write(source,"png",fixture.resolve("assets/skin-wide-qa.png").toFile());
  var slim=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);var g=slim.createGraphics();
  try{
   g.drawImage(source,0,0,null);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
   for(int[] origin:new int[][]{{40,16},{40,32},{32,48},{48,48}}){int x=origin[0],y=origin[1];g.setComposite(AlphaComposite.Clear);g.fillRect(x,y,16,16);g.setComposite(AlphaComposite.Src);
    int[][] faces={{4,0,4,4,4,0,3,4},{8,0,4,4,7,0,3,4},{0,4,4,12,0,4,4,12},{4,4,4,12,4,4,3,12},{8,4,4,12,7,4,4,12},{12,4,4,12,11,4,3,12}};
    for(int[] f:faces)g.drawImage(source,x+f[4],y+f[5],x+f[4]+f[6],y+f[5]+f[7],x+f[0],y+f[1],x+f[0]+f[2],y+f[1]+f[3],null);
   }
  }finally{g.dispose();}
  ImageIO.write(slim,"png",fixture.resolve("assets/skin-slim-qa.png").toFile());
  var data=new JsonObject();data.addProperty("schemaVersion",1);var skins=new JsonArray();
  for(String kind:new String[]{"wide","slim"}){var item=new JsonObject();item.addProperty("id",kind+"-qa");item.addProperty("name",kind);item.addProperty("model",kind.equals("slim")?"slim":"default");skins.add(item);}data.add("skins",skins);AtomicJson.write(fixture.resolve("presets.json"),data);return fixture;
 }
 static void selectAndCheck(Path directory,String kind)throws Exception{
  LocalLooks.selectSkin(directory,kind+"-qa");var player=Minecraft.getInstance().player;var skin=player.getSkin();
  if(skin.model()!=(kind.equals("slim")?PlayerModelType.SLIM:PlayerModelType.WIDE))throw new AssertionError("Selected arm model was ignored");
  var state=new net.minecraft.client.renderer.entity.state.AvatarRenderState();state.id=player.getId();
  if(!skin.body().texturePath().equals(LocalLooks.skin(state)))throw new AssertionError("World and vanilla hand textures differ");
  if(player.getSkin()!=skin)throw new AssertionError("Unchanged skin override allocated a new result");
  var network=dev.votex.social.SocialRules.validate(LocalLooks.networkAppearance());if(!network.has("skinModel")||!network.get("skinModel").getAsString().equals(kind.equals("slim")?"slim":"default"))throw new AssertionError("Arm model lost in appearance validation");
 }
}
