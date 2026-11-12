package dev.f1zzen.biomeregions.api;

import java.util.Set;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.util.BoundingBox;

// база

public interface Region {

    String getId();

    String getWorld();

    BoundingBox getBox();

    int getPriority();

    Set<NamespacedKey> getBiomes();

    boolean contains(Location location);
}
