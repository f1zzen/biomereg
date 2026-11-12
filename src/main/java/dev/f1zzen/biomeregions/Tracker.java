package dev.f1zzen.biomeregions;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

final class Tracker implements Listener {

    private final RegionService service;
    private final Set<UUID> debug = ConcurrentHashMap.newKeySet();

    Tracker(Plugin plugin, RegionService service) {
        this.service = service;
        Bukkit.getScheduler().runTaskTimer(plugin, this::showDebug, 10L, 10L);
    }

    boolean toggleDebug(UUID uuid) {
        if (debug.remove(uuid)) return false;
        debug.add(uuid);
        return true;
    }

    private void showDebug() {
        for (UUID uuid : debug) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            RegionImpl region = service.regionAt(player.getLocation());
            String name = region == null ? "<gray>вне регионов" : "<white>" + region.getId() + " <gray>(" + region.getPriority() + ")";
            player.sendActionBar(MiniMessage.miniMessage().deserialize("<gold>Регион: " + name
                    + " <dark_gray>| <gold>Биом: <white>" + RegionService.biomeAt(player.getLocation()).asString()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock()) service.update(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        service.update(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.update(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        service.update(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.update(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.forget(event.getPlayer().getUniqueId());
        debug.remove(event.getPlayer().getUniqueId());
    }
}
