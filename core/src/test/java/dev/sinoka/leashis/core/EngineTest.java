package dev.sinoka.leashis.core;

import java.util.*;
import static dev.sinoka.leashis.core.LeashEngine.Interaction.*;

/** Dependency-free regression suite shared by every distribution. */
public final class EngineTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final class Fixture implements LeashEngine.Port {
        final UUID owner = UUID.randomUUID(), target = UUID.randomUUID(), stranger = UUID.randomUUID();
        final Map<UUID, LeashEngine.Actor> actors = new HashMap<>();
        final Map<UUID, UUID> holders = new HashMap<>();
        Settings settings = Settings.DEFAULT;
        int refunds, spawns, removals;
        boolean failSpawn;
        final LeashEngine engine = new LeashEngine(this, () -> settings);
        Fixture() { position(owner, 1); position(target, 0); position(stranger, 2); }
        void position(UUID id, double x) {
            actors.put(id, new LeashEngine.Actor(WORLD, new Vec(x,0,0), Vec.ZERO, true, false, false));
        }
        void state(UUID id, boolean valid, boolean spectator, boolean riding, UUID world) {
            var a = actors.get(id);
            actors.put(id, new LeashEngine.Actor(world,a.position(),a.velocity(),valid,spectator,riding));
        }
        boolean attach(boolean creative) { return engine.interact(owner,target,true,creative) == ATTACHED; }
        UUID proxy() { return engine.sessions().iterator().next().proxy(); }
        public LeashEngine.Actor actor(UUID id) { return actors.get(id); }
        public UUID spawnProxy(UUID target, UUID holder) {
            if (failSpawn) return null;
            UUID id=UUID.randomUUID(); holders.put(id,holder); spawns++; return id;
        }
        public boolean proxyValid(UUID id) { return holders.containsKey(id); }
        public UUID holder(UUID id) { return holders.get(id); }
        public void follow(UUID proxy, UUID target) { }
        public void suspendProxy(UUID id) { holders.remove(id); }
        public void removeProxy(UUID id) { holders.remove(id); removals++; }
        public void refund(UUID target, UUID proxy) { refunds++; }
        public void velocity(UUID id, Vec velocity) {
            var a=actors.get(id); actors.put(id,new LeashEngine.Actor(a.world(),a.position(),velocity,a.valid(),a.spectator(),a.riding()));
        }
    }
    public static void main(String[] args) {
        Fixture f=new Fixture();
        assert f.attach(false);
        assert f.engine.interact(f.owner,f.target,true,false)==HANDLED;
        assert f.spawns==1;
        assert f.engine.interact(f.stranger,f.target,true,false)==ALREADY_BOUND;
        assert f.engine.interact(f.owner,f.owner,true,false)==PASS;
        f.engine.advanceTo(21);
        assert f.engine.interact(f.owner,f.target,false,true)==HANDLED;
        assert !f.engine.isLeashed(f.target) && f.refunds==1;
        assert f.engine.interact(f.owner,f.target,true,false)==HANDLED;
        f.engine.stop(); assert f.refunds==1;

        f=new Fixture(); assert f.attach(true); f.engine.stop(); assert f.refunds==0;
        f=new Fixture(); f.failSpawn=true; assert !f.attach(false); assert f.spawns==0;
        f=new Fixture(); assert f.attach(false); f.engine.tick(); assert f.actor(f.target).velocity().equals(Vec.ZERO);
        f.position(f.owner,6); for(int i=0;i<100;i++) f.engine.tick();
        assert f.actor(f.target).velocity().x()>0 && f.actor(f.target).velocity().length()<=1.500000001;
        f.position(f.owner,11); f.engine.tick(); assert f.refunds==1 && !f.engine.isLeashed(f.target);

        f=new Fixture(); f.settings=new Settings(true,0,1,false); f.position(f.owner,0);
        assert f.attach(false); f.engine.tick(); assert f.actor(f.target).velocity().equals(Vec.ZERO);
        assert new Settings(true,100,10,false).distanceMin()==9;
        assert new Settings(true,-1,0,false).distanceMax()==1;
        for(int reason=0;reason<6;reason++) {
            f=new Fixture(); assert f.attach(false);
            switch(reason) {
                case 0 -> f.state(f.target,false,false,false,WORLD);
                case 1 -> f.state(f.owner,false,false,false,WORLD);
                case 2 -> f.state(f.target,true,true,false,WORLD);
                case 3 -> f.state(f.target,true,false,true,WORLD);
                case 4 -> f.state(f.owner,true,false,false,UUID.randomUUID());
                case 5 -> f.settings=new Settings(false,4,10,false);
            }
            f.engine.tick(); f.engine.detach(f.target); f.engine.stop(); assert f.refunds==1 && f.removals==1;
        }
        f=new Fixture(); assert f.attach(false); assert f.engine.protectKnot(f.target);
        UUID knot=UUID.randomUUID(); f.position(knot,3); f.holders.put(f.proxy(),knot);
        f.engine.detachInvolving(f.owner); assert f.engine.isLeashed(f.target);
        f.engine.tick(); f.holders.remove(f.proxy()); f.engine.tick(); assert f.refunds==1;
        f=new Fixture(); assert f.attach(false);
        assert f.engine.interact(f.owner,f.stranger,true,false)==ATTACHED;
        f.engine.detachInvolving(f.owner); assert f.refunds==2 && f.removals==2;
        // Offline targets keep their session without a proxy or an early refund.
        f=new Fixture(); assert f.attach(false); UUID old=f.proxy();
        f.engine.logout(f.target); f.actors.remove(f.target);
        for (int i=0;i<100;i++) f.engine.tick();
        assert f.engine.isLeashed(f.target) && f.engine.protectKnot(f.target);
        assert !f.proxyValid(old) && f.refunds==0;
        assert f.engine.involves(f.owner) && f.engine.involves(f.target);
        f.position(f.target,0); f.engine.resume(f.target); f.engine.tick();
        assert f.engine.isLeashed(f.target) && !f.proxy().equals(old) && f.spawns==2 && f.refunds==0;
        f.engine.resume(f.target); assert f.spawns==2;
        f.engine.stop(); assert f.refunds==1;
        // Fence holder identity and creative refund ownership survive reconnects.
        f=new Fixture(); assert f.attach(true); knot=UUID.randomUUID(); f.position(knot,3);
        f.holders.put(f.proxy(),knot); f.engine.logout(f.target);
        f.position(f.target,0); f.engine.resume(f.target);
        assert knot.equals(f.holder(f.proxy()));
        assert f.engine.holders().contains(knot);
        f.engine.logout(f.target); assert f.engine.holders().contains(knot);
        f.engine.stop(); assert f.refunds==0 && f.engine.holders().isEmpty();
        for (int reason=0;reason<5;reason++) {
            f=new Fixture(); assert f.attach(false); f.engine.logout(f.target);
            switch(reason) {
                case 0 -> f.engine.logout(f.owner);
                case 1 -> { f.settings=new Settings(false,4,10,false); f.engine.tick(); }
                case 2 -> { f.failSpawn=true; f.engine.resume(f.target); }
                case 3 -> { f.state(f.target,true,false,false,UUID.randomUUID()); f.engine.resume(f.target); }
                case 4 -> f.engine.detachInvolving(f.owner);
            }
            assert !f.engine.isLeashed(f.target) && f.refunds==1;
            f.engine.stop(); assert f.refunds==1;
        }
        f=new Fixture(); assert f.attach(false); f.engine.logout(f.target);
        f.engine.stop(); f.engine.stop(); assert f.refunds==1;
        // Disabled reconnect retention restores the old logout/refund behavior.
        f=new Fixture(); f.settings=new Settings(true,4,10,false,false); assert f.attach(false);
        f.engine.logout(f.target); assert !f.engine.isLeashed(f.target) && f.refunds==1;
        f.engine.resume(f.target); assert f.spawns==1;
        // Changing policy also releases already suspended sessions exactly once.
        f=new Fixture(); assert f.attach(false); f.engine.logout(f.target);
        f.settings=new Settings(true,4,10,false,false); f.engine.tick();
        assert !f.engine.isLeashed(f.target) && f.refunds==1;
        f.engine.stop(); assert f.refunds==1;
        f=new Fixture(); assert f.attach(false); f.engine.logout(f.target);
        f.settings=new Settings(true,4,10,false,false); f.engine.resume(f.target);
        assert !f.engine.isLeashed(f.target) && f.refunds==1;
        // Fence deaths preserve paid ownership even when logout retention is disabled.
        f=new Fixture(); f.settings=new Settings(true,4,10,false,false); assert f.attach(false);
        knot=UUID.randomUUID(); f.position(knot,2); f.holders.put(f.proxy(),knot);
        f.engine.death(f.target,true); f.state(f.target,false,false,false,WORLD);
        for (int i=0;i<100;i++) f.engine.tick();
        assert f.engine.awaitingRespawn(f.target) && f.engine.isLeashed(f.target) && f.refunds==0;
        assert f.engine.holders().contains(knot);
        f.engine.resume(f.target); assert f.engine.awaitingRespawn(f.target);
        f.position(f.target,1); f.engine.respawn(f.target); f.engine.tick();
        assert f.engine.isLeashed(f.target) && !f.engine.awaitingRespawn(f.target) && f.refunds==0;
        assert knot.equals(f.holder(f.proxy()));
        f.engine.death(f.target,true); f.engine.death(f.target,false);
        assert f.engine.awaitingRespawn(f.target) && f.refunds==0;
        f.engine.detach(f.target); assert f.refunds==1;
        f.engine.respawn(f.target); assert !f.engine.isLeashed(f.target);
        // Player-held deaths still release both the incoming and outgoing leashes.
        f=new Fixture(); assert f.attach(false);
        assert f.engine.attach(f.target,f.stranger,true); f.engine.death(f.target,false);
        assert !f.engine.isLeashed(f.target) && !f.engine.isLeashed(f.stranger) && f.refunds==2;
        f.engine.respawn(f.target); assert f.spawns==2;
        // A missing fence while waiting cannot revive a stale connection.
        f=new Fixture(); assert f.attach(false); knot=UUID.randomUUID(); f.position(knot,2);
        f.holders.put(f.proxy(),knot); f.engine.death(f.target,true); f.actors.remove(knot); f.engine.tick();
        assert !f.engine.isLeashed(f.target) && f.refunds==1;
        // Death retention is independently configurable and releases exactly once when disabled.
        f=new Fixture(); f.settings=new Settings(true,4,10,false,true,false); assert f.attach(false);
        knot=UUID.randomUUID(); f.position(knot,2); f.holders.put(f.proxy(),knot);
        f.engine.death(f.target,true); f.engine.respawn(f.target);
        assert !f.engine.isLeashed(f.target) && !f.engine.awaitingRespawn(f.target) && f.refunds==1 && f.spawns==1;
        for (boolean tick : new boolean[] {true,false}) {
            f=new Fixture(); assert f.attach(false); knot=UUID.randomUUID(); f.position(knot,2);
            f.holders.put(f.proxy(),knot); f.engine.death(f.target,true);
            f.settings=new Settings(true,4,10,false,true,false);
            assert !f.engine.awaitingRespawn(f.target);
            if (tick) f.engine.tick(); else f.engine.respawn(f.target);
            assert !f.engine.isLeashed(f.target) && f.refunds==1 && f.spawns==1;
            f.engine.stop(); assert f.refunds==1;
        }
        System.out.println("LeashEngine regression suite passed");
    }
}
