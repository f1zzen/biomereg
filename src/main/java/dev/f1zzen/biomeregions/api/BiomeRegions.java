package dev.f1zzen.biomeregions.api;

import org.bukkit.Bukkit;

public final class BiomeRegions {

    private BiomeRegions() {
    }

    public static BiomeRegionsAPI api() {
        BiomeRegionsAPI api = Bukkit.getServicesManager().load(BiomeRegionsAPI.class);
        if (api == null) throw new IllegalStateException("BiomeRegions is not enabled");
        return api;
    }
}
