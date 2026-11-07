package dev.votex.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Resolve the new-profile B conflict without replacing a saved controls preference. */
final class StarterKeyBindings {
 private static final String WAYPOINT="gui.xaero_new_waypoint";
 private final boolean missingSavedWaypoint;
 StarterKeyBindings(Path options){
  boolean missing=false;
  try{missing=!Files.exists(options)||(Files.size(options)<=256_000&&Files.readString(options).lines().noneMatch(line->line.startsWith("key_"+WAYPOINT+":")));}
  catch(Exception ignored){/* An unreadable existing options file is never treated as a fresh preference. */}
  missingSavedWaypoint=missing;
 }
 boolean apply(Minecraft client){
  if(!missingSavedWaypoint||client.options==null)return false;
  var b=InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B);
  var waypoint=waypoint(client);
  if(waypoint==null||!waypoint.saveString().equals(b.getName())||!waypoint.getDefaultKey().equals(b))return false;
  return repair(client);
 }
 private static KeyMapping waypoint(Minecraft client){for(var mapping:client.options.keyMappings)if(mapping.getName().equals(WAYPOINT))return mapping;return null;}
 static boolean conflict(Minecraft client){var core=VotexClient.INSTANCE;if(core==null||core.emoteKey==null||core.emoteKey.isUnbound())return false;for(var mapping:client.options.keyMappings)if(mapping.getName().equals(WAYPOINT)&&mapping.saveString().equals(core.emoteKey.saveString()))return true;return false;}
 static InputConstants.Key suggestion(Minecraft client){
  for(int code:new int[]{GLFW.GLFW_KEY_N,GLFW.GLFW_KEY_J,GLFW.GLFW_KEY_F7,GLFW.GLFW_KEY_F8}){
   var candidate=InputConstants.Type.KEYSYM.getOrCreate(code);boolean occupied=false;
   for(var mapping:client.options.keyMappings)if(!mapping.getName().equals(WAYPOINT)&&mapping.saveString().equals(candidate.getName())){occupied=true;break;}
   if(!occupied)return candidate;
  }
  return null;
 }
 /** Explicit Settings action: the user can repair an older saved conflict as well. */
 static boolean repair(Minecraft client){
  if(!conflict(client))return false;var candidate=suggestion(client);if(candidate==null)return false;
  for(var mapping:client.options.keyMappings)if(mapping.getName().equals(WAYPOINT))mapping.setKey(candidate);
  KeyMapping.resetMapping();client.options.save();return true;
 }
}
