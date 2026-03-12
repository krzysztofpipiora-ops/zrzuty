package pl.twojanazwa;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
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

import java.util.*;

public class Main extends JavaPlugin implements Listener {

    private final Map<Location, Integer> activeDrops = new HashMap<>();
    private int lastSpawnHour = -1;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // Timer sprawdzający czas (co 1 minutę)
        new BukkitRunnable() {
            @Override
            public void run() {
                checkSchedule();
            }
        }.runTaskTimer(this, 0L, 1200L);
        
        getLogger().info("Plugin na zrzuty został uruchomiony!");
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
        // Border 2000x2000 (od -1000 do 1000)
        int x = random.nextInt(2001) - 1000;
        int z = random.nextInt(2001) - 1000;
        int y = world.getHighestBlockYAt(x, z);

        Location dropLoc = new Location(world, x, y + 1, z);
        dropLoc.getBlock().setType(Material.CHEST);
        
        world.strikeLightningEffect(dropLoc);
        
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(" §6§l[ZRZUT] §fZaopatrzenie wylądowało!");
        Bukkit.broadcastMessage(" §7Koordynaty: §eX: " + x + " §7Y: " + (y + 1) + " §7Z: " + z);
        Bukkit.broadcastMessage(" §7Przejmowanie trwa 5 minut. Powodzenia!");
        Bukkit.broadcastMessage(" ");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        Location loc = block.getLocation();
        if (activeDrops.containsKey(loc)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cTen zrzut jest już w trakcie otwierania!");
            return;
        }

        // Rozpoczęcie otwierania (tylko jeśli skrzynia nie ma jeszcze postępu)
        activeDrops.put(loc, 0);
        startOpeningProcess(loc, event.getPlayer());
    }

    private void startOpeningProcess(Location loc, Player opener) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeDrops.containsKey(loc)) {
                    this.cancel();
                    return;
                }

                int progress = activeDrops.get(loc);
                Collection<Player> nearby = loc.getNearbyPlayers(10);

                // Logika pauzy: więcej niż 1 osoba w promieniu 10 kratek
                if (nearby.size() > 1) {
                    for (Player p : nearby) {
                        p.sendActionBar("§c§lWALKA O ZRZUT! §7(Pauza)");
                    }
                    return;
                }

                // Jeśli nikt nie stoi w promieniu 10 kratek - reset
                if (nearby.isEmpty()) {
                    activeDrops.remove(loc);
                    this.cancel();
                    return;
                }

                // 300 sekund = 5 minut
                if (progress >= 300) {
                    finishDrop(loc);
                    activeDrops.remove(loc);
                    this.cancel();
                    return;
                }

                activeDrops.put(loc, progress + 1);
                for (Player p : nearby) {
                    p.sendActionBar("§eOtwieranie: §6" + (progress * 100 / 300) + "% §7(" + (300 - progress) + "s)");
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void finishDrop(Location loc) {
        Block block = loc.getBlock();
        if (block.getType() != Material.CHEST) return;

        Chest chest = (Chest) block.getState();
        Inventory inv = chest.getInventory();
        Random r = new Random();

        // Standardowy drop
        inv.addItem(new ItemStack(Material.DIAMOND, r.nextInt(8) + 4));
        inv.addItem(new ItemStack(Material.GOLD_INGOT, r.nextInt(12) + 6));
        inv.addItem(new ItemStack(Material.OBSIDIAN, r.nextInt(16) + 8));
        inv.addItem(new ItemStack(Material.ENDER_PEARL, r.nextInt(4) + 2));
        inv.addItem(new ItemStack(Material.GOLDEN_APPLE, r.nextInt(3) + 1));

        // Rzadki drop
        if (r.nextBoolean()) inv.addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        if (r.nextInt(100) < 20) inv.addItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
        if (r.nextInt(100) < 30) inv.addItem(new ItemStack(Material.NETHERITE_SCRAP, r.nextInt(2) + 1));

        Bukkit.broadcastMessage("§6§l[ZRZUT] §aSkrzynia na koordynatach " + loc.getBlockX() + ", " + loc.getBlockZ() + " została otwarta!");
    }

    // Blokada niszczenia i stawiania bloków w promieniu 10 kratek
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (isNearActiveDrop(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie możesz niszczyć terenu wokół zrzutu!");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isNearActiveDrop(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie możesz budować wokół zrzutu!");
        }
    }

    private boolean isNearActiveDrop(Location loc) {
        for (Location dropLoc : activeDrops.keySet()) {
            if (dropLoc.getWorld().equals(loc.getWorld()) && dropLoc.distance(loc) <= 10) {
                return true;
            }
        }
        return false;
    }
}
