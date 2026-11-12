package dev.f1zzen.biomeregions.api.event;

import dev.f1zzen.biomeregions.api.Region;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class RegionLeaveEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Region region;

    public RegionLeaveEvent(Player player, Region region) {
        super(player);
        this.region = region;
    }

    public Region getRegion() {
        return region;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
