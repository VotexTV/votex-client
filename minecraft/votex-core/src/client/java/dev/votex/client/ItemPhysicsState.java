package dev.votex.client;
/** Per-extraction primitive data, attached to the existing item render state; never retains an entity. */
public interface ItemPhysicsState {
 void votex$grounded(boolean grounded);
 boolean votex$grounded();
}
