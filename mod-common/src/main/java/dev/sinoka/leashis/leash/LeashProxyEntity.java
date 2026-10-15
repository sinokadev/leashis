package dev.sinoka.leashis.leash;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

/** A transient vanilla entity supplies the leash renderer to unmodified clients. */
public final class LeashProxyEntity extends Rabbit {
    public static final String TEAM_NAME = "leashis_proxy";
    private final ServerPlayer target;

    public LeashProxyEntity(ServerPlayer target) {
        super(EntityType.RABBIT, target.serverLevel());
        this.target = target;
        setBaby(true);
        setInvisible(true);
        setInvulnerable(true);
        setNoAi(true);
        setSilent(true);
        setNoGravity(true);
        noPhysics = true;
        followTarget();
    }

    public ServerPlayer target() {
        return target;
    }

    public void joinCollisionTeam() {
        Scoreboard scoreboard = target.serverLevel().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.addPlayerTeam(TEAM_NAME);
        }
        team.setCollisionRule(Team.CollisionRule.NEVER);
        scoreboard.addPlayerToTeam(getScoreboardName(), team);
    }

    public void followTarget() {
        setPos(target.getX(), target.getY() + target.getEyeHeight() - 0.3, target.getZ());
    }

    @Override
    public void tick() {
        // Do not run Mob.tick: it would apply vanilla leash physics and item drops again.
        if (target.isRemoved() || !target.isAlive() || target.hasDisconnected()
                || target.level() != level()) {
            discard();
        } else {
            followTarget();
        }
    }

    @Override
    public void dropLeash(boolean broadcastPacket, boolean dropItem) {
        // The session owns the consumed item, including creative-mode accounting.
        super.dropLeash(broadcastPacket, false);
    }

    @Override
    public void remove(RemovalReason reason) {
        dropLeash(true, false);
        Scoreboard scoreboard = ((net.minecraft.server.level.ServerLevel) level()).getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(getScoreboardName());
        if (team != null && TEAM_NAME.equals(team.getName())) {
            scoreboard.removePlayerFromTeam(getScoreboardName(), team);
        }
        super.remove(reason);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean saveAsPassenger(CompoundTag tag) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity other) {
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    public void removeProxy() {
        discard();
    }
}
