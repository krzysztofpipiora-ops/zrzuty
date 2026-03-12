package pl.twojanazwa;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;

public class Main extends JavaPlugin implements Listener, CommandExecutor {

    // Lista lokalizacji, w których aktualnie leżą zrzuty
    private final Set<Location> supplyDropLocations = new HashSet<>();
    // Mapa postępu otwierania (Lokalizacja -> Sekundy)
    private final Map<Location, Integer> openingProgress = new HashMap<>();
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

    private void spawnSupplyDrop() {
        World world = Bukkit.getWorld("world");
        if (world == null) return;
        
        Random random = new Random();
        int x = random.nextInt(2001) - 1000;
        int z = random.nextInt(2001) - 1000;
        int y = world.getHighestBlockYAt(x, z);
        
        Location dropLoc = new Location(world, x, y + 1, z);
        Block block = dropLoc.getBlock();
        block.setType(Material.CHEST);
        
        // Oznaczamy skrzynię jako ZRZUT
        supplyDropLocations.add(dropLoc);
        world.strikeLightningEffect(dropLoc);
        
        Bukkit.broadcastMessage("§6§l[ZRZUT] §fZaopatrzenie: §eX: " + x + " Y: " + (y + 1) + " Z: " + z);
        
        // Efekt wizualny co 30 sekund
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!supplyDropLocations.contains(dropLoc)) {
                    this.cancel();
                    return;
                }
                spawnFirework(dropLoc);
            }
        }.runTaskTimer(this, 0L, 600L);
    }

    private void spawnFirework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc.clone().add(0.5, 1, 0.5), Firework.class);
        FireworkMeta fwm = fw.getFireworkMeta();
        fwm.addEffect(FireworkEffect.builder().withColor(Color.ORANGE).with(FireworkEffect.Type.BALL_LARGE).build());
        fwm.setPower(1);
        fw.setFireworkMeta(fwm);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("zrzut.admin")) return true;
        if (args.length > 0 && args[0].equalsIgnoreCase("spawn")) {
            spawnSupplyDrop();
            sender.sendMessage("§aZespawnowano zrzut!");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;
        
        Location loc = block.getLocation();
        
        // SPRAWDZENIE: Czy to jest zrzut?
        if (!supplyDropLocations.contains(loc)) return;

        // Jeśli to zrzut, blokujemy otwarcie ekwipunku i startujemy proces
        event.setCancelled(true);
        
        if (openingProgress.containsKey(loc)) return;

        openingProgress.put(loc, 0);
        startOpeningProcess(loc);
    }

    private void startOpeningProcess(Location loc) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!openingProgress.containsKey(loc)) {
                    this.cancel();
                    return;
                }
                
                int progress = openingProgress.get(loc);
                List<Player> nearby = new ArrayList<>();
                for (Entity e : loc.getWorld().getNearbyEntities(loc, 10, 10, 10)) {
                    if (e instanceof Player) nearby.add((Player) e);
                }

                if (nearby.size() > 1) {
                    for (Player p : nearby) sendAction(p, "§c§lWALKA O ZRZUT! §7(Pauza)");
                    return;
                }
                
                if (nearby.isEmpty()) {
                    openingProgress.remove(loc);
                    this.cancel();
                    return;
                }
                
                if (progress >= 300) {
                    finishDrop(loc);
                    openingProgress.remove(loc);
                    supplyDropLocations.remove(loc); // Usuwamy z listy zrzutów po otwarciu
                    this.cancel();
                    return;
                }
                
                openingProgress.put(loc, progress + 1);
                for (Player p : nearby) {
                    sendAction(p, "§eOtwieranie zrzutu: §6" + (progress * 100 / 300) + "%");
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
        inv.addItem(new ItemStack(Material.NETHERITE_SCRAP, r.nextInt(2) + 1));
        
        Bukkit.broadcastMessage("§6§l[ZRZUT] §aZrzut został otwarty!");
        
        // Teraz gracze mogą normalnie otworzyć tę skrzynię (bo usunęliśmy ją z supplyDropLocations)
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (isNear(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isNear(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean isNear(Location loc) {
        for (Location dropLoc : supplyDropLocations) {
            if (dropLoc.getWorld().equals(loc.getWorld()) && dropLoc.distance(loc) <= 10) return true;
        }
        return false;
    }
}
