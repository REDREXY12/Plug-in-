package com.youtuberrecs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class YouTuberRecs extends JavaPlugin implements Listener, TabExecutor {
    private final Map<UUID, UUID> selected = new HashMap<>();
    private final Map<UUID, String> modes = new HashMap<>();
    private final Map<UUID, String> targets = new HashMap<>();
    private final Map<String, ItemStack[]> kits = new HashMap<>();
    private NamespacedKey npcKey;
    private File kitsFile;
    private YamlConfiguration kitsConfig;

    @Override public void onEnable() {
        npcKey = new NamespacedKey(this, "npc");
        getCommand("npc").setExecutor(this);
        getCommand("npc").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        loadKits();
        new BukkitRunnable() {
            double angle = 0;
            @Override public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (Entity e : world.getEntities()) {
                        if (!(e instanceof Mob mob) || !isNpc(e)) continue;
                        String mode = modes.get(e.getUniqueId());
                        String targetName = targets.get(e.getUniqueId());
                        if (mode == null || targetName == null) continue;
                        Player target = Bukkit.getPlayerExact(targetName);
                        if (target == null || !target.isOnline() || !target.getWorld().equals(e.getWorld())) continue;
                        if (mode.equals("follow")) {
                            if (mob.getLocation().distanceSquared(target.getLocation()) > 2.5) mob.getPathfinder().moveTo(target.getLocation(), 1.15);
                        } else if (mode.equals("circle")) {
                            angle += 0.18;
                            Location p = target.getLocation().clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
                            mob.getPathfinder().moveTo(p, 1.0);
                        } else if (mode.equals("attack")) {
                            mob.setTarget(target);
                            if (mob.getLocation().distanceSquared(target.getLocation()) > 2.0) mob.getPathfinder().moveTo(target.getLocation(), 1.2);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 1L, 10L);
        getLogger().info("YouTuber Recs enabled.");
    }

    @Override public void onDisable() { saveKits(); }

    private boolean isNpc(Entity e) { return e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE); }
    private void markNpc(Entity e) { e.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte)1); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Run this command in-game."); return true; }
        if (!p.hasPermission("youtuberrecs.admin")) { p.sendMessage(ChatColor.RED + "You don't have permission."); return true; }
        if (args.length == 0) {
            p.sendMessage(ChatColor.GOLD + "YouTuber Recs commands:");
            p.sendMessage(ChatColor.YELLOW + "/npc spawn <number> <name>");
            p.sendMessage(ChatColor.YELLOW + "/npc select <nearby NPC>");
            p.sendMessage(ChatColor.YELLOW + "/npc inventory | rename <name>");
            p.sendMessage(ChatColor.YELLOW + "/npc follow <player> | circle <player> | kill <player>");
            p.sendMessage(ChatColor.YELLOW + "/npc kit | kit create/load/clear/list/delete");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "spawn" -> {
                    if (args.length < 3) { p.sendMessage(ChatColor.RED + "Usage: /npc spawn <number> <name>"); return true; }
                    int count = Math.max(1, Math.min(50, Integer.parseInt(args[1])));
                    String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    for (int i=0; i<count; i++) {
                        Location loc = p.getLocation().clone().add((i % 5) - 2, 0, (i / 5) + 1);
                        Entity e = p.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.VILLAGER);
                        e.setCustomName(ChatColor.GREEN + name + (count > 1 ? " " + (i+1) : ""));
                        e.setCustomNameVisible(true);
                        markNpc(e);
                    }
                    p.sendMessage(ChatColor.GREEN + "Spawned " + count + " NPC-like villagers. Look at one and use /npc select.");
                }
                case "select" -> {
                    Entity nearest = p.getNearbyEntities(8, 5, 8).stream().filter(this::isNpc)
                        .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation()))).orElse(null);
                    if (nearest == null) { p.sendMessage(ChatColor.RED + "No YouTuber Recs NPC nearby."); return true; }
                    selected.put(p.getUniqueId(), nearest.getUniqueId());
                    p.sendMessage(ChatColor.GREEN + "Selected NPC: " + ChatColor.stripColor(nearest.getCustomName() == null ? "NPC" : nearest.getCustomName()));
                }
                case "inventory" -> {
                    Entity e = getSelected(p);
                    if (!(e instanceof LivingEntity le)) { p.sendMessage(ChatColor.RED + "Select a living NPC first: /npc select"); return true; }
                    Inventory inv = Bukkit.createInventory(p, 27, "NPC Inventory");
                    if (le.getEquipment() != null) {
                        ItemStack[] armor = le.getEquipment().getArmorContents();
                        for (int i=0; i<Math.min(4, armor.length); i++) if (armor[i] != null) inv.setItem(18+i, armor[i]);
                        if (le.getEquipment().getItemInMainHand() != null) inv.setItem(0, le.getEquipment().getItemInMainHand());
                        if (le.getEquipment().getItemInOffHand() != null) inv.setItem(1, le.getEquipment().getItemInOffHand());
                    }
                    p.openInventory(inv);
                    p.sendMessage(ChatColor.GRAY + "NPC inventory view: main hand slot 1, offhand slot 2, armor slots 19-22. Use /npc kit to equip saved kits.");
                }
                case "rename" -> {
                    Entity e = getSelected(p);
                    if (e == null || args.length < 2) { p.sendMessage(ChatColor.RED + "Select an NPC and use /npc rename <name>"); return true; }
                    e.setCustomName(ChatColor.GREEN + String.join(" ", Arrays.copyOfRange(args,1,args.length)));
                    e.setCustomNameVisible(true); p.sendMessage(ChatColor.GREEN + "NPC renamed.");
                }
                case "follow", "circle", "kill" -> {
                    if (args.length < 2) { p.sendMessage(ChatColor.RED + "Usage: /npc " + sub + " <player>"); return true; }
                    Entity e = getSelected(p);
                    if (!(e instanceof Mob)) { p.sendMessage(ChatColor.RED + "Select an NPC first with /npc select."); return true; }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) { p.sendMessage(ChatColor.RED + "That player must be online."); return true; }
                    modes.put(e.getUniqueId(), sub.equals("kill") ? "attack" : sub);
                    targets.put(e.getUniqueId(), target.getName());
                    p.sendMessage(ChatColor.GREEN + "NPC set to " + sub + " " + target.getName() + ".");
                }
                case "stop" -> {
                    Entity e = getSelected(p); if (e != null) { modes.remove(e.getUniqueId()); targets.remove(e.getUniqueId()); if (e instanceof Mob m) { m.setTarget(null); m.getPathfinder().stopPathfinding(); } }
                    p.sendMessage(ChatColor.GREEN + "NPC stopped.");
                }
                case "kit" -> handleKit(p, Arrays.copyOfRange(args,1,args.length));
                default -> p.sendMessage(ChatColor.RED + "Unknown subcommand. Use /npc");
            }
        } catch (NumberFormatException ex) { p.sendMessage(ChatColor.RED + "Please enter a valid number."); }
        return true;
    }

    private Entity getSelected(Player p) {
        UUID id = selected.get(p.getUniqueId());
        if (id == null) return null;
        Entity e = Bukkit.getEntity(id);
        if (e == null || !e.isValid() || !isNpc(e)) { selected.remove(p.getUniqueId()); return null; }
        return e;
    }

    private void handleKit(Player p, String[] args) {
        if (args.length == 0) {
            p.sendMessage(ChatColor.GOLD + "Kit commands: /npc kit create <name>, load <name>, clear, list, delete <name>");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "Usage: /npc kit create <name>"); return; }
                String name = String.join(" ", Arrays.copyOfRange(args,1,args.length)).toLowerCase(Locale.ROOT);
                kits.put(name, p.getInventory().getContents().clone());
                saveKits(); p.sendMessage(ChatColor.GREEN + "Saved kit '" + name + "' from your inventory.");
            }
            case "load" -> {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "Usage: /npc kit load <name>"); return; }
                String name = String.join(" ", Arrays.copyOfRange(args,1,args.length)).toLowerCase(Locale.ROOT);
                ItemStack[] kit = kits.get(name); Entity e = getSelected(p);
                if (kit == null) { p.sendMessage(ChatColor.RED + "Kit not found."); return; }
                if (!(e instanceof LivingEntity le) || le.getEquipment() == null) { p.sendMessage(ChatColor.RED + "Select a living NPC first."); return; }
                ItemStack main = kit.length > 0 ? kit[0] : null;
                ItemStack off = kit.length > 40 ? kit[40] : null;
                le.getEquipment().setItemInMainHand(cloneOrNull(main));
                le.getEquipment().setItemInOffHand(cloneOrNull(off));
                ItemStack[] armor = new ItemStack[4];
                if (kit.length > 36) for (int i=0;i<4;i++) armor[i] = cloneOrNull(kit[36+i]);
                le.getEquipment().setArmorContents(armor);
                p.sendMessage(ChatColor.GREEN + "Loaded kit '" + name + "' onto selected NPC (weapon, offhand, armor).");
            }
            case "clear" -> {
                Entity e = getSelected(p);
                if (!(e instanceof LivingEntity le) || le.getEquipment() == null) { p.sendMessage(ChatColor.RED + "Select a living NPC first."); return; }
                le.getEquipment().clear(); p.sendMessage(ChatColor.GREEN + "Cleared NPC equipment.");
            }
            case "list" -> p.sendMessage(ChatColor.GOLD + "Kits: " + (kits.isEmpty() ? "none" : String.join(", ", kits.keySet())));
            case "delete" -> {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "Usage: /npc kit delete <name>"); return; }
                String name = String.join(" ", Arrays.copyOfRange(args,1,args.length)).toLowerCase(Locale.ROOT);
                if (kits.remove(name) == null) p.sendMessage(ChatColor.RED + "Kit not found.");
                else { saveKits(); p.sendMessage(ChatColor.GREEN + "Deleted kit '" + name + "'."); }
            }
            default -> p.sendMessage(ChatColor.RED + "Use create, load, clear, list, or delete.");
        }
    }
    private ItemStack cloneOrNull(ItemStack i) { return i == null ? null : i.clone(); }

    private void loadKits() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        kitsFile = new File(getDataFolder(), "kits.yml");
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
        ConfigurationSection sec = kitsConfig.getConfigurationSection("kits");
        if (sec != null) for (String name : sec.getKeys(false)) {
            Object obj = sec.get(name);
            if (obj instanceof ItemStack[] items) kits.put(name, items);
            else if (obj instanceof List<?> list) {
                ItemStack[] items = list.stream().map(v -> v instanceof ItemStack i ? i : null).toArray(ItemStack[]::new);
                kits.put(name, items);
            }
        }
    }
    private void saveKits() {
        if (kitsConfig == null) return;
        kitsConfig.set("kits", null);
        for (Map.Entry<String, ItemStack[]> entry : kits.entrySet()) kitsConfig.set("kits." + entry.getKey(), Arrays.asList(entry.getValue()));
        try { kitsConfig.save(kitsFile); } catch (IOException e) { getLogger().severe("Could not save kits: " + e.getMessage()); }
    }

    @EventHandler public void onDeath(EntityDeathEvent event) {
        if (isNpc(event.getEntity())) event.getDrops().clear();
    }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("NPC Inventory")) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) p.sendMessage(ChatColor.GRAY + "This basic build displays equipment only. Use kits to equip the NPC.");
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("spawn","select","inventory","rename","follow","circle","kill","stop","kit"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("kit")) return filter(List.of("create","load","clear","list","delete"), args[1]);
        return Collections.emptyList();
    }
    private List<String> filter(List<String> values, String prefix) {
        return values.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).collect(Collectors.toList());
    }
                      }
