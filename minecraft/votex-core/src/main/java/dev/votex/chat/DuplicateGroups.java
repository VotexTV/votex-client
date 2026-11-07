package dev.votex.chat;
import java.util.*;
import java.util.function.Function;
/** Presentation-only consecutive groups. Message objects, signatures and history are never altered. */
public final class DuplicateGroups<T> {
 private final IdentityHashMap<T,Integer> counts=new IdentityHashMap<>();
 public void rebuild(List<T> newestFirst,Function<T,Object> key){counts.clear();int limit=Math.min(newestFirst.size(),2048);for(int i=0;i<limit;){T newest=newestFirst.get(i);Object value=key.apply(newest);int end=i+1;while(end<limit&&Objects.equals(value,key.apply(newestFirst.get(end))))end++;counts.put(newest,end-i);for(int j=i+1;j<end;j++)counts.put(newestFirst.get(j),0);i=end;}}
 public int count(T item){return counts.getOrDefault(item,1);}
 public void clear(){counts.clear();}
 public static boolean matches(String value,String query){return value.toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT));}
 /** Only valid online-profile shaped names become reply targets; text can never inject a command. */
 public static String safeName(String value){return value!=null&&value.matches("[A-Za-z0-9_]{1,16}")?value:"";}
}
