package dev.votex.social;

import java.util.UUID;

/** Client-thread transient feedback; an accepted invitation must not erase an unrelated error. */
public final class SocialFeedback {
 private String text="";
 private UUID invitationPeer;
 private long until;
 public void show(String value,long now){text=value;invitationPeer=null;until=now+5_000;}
 public void invitation(UUID peer,String value,long now){show(value,now);invitationPeer=peer;}
 public void accepted(UUID peer){if(invitationPeer!=null&&invitationPeer.equals(peer))clear();}
 public String current(long now){if(now>=until){clear();return "";}return text;}
 public void clear(){text="";invitationPeer=null;until=0;}
}
