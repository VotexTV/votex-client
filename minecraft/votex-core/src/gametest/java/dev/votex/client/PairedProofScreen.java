package dev.votex.client;

import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** Test-only view of the two actual connected player entities, without pose overrides. */
final class PairedProofScreen extends Screen {
 private final UUID peer;
 private final String stage;
 PairedProofScreen(UUID peer,String stage){super(Component.literal("Votex two-client proof"));this.peer=peer;this.stage=stage;}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
  GlassRender.round(g,12,12,width-24,height-24,16,0xf0122032);
  UiText.title(g,"Zwei echte Minecraft-Verbindungen",26,27,0xffeef6ff,1.5f);
  UiText.draw(g,stage,26,55,0xffa9c9e8);
  var other=minecraft.level==null?null:minecraft.level.getPlayerByUUID(peer);
  int middle=width/2;
  if(minecraft.player!=null){UiText.centered(g,minecraft.player.getGameProfile().name()+" · lokal",middle/2,height-39,0xffebf5ff);InventoryScreen.extractEntityInInventoryFollowsMouse(g,24,82,middle-12,height-53,Math.max(36,(height-150)/2),.0625f,middle/2f,height/2f,minecraft.player);}
  if(other!=null){UiText.centered(g,other.getGameProfile().name()+" · Netzwerk",middle+middle/2,height-39,0xffebf5ff);InventoryScreen.extractEntityInInventoryFollowsMouse(g,middle+12,82,width-24,height-53,Math.max(36,(height-150)/2),.0625f,middle+middle/2f,height/2f,other);}
  UiText.centered(g,"Echte UUIDs, empfangene Texturen und laufende Emotes · keine Vorschau-Pose",middle,height-22,0xff9bb6d0);
 }
}
