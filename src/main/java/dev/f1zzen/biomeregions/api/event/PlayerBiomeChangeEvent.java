package dev.f1zzen.biomeregions.api.event;

import dev.f1zzen.biomeregions.api.Region;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerBiomeChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final NamespacedKey from;
    private final NamespacedKey to;
    private final Region region;

    public PlayerBiomeChangeEvent(Player player, @Nullable NamespacedKey from, NamespacedKey to, @Nullable Region region) {
        super(player);
        this.from = from;
        this.to = to;
        this.region = region;
    }

    public @Nullable NamespacedKey getFromBiome() {
        return from;
    }

    public NamespacedKey getToBiome() {
        return to;
    }

    public @Nullable Region getRegion() {
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
