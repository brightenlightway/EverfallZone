
package com.everfall.everzones;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.util.*;

public final class EverZones extends JavaPlugin implements Listener {
    private final Map<Integer, Zone> pvp = new LinkedHashMap<>();
    private final Map<Integer, Zone> safe = new LinkedHashMap<>();
    private final Map<UUID, Location> pvpPos1 = new HashMap<>();
    private final Map<UUID, Location> safePos1 = new HashMap<>();
    private final Map<UUID, Long> combat = new HashMap<>();
    private int nextPvp = 1, nextSafe = 1;
    private File file;

    private static final TextColor CYAN = TextColor.color(0x00D6FF);

    @Override public void onEnable() {
        file = new File(getDataFolder(), "zones.yml");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        loadZones();
        Objects.requireNonNull(getCommand("pvpzone")).setExecutor(new PvpCommand());
        Objects.requireNonNull(getCommand("safezone")).setExecutor(new SafeCommand());
        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            public void run() {
                long now = System.currentTimeMillis();
                combat.entrySet().removeIf(e -> e.getValue() <= now);
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    @Override public void onDisable() {
        saveZones();
    }

    private Component msg(String s) {
        return Component.text(s).color(CYAN);
    }

    private ItemStack wand(Material mat, String name) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(msg(name));
        i.setItemMeta(m);
        return i;
    }

    private boolean isWand(ItemStack item, Material mat, String name) {
        if (item == null || item.getType() != mat || !item.hasItemMeta()) return false;
        Component d = item.getItemMeta().displayName();
        return d != null && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(d).equals(name);
    }

    private void saveZones() {
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        y.set("next-pvp", nextPvp);
        y.set("next-safe", nextSafe);
        for (var e : pvp.entrySet()) writeZone(y, "pvp." + e.getKey(), e.getValue());
        for (var e : safe.entrySet()) writeZone(y, "safe." + e.getKey(), e.getValue());
        try { y.save(file); } catch (Exception ex) { getLogger().severe("Could not save zones.yml: " + ex.getMessage()); }
    }

    private void writeZone(org.bukkit.configuration.file.YamlConfiguration y, String path, Zone z) {
        y.set(path + ".world", z.world.getName());
        y.set(path + ".minX", z.minX); y.set(path + ".maxX", z.maxX);
        y.set(path + ".minY", z.minY); y.set(path + ".maxY", z.maxY);
        y.set(path + ".minZ", z.minZ); y.set(path + ".maxZ", z.maxZ);
    }

    private void loadZones() {
        if (!file.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        nextPvp = y.getInt("next-pvp", 1);
        nextSafe = y.getInt("next-safe", 1);
        loadGroup(y, "pvp", pvp);
        loadGroup(y, "safe", safe);
    }

    private void loadGroup(org.bukkit.configuration.file.YamlConfiguration y, String group, Map<Integer, Zone> map) {
        var sec = y.getConfigurationSection(group);
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            try {
                int id = Integer.parseInt(k);
                String wn = y.getString(group + "." + k + ".world");
                World w = Bukkit.getWorld(wn);
                if (w == null) continue;
                Zone z = new Zone(w,
                    y.getDouble(group + "." + k + ".minX"),
                    y.getDouble(group + "." + k + ".maxX"),
                    y.getDouble(group + "." + k + ".minY"),
                    y.getDouble(group + "." + k + ".maxY"),
                    y.getDouble(group + "." + k + ".minZ"),
                    y.getDouble(group + "." + k + ".maxZ"));
                map.put(id, z);
            } catch (Exception ignored) {}
        }
    }

    private Zone zoneAt(Location l, Map<Integer, Zone> map) {
        if (l == null || l.getWorld() == null) return null;
        for (Zone z : map.values()) if (z.contains(l)) return z;
        return null;
    }

    private int zoneId(Location l, Map<Integer, Zone> map) {
        for (var e : map.entrySet()) if (e.getValue().contains(l)) return e.getKey();
        return 0;
    }

    private boolean isCombat(Player p) {
        return combat.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    private void tag(Player a, Player b) {
        long until = System.currentTimeMillis() + 16000L;
        combat.put(a.getUniqueId(), until);
        combat.put(b.getUniqueId(), until);
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!p.isOp() || e.getClickedBlock() == null) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (isWand(item, Material.BLAZE_ROD, "PvPZone Wand")) {
            e.setCancelled(true);
            if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                pvpPos1.put(p.getUniqueId(), e.getClickedBlock().getLocation());
                p.sendMessage(msg("PvPZone | Position 1 set."));
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Location a = pvpPos1.get(p.getUniqueId());
                if (a == null) { p.sendMessage(msg("PvPZone | Set Position 1 first.")); return; }
                Location b = e.getClickedBlock().getLocation();
                if (a.getWorld() != b.getWorld()) { p.sendMessage(msg("PvPZone | Both positions must be in the same world.")); return; }
                pvp.put(nextPvp, Zone.from(a,b)); p.sendMessage(msg("PvPZone | Zone " + nextPvp + " created and saved."));
                nextPvp++; pvpPos1.remove(p.getUniqueId()); saveZones();
            }
        } else if (isWand(item, Material.STICK, "SafeZone Wand")) {
            e.setCancelled(true);
            if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                safePos1.put(p.getUniqueId(), e.getClickedBlock().getLocation());
                p.sendMessage(msg("SafeZone | Position 1 set."));
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Location a = safePos1.get(p.getUniqueId());
                if (a == null) { p.sendMessage(msg("SafeZone | Set Position 1 first.")); return; }
                Location b = e.getClickedBlock().getLocation();
                if (a.getWorld() != b.getWorld()) { p.sendMessage(msg("SafeZone | Both positions must be in the same world.")); return; }
                safe.put(nextSafe, Zone.from(a,b)); p.sendMessage(msg("SafeZone | Zone " + nextSafe + " created and saved."));
                nextSafe++; safePos1.remove(p.getUniqueId()); saveZones();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker == null) return;

        if (!attacker.isOp() && zoneAt(attacker.getLocation(), safe) != null && zoneAt(victim.getLocation(), safe) != null) {
            e.setCancelled(true); return;
        }

        if (zoneAt(attacker.getLocation(), pvp) != null && zoneAt(victim.getLocation(), pvp) != null) tag(attacker, victim);
    }

