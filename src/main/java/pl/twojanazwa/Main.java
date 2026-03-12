package pl.twojanazwa;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;

public class Main extends JavaPlugin implements Listener {

    private final Map<Location, Integer> activeDrops = new HashMap<>();
    private int lastSpawnHour = -1;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        new BukkitRunnable() {
            @Override
            public void run() {
                checkSchedule();
            }
        }.runTaskTimer(this, 0L, 1200L);
    }

    private void checkSchedule() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (hour >= 18 && hour <= 22) {
            if (hour != lastSpawnHour) {
                spawnSupplyDrop();
                lastSpawnHour = hour;
            }
        }
    }

    private void spawnSupplyDrop() {
        World world = Bukkit.getWorld("world");
        if (world == null) return;
        Random random = new Random();
        int x = random.nextInt(2001) - 1000;
        int z = random.nextInt(2001) - 1000;
        int y = world.getHighestBlockYAt(x, z);
        Location dropLoc = new Location(world, x, y + 1, z);
        dropLoc.getBlock().setType(Material.CHEST);
        world.strikeLightningEffect(dropLoc);
        Bukkit.broadcastMessage("§6§l[ZRZUT] §fZaopatrzenie: §eX: " + x + " Y: " + (y + 1) + " Z: " + z);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;
        Location loc = block.getLocation();
        if (activeDrops.containsKey(loc)) return;
        activeDrops.put(loc, 0);
        startOpeningProcess(loc);
    }

    private void startOpeningProcess(Location loc) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeDrops.containsKey(loc)) {
                    this.cancel();
                    return;
                }
                int progress = activeDrops.get(loc);
                List<Player> nearby = new ArrayList<>();
                for (Entity e : loc.getWorld().getNearbyEntities(loc, 10, 10, 10)) {
                    if (e instanceof Player) nearby.add((Player) e);
                }

                if (nearby.size() > 1) {
                    for (Player p : nearby) sendAction(p, "§c§lWALKA O ZRZUT! §7(Pauza)");
                    return;
                }
                if (nearby.isEmpty()) {
                    activeDrops.remove(loc);
                    this.cancel();
                    return;
                }
                if (progress >= 300) {
                    finishDrop(loc);
                    activeDrops.remove(loc);
                    this.cancel();
                    return;
                }
                activeDrops.put(loc, progress + 1);
                for (Player p : nearby) {
                    sendAction(p, "§eOtwieranie: §6" + (progress * 100 / 300) + "%");
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void sendAction(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private void finishDrop(Location loc) {
        if (!(loc.getBlock().getState() instanceof Chest)) return;
        Chest chest = (Chest) loc.getBlock().getState();
        Inventory inv = chest.getInventory();
        Random r = new Random();
        inv.addItem(new ItemStack(Material.DIAMOND, r.nextInt(8) + 4));
        inv.addItem(new ItemStack(Material.GOLD_INGOT, r.nextInt(12) + 6));
        inv.addItem(new ItemStack(Material.OBSIDIAN, r.nextInt(16) + 8));
        if (r.nextBoolean()) inv.addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        Bukkit.broadcastMessage("§6§l[ZRZUT] §aZrzut został otwarty!");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (isNear(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isNear(event.getBlock().getLocation())) event.setCancelled(true);
    }

    private boolean isNear(Location loc) {
        for (Location dropLoc : activeDrops.keySet()) {
            if (dropLoc.getWorld().equals(loc.getWorld()) && dropLoc.distance(loc) <= 10) return true;
        }
        return false;
    }
}
