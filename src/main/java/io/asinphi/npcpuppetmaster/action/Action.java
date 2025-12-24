package io.asinphi.npcpuppetmaster.action;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum Action {
    POSSESS,
    UNPOSSESS,
    FOLLOW,
    UNFOLLOW,
    MOVE_HERE,
    GO_AWAY,
    DELETE;

    public static final StreamCodec<ByteBuf, Action> CODEC =
            ByteBufCodecs.idMapper(id -> Action.values()[id], Action::ordinal);
}
