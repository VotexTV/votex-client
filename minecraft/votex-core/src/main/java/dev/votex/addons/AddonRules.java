package dev.votex.addons;
import java.net.URI;
import java.util.Locale;
/** Pure boundaries shared by catalogue requests and a next-launch installation plan. */
public final class AddonRules {
 private AddonRules(){}
 public static final long MAX_FILE=128L*1024*1024,MAX_TOTAL=512L*1024*1024;
 public static String filename(String name){if(name==null||name.isEmpty()||name.length()>180||name.equals(".")||name.equals("..")||name.endsWith(".")||name.endsWith(" ")||name.chars().anyMatch(c->c<32||"<>:\"/\\|?*".indexOf(c)>=0))throw new IllegalArgumentException("Unsicherer Dateiname");String stem=name.split("\\.")[0].toUpperCase(Locale.ROOT);if(stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))throw new IllegalArgumentException("Reservierter Dateiname");return name;}
 public static String reference(String value){if(value==null||!value.matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException("Ungültige Projektkennung");return value;}
 public static URI cdn(String value){URI uri=URI.create(value);if(!"https".equals(uri.getScheme())||!"cdn.modrinth.com".equals(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getFragment()!=null)throw new IllegalArgumentException("Download nur vom Modrinth-CDN erlaubt");return uri;}
 public static String hash(String hash){if(hash==null||!hash.matches("[a-fA-F0-9]{128}"))throw new IllegalArgumentException("SHA-512-Prüfsumme fehlt");return hash.toLowerCase(Locale.ROOT);}
 public static String folder(String kind){return switch(kind){case "mod"->"mods";case "shader"->"shaderpacks";case "resourcepack"->"resourcepacks";default->throw new IllegalArgumentException("Nicht unterstützte Erweiterung");};}
 public static boolean png(byte[] bytes){if(bytes==null||bytes.length<24||bytes.length>512*1024)return false;int[] signature={137,80,78,71,13,10,26,10};for(int i=0;i<8;i++)if((bytes[i]&255)!=signature[i])return false;int width=java.nio.ByteBuffer.wrap(bytes,16,4).getInt(),height=java.nio.ByteBuffer.wrap(bytes,20,4).getInt();return width>0&&width<=512&&height>0&&height<=512;}
}
