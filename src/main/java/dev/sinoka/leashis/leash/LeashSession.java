package dev.sinoka.leashis.leash;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Holder is read from the proxy so vanilla fence transfers cannot leave stale state. */
public record LeashSession(ServerPlayer target, LeashProxyEntity proxy,
                           long attachedAtTick, boolean returnLead) {
    public UUID leashedPlayerId() {
        return target.getUUID();
    }

    public Entity holder() {
        return proxy.getLeashHolder();
    }
}
