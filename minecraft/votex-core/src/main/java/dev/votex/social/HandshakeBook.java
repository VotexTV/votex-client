package dev.votex.social;
import java.util.*;
/** Consent is a one-use, recipient-bound, expiring server capability. */
public final class HandshakeBook {
 public record Invitation(UUID token,UUID from,UUID to,long expires){}
 private final Map<UUID,Invitation> pending=new HashMap<>();
 private final Map<UUID,Long> lastRequest=new HashMap<>();
 public Invitation invite(UUID from,UUID to,long now){prune(now);if(from.equals(to)||pending.containsKey(to)||pending.size()>=SocialRules.MAX_PEERS||lastRequest.getOrDefault(from,Long.MIN_VALUE/2)+5_000>now)return null;Invitation request=new Invitation(UUID.randomUUID(),from,to,now+10_000);pending.put(to,request);lastRequest.put(from,now);return request;}
 public Invitation accept(UUID recipient,UUID token,long now){prune(now);Invitation request=pending.get(recipient);if(request==null||!request.token.equals(token))return null;pending.remove(recipient);return request;}
 public void remove(UUID player){pending.entrySet().removeIf(e->e.getKey().equals(player)||e.getValue().from.equals(player));lastRequest.remove(player);}
 public void prune(long now){pending.entrySet().removeIf(e->e.getValue().expires<now);lastRequest.entrySet().removeIf(e->e.getValue()+60_000<now);}
 public int pending(){return pending.size();}
}
