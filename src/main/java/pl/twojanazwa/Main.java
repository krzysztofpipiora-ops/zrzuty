package pl.twojanazwa;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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

public class Main extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<Location, Integer> activeDrops = new HashMap<>();
    private int lastSpawnHour = -1;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("zrzut").setExecutor(this);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                checkSchedule();
            }
        }.runTaskTimer(this, 0L, 400L);
    }

    private void checkSchedule() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        if (hour >= 18 && hour <= 22) {
            if (minute == 0 && hour != lastSpawnHour) {
                spawnSupplyDrop();
                lastSpawnHour = hour;
            }
        }
    }

    // Metoda wywołująca zrzut (używana przez automat i komendę)
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
        
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" §6§l[ZRZUT] §fNowy zrzut pojawił się na mapie!");
        Bukkit.broadcastMessage(" §7Lokalizacja: §eX: " + x + " Y: " + (y + 1) + " Z: " + z);
        Bukkit.broadcastMessage(" ");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("zrzut.admin")) {
            sender.sendMessage("§cNie masz uprawnień!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("spawn")) {
            spawnSupplyDrop();
            sender.sendMessage("§aPomyślnie wywołano zrzut zaopatrzenia!");
            return true;
        }

        sender.sendMessage("§6Użycie: §f/zrzut spawn");
        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;
        
        Location loc = block.getLocation();
        if (activeDrops.containsKey(loc)) {
            event.setCancelled(true);
            return;
        }

        activeDrops.put(loc, 0);
        startOpeningProcess(loc);
        event.setCancelled(true); 
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
        
        inv.addItem(new ItemStack(Material.DIAMOND, r.nextInt(5) + 3));
        inv.addItem(new ItemStack(Material.GOLD_INGOT, r.nextInt(10) + 5));
        inv.addItem(new ItemStack(Material.NETHERITE_SCRAP, r.nextInt(2) + 1));
        if (r.nextInt(100) < 10) inv.addItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
        
        Bukkit.broadcastMessage("§6§l[ZRZUT] §aZrzut został przejęty!");
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
