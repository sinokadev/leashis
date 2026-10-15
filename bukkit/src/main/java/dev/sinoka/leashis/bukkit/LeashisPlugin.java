package dev.sinoka.leashis.bukkit;

import dev.sinoka.leashis.core.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Uses only the Bukkit 1.20 API; no CraftBukkit internals or packet-library dependency. */
public final class LeashisPlugin extends JavaPlugin implements Listener, LeashEngine.Port {
    private final Map<UUID, Rabbit> proxies = new HashMap<>();
    private final Map<UUID, UUID> targets = new HashMap<>();
    private final Map<UUID, Entity> leashHolders = new HashMap<>();
    private final Set<UUID> restoringRespawns = new HashSet<>();
    private final Set<UUID> distanceUnlinks = new HashSet<>();
    private NamespacedKey proxyKey;
    private LeashEngine engine;
    private Settings settings;

    @Override public void onEnable() {
        proxyKey = new NamespacedKey(this, "proxy");
        saveDefaultConfig();
        loadSettings();
        engine = new LeashEngine(this, () -> settings);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getWorlds().forEach(w -> w.getEntities().stream().filter(this::marked).forEach(Entity::remove));
        getServer().getScheduler().runTaskTimer(this, engine::tick, 1, 1);
    }
    @Override public void onDisable() { if (engine != null) engine.stop(); }
    private void loadSettings() {
        settings = new Settings(getConfig().getBoolean("enabled", true),
                getConfig().getInt("distanceMin", 4), getConfig().getInt("distanceMax", 10),
                getConfig().getBoolean("allowLeashedRemoveFenceKnot", false),
                getConfig().getBoolean("keepOnLogout", true), getConfig().getBoolean("keepOnDeath", true));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("leashis.admin")) { sender.sendMessage("이 명령어를 사용할 권한이 없습니다."); return true; }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("reconnect") || args[0].equalsIgnoreCase("death"))) {
            boolean death = args[0].equalsIgnoreCase("death");
            if (args.length > 2) return false;
            if (args.length == 2) {
                if (!args[1].equalsIgnoreCase("true") && !args[1].equalsIgnoreCase("false")) return false;
                getConfig().set(death ? "keepOnDeath" : "keepOnLogout", Boolean.parseBoolean(args[1]));
                saveConfig(); loadSettings(); engine.tick();
            }
            sender.sendMessage((death ? "사망 시 울타리 묶임 유지: " : "재접속 시 묶임 유지: ")
                    + ((death ? settings.keepOnDeath() : settings.keepOnLogout()) ? "켜짐 (true)" : "꺼짐 (false)"));
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) return false;
        reloadConfig(); loadSettings(); engine.tick(); sender.sendMessage("Leashis 설정을 다시 불러왔습니다."); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("leashis.admin")) return List.of();
        List<String> options = args.length == 1 ? List.of("reload", "reconnect", "death")
                : args.length == 2 && (args[0].equalsIgnoreCase("reconnect") || args[0].equalsIgnoreCase("death")) ? List.of("true", "false") : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
    private boolean marked(Entity e) { return e.getPersistentDataContainer().has(proxyKey, PersistentDataType.BYTE); }
    @EventHandler public void onChunk(ChunkLoadEvent e) {
        for (Entity entity : e.getChunk().getEntities())
            if (marked(entity) && !proxies.containsKey(entity.getUniqueId())) entity.remove();
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity clicked = e.getRightClicked();
        if (clicked instanceof LeashHitch) {
            if (engine.protectKnot(e.getPlayer().getUniqueId())) { e.setCancelled(true); return; }
            // Own every proxy refund. Vanilla may still process unrelated mobs on this knot.
            for (LeashEngine.Session s : engine.sessions())
                if (clicked.getUniqueId().equals(holder(s.proxy()))) engine.detach(s.target());
            return;
        }
        UUID id = targets.get(clicked.getUniqueId());
        Player target = clicked instanceof Player p ? p : id == null ? null : getServer().getPlayer(id);
        if (target == null) return;
        ItemStack item = e.getPlayer().getInventory().getItem(e.getHand());
        LeashEngine.Interaction result = engine.interact(e.getPlayer().getUniqueId(), target.getUniqueId(),
                item.getType() == Material.LEAD, e.getPlayer().getGameMode() == GameMode.CREATIVE);
        if (result == LeashEngine.Interaction.PASS) return;
        e.setCancelled(true);
        if (result == LeashEngine.Interaction.ATTACHED && e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
            e.getPlayer().getInventory().setItem(e.getHand(), item);
        }
        if (result == LeashEngine.Interaction.ALREADY_BOUND)
            e.getPlayer().sendMessage("이미 다른 곳에 연결된 플레이어입니다.");
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFence(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null
                || !Tag.FENCES.isTagged(e.getClickedBlock().getType()) || e.getHand() != EquipmentSlot.HAND) return;
        List<LeashEngine.Session> held = engine.sessions().stream()
                .filter(s -> e.getPlayer().getUniqueId().equals(holder(s.proxy()))).toList();
        if (held.isEmpty()) return;
        Location center = e.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
        LeashHitch knot = center.getWorld().getNearbyEntities(center, 0.5, 0.5, 0.5).stream()
                .filter(LeashHitch.class::isInstance).map(LeashHitch.class::cast).findFirst()
                .orElseGet(() -> center.getWorld().spawn(center, LeashHitch.class));
        for (LeashEngine.Session s : held) proxies.get(s.proxy()).setLeashHolder(knot);
        // Let vanilla transfer other, ordinary mobs too; it cannot transfer these a second time.
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) { if (marked(e.getEntity())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onKnotDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof LeashHitch && e.getDamager() instanceof Player p
                && engine.protectKnot(p.getUniqueId())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onKnotBreak(HangingBreakByEntityEvent e) {
        if (e.getEntity() instanceof LeashHitch && e.getRemover() instanceof Player p
                && engine.protectKnot(p.getUniqueId())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        for (LeashEngine.Session session : engine.sessions()) {
            if (!session.target().equals(e.getPlayer().getUniqueId())) continue;
            UUID holderId = holder(session.proxy());
            Entity anchor = holderId == null ? null : getServer().getEntity(holderId);
            if (anchor instanceof LeashHitch && anchor.getWorld().equals(e.getBlock().getWorld())
                    && anchor.getLocation().getBlock().equals(e.getBlock())) e.setCancelled(true);
            return;
        }
    }
    private boolean protectsAnchor(org.bukkit.block.Block block) {
        for (UUID holder : engine.holders()) {
            Entity anchor = getServer().getEntity(holder);
            if (anchor instanceof LeashHitch && anchor.getWorld().equals(block.getWorld())
                    && anchor.getLocation().getBlock().equals(block)) return true;
        }
        return false;
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::protectsAnchor)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::protectsAnchor)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onLeash(PlayerLeashEntityEvent e) { if (marked(e.getEntity())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onUnleash(PlayerUnleashEntityEvent e) { if (marked(e.getEntity())) e.setCancelled(true); }
    @EventHandler public void onNativeUnleash(EntityUnleashEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (!proxies.containsKey(id) || e.getReason() != EntityUnleashEvent.UnleashReason.DISTANCE) return;
        Rabbit proxy = proxies.get(id);
        if (proxy.isLeashed()) leashHolders.put(id, proxy.getLeashHolder());
        // Vanilla uses its own fixed leash range. The common engine owns the configurable range.
        distanceUnlinks.add(id);
    }
    @EventHandler public void onDrop(EntityDropItemEvent e) {
        if (marked(e.getEntity())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent e) {
        if (restoringRespawns.contains(e.getPlayer().getUniqueId())) return;
        if (e.getTo() == null || e.getFrom().getWorld().equals(e.getTo().getWorld())) return;
        if (engine.isLeashed(e.getPlayer().getUniqueId())) e.setCancelled(true);
        else engine.detachInvolving(e.getPlayer().getUniqueId());
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent e) { onTeleport(e); }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId(), holder = engine.leashHolder(id);
        engine.death(id, holder != null && getServer().getEntity(holder) instanceof LeashHitch);
    }
    private LeashHitch respawnAnchor(UUID id) {
        if (!engine.awaitingRespawn(id)) return null;
        UUID holder = engine.leashHolder(id);
        Entity entity = holder == null ? null : getServer().getEntity(holder);
        return entity instanceof LeashHitch knot && knot.isValid()
                && Tag.FENCES.isTagged(knot.getLocation().getBlock().getType()) ? knot : null;
    }
    private Location respawnPosition(LeashHitch knot) {
        var origin = knot.getLocation().getBlock();
        for (int dy : new int[] {0, 1, -1, 2, -2}) for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                var feet = origin.getRelative(dx, dy, dz);
                var head = feet.getRelative(0, 1, 0);
                var floor = feet.getRelative(0, -1, 0);
                if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()
                        || !floor.getType().isSolid()) continue;
                if (Set.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.CACTUS,
                        Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
                        Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW).contains(floor.getType())
                        || Set.of(Material.FIRE, Material.SOUL_FIRE, Material.SWEET_BERRY_BUSH,
                        Material.POWDER_SNOW).contains(feet.getType())) continue;
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return origin.getLocation().add(0.5, 1.5, 0.5);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!engine.awaitingRespawn(id)) return;
        LeashHitch knot = respawnAnchor(id);
        if (knot == null) { engine.detach(id); return; }
        e.setRespawnLocation(respawnPosition(knot));
        getServer().getScheduler().runTask(this, () -> {
            Player player = getServer().getPlayer(id);
            if (player != null && !player.isDead()) engine.respawn(id);
        });
    }
    private void completeRespawn(Player player) {
        UUID id = player.getUniqueId();
        if (!engine.awaitingRespawn(id) || player.isDead()) return;
        LeashHitch knot = respawnAnchor(id);
        if (knot == null) { engine.detach(id); return; }
        restoringRespawns.add(id);
        try {
            if (player.teleport(respawnPosition(knot))) engine.respawn(id);
            else engine.detach(id);
        } finally { restoringRespawns.remove(id); }
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { engine.logout(e.getPlayer().getUniqueId()); }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        if (engine.awaitingRespawn(e.getPlayer().getUniqueId()))
            getServer().getScheduler().runTask(this, () -> completeRespawn(e.getPlayer()));
        else engine.resume(e.getPlayer().getUniqueId());
    }
    @EventHandler public void onWorld(PlayerChangedWorldEvent e) {
        if (!engine.awaitingRespawn(e.getPlayer().getUniqueId())) engine.detachInvolving(e.getPlayer().getUniqueId());
    }
    @Override public LeashEngine.Actor actor(UUID id) {
        Entity entity = getServer().getEntity(id);
        if (entity == null) return null;
        boolean valid = entity.isValid() && !entity.isDead() && (!(entity instanceof Player p) || p.isOnline());
        return new LeashEngine.Actor(entity.getWorld().getUID(), vec(entity.getLocation().toVector()),
                vec(entity.getVelocity()), valid, entity instanceof Player p && p.getGameMode() == GameMode.SPECTATOR,
                entity.isInsideVehicle());
    }
    @Override public UUID spawnProxy(UUID targetId, UUID holderId) {
        Player target = getServer().getPlayer(targetId);
        Entity holder = getServer().getEntity(holderId);
        if (target == null || holder == null) return null;
        Rabbit proxy = target.getWorld().spawn(neck(target), Rabbit.class, t -> {
            t.getPersistentDataContainer().set(proxyKey, PersistentDataType.BYTE, (byte)1);
            t.setBaby(); t.setAgeLock(true); t.setInvisible(true); t.setInvulnerable(true); t.setAI(false);
            t.setSilent(true); t.setGravity(false); t.setCollidable(false); t.setPersistent(false);
            t.setRemoveWhenFarAway(false);
        });
        if (!proxy.setLeashHolder(holder)) { proxy.remove(); return null; }
        proxies.put(proxy.getUniqueId(), proxy); targets.put(proxy.getUniqueId(), targetId);
        leashHolders.put(proxy.getUniqueId(), holder);
        return proxy.getUniqueId();
    }
    @Override public boolean proxyValid(UUID id) { Rabbit p = proxies.get(id); return p != null && p.isValid(); }
    @Override public UUID holder(UUID id) {
        Rabbit p = proxies.get(id);
        if (p == null) return null;
        if (p.isLeashed()) {
            Entity holder = p.getLeashHolder(); leashHolders.put(id, holder); return holder.getUniqueId();
        }
        Entity holder = distanceUnlinks.contains(id) ? leashHolders.get(id) : null;
        return holder == null ? null : holder.getUniqueId();
    }
    @Override public void follow(UUID proxy, UUID target) {
        Player p = getServer().getPlayer(target);
        if (p != null) {
            Rabbit rabbit = proxies.get(proxy);
            rabbit.teleport(neck(p)); rabbit.setVelocity(new Vector());
            if (distanceUnlinks.remove(proxy) && !rabbit.isLeashed()) {
                Entity holder = leashHolders.get(proxy);
                if (holder != null && holder.isValid()) rabbit.setLeashHolder(holder);
            }
        }
    }
    @Override public void suspendProxy(UUID id) {
        Rabbit proxy = proxies.get(id);
        if (proxy != null) { proxy.setLeashHolder(null); proxy.remove(); }
    }
    @Override public void removeProxy(UUID id) {
        Rabbit proxy = proxies.remove(id); targets.remove(id); leashHolders.remove(id); distanceUnlinks.remove(id);
        if (proxy != null) { proxy.setLeashHolder(null); proxy.remove(); }
    }
    @Override public void refund(UUID target, UUID proxy) {
        Entity p = getServer().getEntity(target), fallback = proxies.get(proxy);
        Location where = p != null ? p.getLocation() : fallback == null ? null : fallback.getLocation();
        if (where != null) where.getWorld().dropItemNaturally(where, new ItemStack(Material.LEAD));
    }
    @Override public void velocity(UUID id, Vec v) {
        Player p = getServer().getPlayer(id);
        if (p != null) p.setVelocity(new Vector(v.x(), v.y(), v.z()));
    }
    private static Location neck(Player p) { return p.getLocation().add(0, p.getEyeHeight() - 0.3, 0); }
    private static Vec vec(Vector v) { return new Vec(v.getX(), v.getY(), v.getZ()); }
}
