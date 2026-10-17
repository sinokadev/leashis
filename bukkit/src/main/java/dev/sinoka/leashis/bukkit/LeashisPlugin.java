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
    private static final Map<String, String> POLICIES = Map.of(
            "reconnect", "keepOnLogout", "death", "keepOnDeath",
            "projectile", "protectProjectile", "fencebreak", "protectFenceBreak", "piston", "protectPiston", "interactions", "blockInteractionsWhileLeashed");
    private static final Map<String, String> POLICY_LABELS = Map.of(
            "reconnect", "재접속 시 묶임 유지", "death", "사망 시 울타리 묶임 유지",
            "projectile", "목줄 투사체 보호", "fencebreak", "묶인 플레이어의 울타리 파괴 방지", "piston", "울타리 피스톤 이동 방지", "interactions", "묶인 플레이어 상호작용 차단");

    @Override public void onEnable() {
        proxyKey = new NamespacedKey(this, "proxy");
        saveDefaultConfig();
        loadSettings();
        engine = new LeashEngine(this, () -> settings);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getWorlds().forEach(w -> w.getEntities().stream().filter(this::marked).forEach(Entity::remove));
        getServer().getScheduler().runTaskTimer(this, () -> {
            engine.tick();
            for (Player player : getServer().getOnlinePlayers()) if (blockInteractions(player)) {
                // Close containers opened before attachment or before the option was enabled.
                if (player.getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING
                        && player.getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CREATIVE)
                    player.closeInventory();
            }
        }, 1, 1);
    }
    @Override public void onDisable() { if (engine != null) engine.stop(); }
    private void loadSettings() {
        settings = new Settings(getConfig().getBoolean("enabled", true),
                getConfig().getInt("distanceMin", 4), getConfig().getInt("distanceMax", 10),
                getConfig().getBoolean("allowLeashedRemoveFenceKnot", false),
                getConfig().getBoolean("keepOnLogout", true), getConfig().getBoolean("keepOnDeath", true));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("ll") || command.getName().equalsIgnoreCase("leashis")
                && args.length == 1 && args[0].equalsIgnoreCase("lock")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("플레이어만 사용할 수 있습니다."); return true; }
            if (!sender.hasPermission("leashis.lock")) { sender.sendMessage("이 명령어를 사용할 권한이 없습니다."); return true; }
            if (command.getName().equalsIgnoreCase("ll") && args.length != 0) return false;
            sender.sendMessage("우클릭 목줄 해제 잠금: " + (engine.toggleReleaseLock(player.getUniqueId()) ? "켜짐" : "꺼짐"));
            return true;
        }
        if (command.getName().equalsIgnoreCase("l")) return args.length == 0 && pull(sender);
        if (args.length == 1 && args[0].equalsIgnoreCase("pull")) return pull(sender);
        if (!sender.hasPermission("leashis.admin")) { sender.sendMessage("이 명령어를 사용할 권한이 없습니다."); return true; }
        if (args.length >= 1 && POLICIES.containsKey(args[0].toLowerCase(Locale.ROOT))) {
            String name = args[0].toLowerCase(Locale.ROOT);
            String key = POLICIES.get(name);
            if (args.length > 2) return false;
            if (args.length == 2) {
                if (!args[1].equalsIgnoreCase("true") && !args[1].equalsIgnoreCase("false")) return false;
                getConfig().set(key, Boolean.parseBoolean(args[1]));
                saveConfig(); loadSettings(); engine.tick();
            }
            sender.sendMessage(POLICY_LABELS.get(name) + ": "
                    + (getConfig().getBoolean(key, !name.equals("interactions")) ? "켜짐 (true)" : "꺼짐 (false)"));
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) return false;
        reloadConfig(); loadSettings(); engine.tick(); sender.sendMessage("Leashis 설정을 다시 불러왔습니다."); return true;
    }
    private boolean pull(CommandSender sender) {
        if (!sender.hasPermission("leashis.pull")) { sender.sendMessage("이 명령어를 사용할 권한이 없습니다."); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage("플레이어만 사용할 수 있습니다."); return true; }
        sender.sendMessage(switch (engine.pull(player.getUniqueId())) {
            case STARTED -> "목줄을 당깁니다.";
            case NO_TARGET -> "직접 끌고 있는 플레이어가 없습니다.";
            case COOLDOWN -> "잠시 후 다시 당길 수 있습니다.";
        });
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("l") || command.getName().equalsIgnoreCase("ll")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("leashis.pull")) options.add("pull");
            if (sender.hasPermission("leashis.lock")) options.add("lock");
            if (sender.hasPermission("leashis.admin")) { options.add("reload"); options.addAll(POLICIES.keySet()); }
        } else if (args.length == 2 && sender.hasPermission("leashis.admin")
                && POLICIES.containsKey(args[0].toLowerCase(Locale.ROOT)))
            options.addAll(List.of("true", "false"));
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
    private boolean blockInteractions(Player player) {
        return getConfig().getBoolean("blockInteractionsWhileLeashed", false) && engine.isLeashed(player.getUniqueId());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockedUse(PlayerInteractEvent event) {
        if (blockInteractions(event.getPlayer())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedEntityUse(PlayerInteractEntityEvent event) {
        if (blockInteractions(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedEntityUseAt(PlayerInteractAtEntityEvent event) {
        if (blockInteractions(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && blockInteractions(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (blockInteractions(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedSwap(PlayerSwapHandItemsEvent event) {
        if (blockInteractions(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedConsume(PlayerItemConsumeEvent event) {
        if (blockInteractions(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedInventory(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !blockInteractions(player)) return;
        switch (event.getAction()) {
            case DROP_ALL_CURSOR, DROP_ONE_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT -> { }
            default -> event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && blockInteractions(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && blockInteractions(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedProjectile(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && blockInteractions(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && blockInteractions(player)) event.setCancelled(true);
    }
    private boolean marked(Entity e) { return e.getPersistentDataContainer().has(proxyKey, PersistentDataType.BYTE); }
    @EventHandler public void onChunk(ChunkLoadEvent e) {
        for (Entity entity : e.getChunk().getEntities())
            if (marked(entity) && !proxies.containsKey(entity.getUniqueId())) entity.remove();
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (blockInteractions(e.getPlayer())) { e.setCancelled(true); return; }
        Entity clicked = e.getRightClicked();
        if (clicked instanceof LeashHitch) {
            if (engine.protectKnot(e.getPlayer().getUniqueId()) || engine.releaseLocked(e.getPlayer().getUniqueId())) { e.setCancelled(true); return; }
            // Own every proxy refund. Vanilla may still process unrelated mobs on this knot.
            engine.detachFromHolder(clicked.getUniqueId());
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
        if (blockInteractions(e.getPlayer())) { e.setCancelled(true); return; }
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
        if (protectsProjectileKnot(e.getEntity(), e.getDamager())
                || e.getEntity() instanceof LeashHitch && e.getDamager() instanceof Player p
                && engine.protectKnot(p.getUniqueId())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onKnotBreak(HangingBreakByEntityEvent e) {
        if (protectsProjectileKnot(e.getEntity(), e.getRemover())
                || e.getEntity() instanceof LeashHitch && e.getRemover() instanceof Player p
                && engine.protectKnot(p.getUniqueId())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        if (blockInteractions(e.getPlayer())) { e.setCancelled(true); return; }
        if (!getConfig().getBoolean("protectFenceBreak", true)) return;
        for (LeashEngine.Session session : engine.sessions()) {
            if (!session.target().equals(e.getPlayer().getUniqueId())) continue;
            UUID holderId = holder(session.proxy());
            Entity anchor = holderId == null ? null : getServer().getEntity(holderId);
            if (anchor instanceof LeashHitch && anchor.getWorld().equals(e.getBlock().getWorld())
                    && anchor.getLocation().getBlock().equals(e.getBlock())) e.setCancelled(true);
            return;
        }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (protectsProjectileKnot(event.getHitEntity(), event.getEntity())) event.setCancelled(true);
    }
    private boolean protectsProjectileKnot(Entity entity, Entity source) {
        return getConfig().getBoolean("protectProjectile", true) && entity instanceof LeashHitch && source instanceof Projectile
                && engine.holders().contains(entity.getUniqueId());
    }
    private boolean protectsAnchor(org.bukkit.block.Block block) {
        if (!getConfig().getBoolean("protectPiston", true)) return false;
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
