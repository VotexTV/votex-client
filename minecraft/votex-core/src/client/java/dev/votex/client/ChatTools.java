package dev.votex.client;
import dev.votex.chat.DuplicateGroups;
import dev.votex.client.mixin.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import java.util.List;
import java.util.regex.Pattern;
public final class ChatTools {
 private ChatTools(){}
 public static boolean enabled(){var c=VotexClient.INSTANCE;return c!=null&&c.modules!=null&&c.modules.enabled("chat");}
 public static boolean stacking(){return enabled()&&VotexClient.INSTANCE.modules.config().chatStackDuplicates;}
 public static List<GuiMessage> history(){return List.copyOf(((ChatComponentAccessor)Minecraft.getInstance().gui.hud.getChat()).votex$messages());}
 public static GuiMessage latest(){var messages=((ChatComponentAccessor)Minecraft.getInstance().gui.hud.getChat()).votex$messages();return messages.isEmpty()?null:messages.getFirst();}
 public static GuiMessage at(double x,double y){var mc=Minecraft.getInstance();var chat=mc.gui.hud.getChat();var access=(ChatComponentAccessor)chat;double scale=access.votex$scale();if(x<0||x>access.votex$width()+8)return null;double fromBottom=(mc.getWindow().getGuiScaledHeight()-40-y)/scale;int row=(int)Math.floor(fromBottom/access.votex$lineHeight());if(row<0||row>=chat.getLinesPerPage())return null;int index=row+access.votex$scroll();var lines=access.votex$lines();return index>=0&&index<lines.size()?lines.get(index).parent():null;}
 public static String sender(GuiMessage message){var contents=message.content().getContents();if(contents instanceof net.minecraft.network.chat.contents.TranslatableContents translated){String key=translated.getKey();if((key.equals("chat.type.text")||key.equals("chat.type.announcement")||key.equals("commands.message.display.incoming"))&&translated.getArgs().length>0){Object arg=translated.getArgs()[0];String value=arg instanceof net.minecraft.network.chat.Component c?c.getString():String.valueOf(arg);String name=DuplicateGroups.safeName(value);if(!name.isEmpty())return name;}}String text=message.content().getString();var angle=Pattern.compile("^<([A-Za-z0-9_]{1,16})>").matcher(text);if(angle.find())return angle.group(1);var prefixed=Pattern.compile("^(?:\\[[^]\\r\\n]{1,32}\\] *){0,3}([A-Za-z0-9_]{1,16}) *[:»]").matcher(text);if(!prefixed.find())return "";String candidate=prefixed.group(1);var connection=Minecraft.getInstance().getConnection();return connection!=null&&connection.getOnlinePlayers().stream().anyMatch(p->p.getProfile().name().equals(candidate))?candidate:"";}
 public static void openSearch(String draft){Minecraft.getInstance().gui.setScreen(new ChatHistoryScreen(VotexClient.INSTANCE,draft));}
 public static void openContext(GuiMessage message,String draft,int x,int y){Minecraft.getInstance().gui.setScreen(new ChatContextScreen(VotexClient.INSTANCE,message,draft,x,y));}
}
