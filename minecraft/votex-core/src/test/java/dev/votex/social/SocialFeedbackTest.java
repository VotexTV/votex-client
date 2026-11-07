package dev.votex.social;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SocialFeedbackTest {
 @Test void accepted_partner_clears_only_the_matching_outgoing_invitation(){
  var f=new SocialFeedback();var a=UUID.randomUUID();var b=UUID.randomUUID();
  f.invitation(a,"Waiting",100);f.accepted(b);assertEquals("Waiting",f.current(101));
  f.accepted(a);assertEquals("",f.current(102));
  f.invitation(a,"Waiting",200);f.show("Connection error",201);f.accepted(a);assertEquals("Connection error",f.current(202));
 }
 @Test void expiration_and_disconnect_do_not_leak_feedback_to_the_next_session(){
  var f=new SocialFeedback();var peer=UUID.randomUUID();f.invitation(peer,"Waiting",100);
  assertEquals("Waiting",f.current(5_099));assertEquals("",f.current(5_100));
  f.show("Old server",6_000);f.clear();assertEquals("",f.current(6_001));
  f.show("New server",7_000);f.accepted(peer);assertEquals("New server",f.current(7_001));
 }
}
