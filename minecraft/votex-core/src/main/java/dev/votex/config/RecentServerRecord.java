package dev.votex.config;

import com.google.gson.JsonObject;
import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Locale;

/** Local successful-join metadata. No account, server token or chat content is stored. */
public final class RecentServerRecord {
 private RecentServerRecord(){}
 public static JsonObject create(String profile,String name,String address,Instant joined){
  if(profile==null||!profile.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid profile ID");
  String normalized=address(address),label=name==null?"":name.codePoints().filter(c->!Character.isISOControl(c)).limit(80).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString().trim();
  JsonObject result=new JsonObject();result.addProperty("schemaVersion",1);result.addProperty("profileId",profile);result.addProperty("name",label.isEmpty()?normalized:label);result.addProperty("address",normalized);result.addProperty("joinedAt",joined.toString());return result;
 }
 public static String address(String source){
  if(source==null)throw new IllegalArgumentException("Server address missing");if(source.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid server address");String value=source.trim();
  if(value.isEmpty()||value.length()>255||value.codePoints().anyMatch(c->Character.isISOControl(c)||Character.isWhitespace(c)||"/\\@?#\"'=".indexOf(c)>=0))throw new IllegalArgumentException("Invalid server address");
  String host,suffix="";
  if(value.startsWith("[")){
   int end=value.indexOf(']');if(end<2)throw new IllegalArgumentException("Invalid IPv6 address");host=value.substring(1,end);suffix=value.substring(end+1);host="["+ipv6(host)+"]";
  }else if(value.chars().filter(c->c==':').count()>1){host="["+ipv6(value)+"]";
  }else{
   int colon=value.indexOf(':');host=colon<0?value:value.substring(0,colon);if(colon>=0)suffix=value.substring(colon);
   host=IDN.toASCII(host).toLowerCase(Locale.ROOT);if(host.isEmpty()||host.length()>253)throw new IllegalArgumentException("Invalid hostname");
   for(String label:host.split("\\.",-1))if(label.isEmpty()||label.length()>63||label.startsWith("-")||label.endsWith("-")||!label.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid hostname");
   if(host.matches("[0-9.]+")){String[] parts=host.split("\\.",-1);if(parts.length!=4)throw new IllegalArgumentException("Invalid IPv4 address");for(String part:parts)if(part.length()>3||Integer.parseInt(part)>255||part.length()>1&&part.startsWith("0"))throw new IllegalArgumentException("Invalid IPv4 address");}
  }
  if(suffix.isEmpty())return host;
  if(!suffix.matches(":[0-9]{1,5}"))throw new IllegalArgumentException("Invalid server port");int port=Integer.parseInt(suffix.substring(1));if(port<1||port>65535)throw new IllegalArgumentException("Invalid server port");return host+":"+port;
 }
 private static String ipv6(String host){try{if(!host.matches("[A-Fa-f0-9:]+")||!(InetAddress.getByName(host) instanceof Inet6Address ip))throw new IllegalArgumentException("Invalid IPv6 literal");return ip.getHostAddress().toLowerCase(Locale.ROOT);}catch(Exception invalid){throw new IllegalArgumentException("Invalid IPv6 literal",invalid);}}
}
