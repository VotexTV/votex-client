package dev.votex.client;

import net.minecraft.client.Minecraft;

/** Resolves the live menu theme without mutating or saving Minecraft's options. */
public final class VotexMenuBlur {
 private VotexMenuBlur(){}
 public static int radius(int vanilla){
  var core=VotexClient.INSTANCE;
  if(core==null||core.modules==null||core.theme==null)return vanilla;
  var mc=Minecraft.getInstance();if(mc==null||mc.gui==null)return vanilla;
  if(mc.gui.screen() instanceof VotexMenuSurface&&(VotexGlassEngine.active()||core.modules.config().glass.quality==dev.votex.config.GlassConfig.Quality.OFF))return 0;return core.theme.menuBlurRadius(vanilla,mc.gui.screen() instanceof VotexMenuSurface,core.modules.config().blurMenus);
 }
}
