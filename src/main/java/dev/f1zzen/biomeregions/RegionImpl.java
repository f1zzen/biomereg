package dev.f1zzen.biomeregions;

import dev.f1zzen.biomeregions.api.Region;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.util.BoundingBox;

final class RegionImpl implements Region {

    private final String id;
    private volatile String world;
    private volatile BoundingBox box;
    private volatile int priority;
    private volatile Set<NamespacedKey> biomes;

    RegionImpl(String id, String world, BoundingBox box, int priority, Set<NamespacedKey> biomes) {
        this.id = id;
        this.world = world;
        this.box = box.clone();
        this.priority = priority;
        this.biomes = Set.copyOf(biomes);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getWorld() {
        return world;
    }

    @Override
    public BoundingBox getBox() {
        return box.clone();
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Set<NamespacedKey> getBiomes() {
        return biomes;
    }

    @Override
    public boolean contains(Location location) {
        return inBox(location) && (biomes.isEmpty() || biomes.contains(RegionService.biomeAt(location)));
    }

    boolean inBox(Location location) {
        return location.getWorld() != null
                && location.getWorld().getName().equals(world)
                && box.contains(location.getX(), location.getY(), location.getZ());
    }

    double volume() {
        return box.getVolume();
    }

    void define(String world, BoundingBox box) {
        this.world = world;
        this.box = box.clone();
    }

    void setPriority(int priority) {
        this.priority = priority;
    }

    void setBiomes(Set<NamespacedKey> biomes) {
        this.biomes = Set.copyOf(biomes);
    }
}