    @EventHandler
    public void move(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Zone from = zoneAt(e.getFrom(), pvp);
        Zone to = zoneAt(e.getTo(), pvp);
        if (from == to) return;

        if (to != null) {
            p.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("PvP Zone").color(CYAN),
                Component.text("Combat is enabled here."),
                net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(500),
                    java.time.Duration.ofMillis(2000),
                    java.time.Duration.ofMillis(500))));
            return;
        }

        if (from != null && isCombat(p)) {
            e.setTo(e.getFrom());
            p.setVelocity(e.getFrom().toVector().subtract(e.getTo().toVector()).normalize().multiply(0.6));
            p.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("COMBAT").color(TextColor.color(0xFF5555)),
                Component.text("You cannot leave while in combat!"),
                net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(100),
                    java.time.Duration.ofMillis(1800),
                    java.time.Duration.ofMillis(500))));
        } else if (from != null) {
            p.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("Zone Left").color(CYAN),
                Component.text("You are safe now."),
                net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(500),
                    java.time.Duration.ofMillis(2000),
                    java.time.Duration.ofMillis(800))));
        }
    }

    @EventHandler
    public void command(PlayerCommandPreprocessEvent e) {
        if (isCombat(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(msg("PvPZone | You cannot use commands while in combat."));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (isCombat(p)) {
            p.setHealth(0);
            combat.remove(p.getUniqueId());
        }
        pvpPos1.remove(p.getUniqueId());
        safePos1.remove(p.getUniqueId());
    }

    @EventHandler
    public void death(org.bukkit.event.entity.PlayerDeathEvent e) {
        combat.remove(e.getPlayer().getUniqueId());
    }

    private final class PvpCommand implements CommandExecutor {
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player p) || !p.isOp()) return true;
            if (a.length == 0 || a[0].equalsIgnoreCase("wand")) {
                p.getInventory().addItem(wand(Material.BLAZE_ROD, "PvPZone Wand"));
                p.sendMessage(msg("PvPZone | Blaze Rod given. Left click = Pos1, Right click = Pos2."));
                return true;
            }
            if (a[0].equalsIgnoreCase("list")) {
                if (pvp.isEmpty()) p.sendMessage(msg("PvPZone | No zones."));
                else pvp.keySet().forEach(id -> p.sendMessage(msg("PvPZone | Zone " + id)));
                return true;
            }
            if (a[0].equalsIgnoreCase("delete") && a.length >= 2) {
                try {
                    int id = Integer.parseInt(a[1]);
                    if (pvp.remove(id) != null) { saveZones(); p.sendMessage(msg("PvPZone | Zone " + id + " deleted.")); }
                    else p.sendMessage(msg("PvPZone | Zone " + id + " does not exist."));
                } catch (NumberFormatException ex) { p.sendMessage(msg("PvPZone | Invalid number.")); }
                return true;
            }
            return true;
        }
    }

    private final class SafeCommand implements CommandExecutor {
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player p) || !p.isOp()) return true;
            if (a.length == 0 || a[0].equalsIgnoreCase("wand")) {
                p.getInventory().addItem(wand(Material.STICK, "SafeZone Wand"));
                p.sendMessage(msg("SafeZone | Stick given. Left click = Pos1, Right click = Pos2."));
                return true;
            }
            if (a[0].equalsIgnoreCase("list")) {
                if (safe.isEmpty()) p.sendMessage(msg("SafeZone | No zones."));
                else safe.keySet().forEach(id -> p.sendMessage(msg("SafeZone | Zone " + id)));
                return true;
            }
            if (a[0].equalsIgnoreCase("delete") && a.length >= 2) {
                try {
                    int id = Integer.parseInt(a[1]);
                    if (safe.remove(id) != null) { saveZones(); p.sendMessage(msg("SafeZone | Zone " + id + " deleted.")); }
                    else p.sendMessage(msg("SafeZone | Zone " + id + " does not exist."));
                } catch (NumberFormatException ex) { p.sendMessage(msg("SafeZone | Invalid number.")); }
                return true;
            }
            return true;
        }
    }

    private record Zone(World world, double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        static Zone from(Location a, Location b) {
            return new Zone(a.getWorld(),
                Math.min(a.getX(),b.getX()), Math.max(a.getX(),b.getX()),
                Math.min(a.getY(),b.getY()), Math.max(a.getY(),b.getY()),
                Math.min(a.getZ(),b.getZ()), Math.max(a.getZ(),b.getZ()));
        }
        boolean contains(Location l) {
            return l.getWorld() == world &&
                l.getX() >= minX && l.getX() <= maxX &&
                l.getY() >= minY && l.getY() <= maxY &&
                l.getZ() >= minZ && l.getZ() <= maxZ;
        }
    }
}
