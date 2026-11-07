package dev.votex.social;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
/** Both ends explicitly advertise support before any social data is sent. */
public record SocialPayload(String json) implements CustomPacketPayload {
 public static final Type<SocialPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("votex_client","social_v1"));
 public static final StreamCodec<RegistryFriendlyByteBuf,SocialPayload> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,SocialRules.MAX_WIRE),b->new SocialPayload(b.readUtf(SocialRules.MAX_WIRE)));
 private static boolean registered;
 public SocialPayload {if(json==null||json.length()>SocialRules.MAX_WIRE||json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>30_000)throw new IllegalArgumentException("Oversized social payload");}
 public static synchronized void register(){if(registered)return;registered=true;PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);PayloadTypeRegistry.serverboundPlay().register(TYPE,CODEC);}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
