package dev.f1zzen.biomeregions;

import dev.f1zzen.biomeregions.api.BiomeRegionsAPI;
import dev.f1zzen.biomeregions.api.Region;
import dev.f1zzen.biomeregions.api.event.PlayerBiomeChangeEvent;
import dev.f1zzen.biomeregions.api.event.RegionEnterEvent;
import dev.f1zzen.biomeregions.api.event.RegionLeaveEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

final class RegionService implements BiomeRegionsAPI {

    static final Pattern ID = Pattern.compile("[a-z0-9_-]+");
    static final Comparator<RegionImpl> ORDER = Comparator.comparingInt(RegionImpl::getPriority).reversed()
            .thenComparingDouble(RegionImpl::volume)
            .thenComparing(RegionImpl::getId);

    record Tracked(RegionImpl region, NamespacedKey biome) {
    }

    private final JavaPlugin plugin;
    private final File regionsFile;
    private final File biomesFile;
    private final Map<String, RegionImpl> regions = new ConcurrentHashMap<>();
    private final Map<NamespacedKey, Map<String, String>> values = new ConcurrentHashMap<>();
    private final Map<UUID, Tracked> tracked = new ConcurrentHashMap<>();

    RegionService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        this.biomesFile = new File(plugin.getDataFolder(), "biomes.yml");
    }

    static Registry<Biome> biomes() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
    }

    static NamespacedKey biomeAt(Location location) {
        Biome biome = location.getWorld().getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return biomes().getKeyOrThrow(biome);
    }

    static boolean isBiome(NamespacedKey key) {
        return biomes().get(key) != null;
    }

    RegionImpl regionAt(Location location) {
        if (location.getWorld() == null) return null;
        NamespacedKey biome = null;
        RegionImpl best = null;
        for (RegionImpl region : regions.values()) {
            if (!region.inBox(location)) continue;
            if (!region.getBiomes().isEmpty()) {
                if (biome == null) biome = biomeAt(location);
                if (!region.getBiomes().contains(biome)) continue;
            }
            if (best == null || ORDER.compare(region, best) < 0) best = region;
        }
        return best;
    }

    RegionImpl region(String id) {
        return regions.get(id);
    }

    Collection<RegionImpl> regions() {
        return regions.values();
    }

    Map<String, String> values(NamespacedKey biome) {
        return new TreeMap<>(values.getOrDefault(biome, Map.of()));
    }

    Tracked tracked(UUID uuid) {
        return tracked.get(uuid);
    }

    void forget(UUID uuid) {
        tracked.remove(uuid);
    }

    void update(Player player, Location location) {
        RegionImpl region = regionAt(location);
        NamespacedKey biome = biomeAt(location);
        Tracked old = tracked.put(player.getUniqueId(), new Tracked(region, biome));
        RegionImpl oldRegion = old == null ? null : old.region();
        NamespacedKey oldBiome = old == null ? null : old.biome();
        if (!Objects.equals(idOf(oldRegion), idOf(region))) {
            if (oldRegion != null) call(new RegionLeaveEvent(player, oldRegion));
            if (region != null) call(new RegionEnterEvent(player, region));
        }
        if (!biome.equals(oldBiome)) call(new PlayerBiomeChangeEvent(player, oldBiome, biome, region));
    }

    void recheckAll() {
        for (Player player : Bukkit.getOnlinePlayers()) update(player, player.getLocation());
    }

    private static String idOf(RegionImpl region) {
        return region == null ? null : region.getId();
    }

    private static void call(Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }

    RegionImpl create(String id, String world, BoundingBox box, int priority) {
        RegionImpl region = new RegionImpl(id, world, box, priority, Set.of());
        regions.put(id, region);
        regionsChanged();
        return region;
    }

    void delete(String id) {
        regions.remove(id);
        regionsChanged();
    }

    void regionsChanged() {
        saveRegions();
        recheckAll();
    }

    void setValue(NamespacedKey biome, String key, String value) {
        values.computeIfAbsent(biome, k -> new ConcurrentHashMap<>()).put(key, value);
        saveBiomes();
    }

    boolean unsetValue(NamespacedKey biome, String key) {
        Map<String, String> map = values.get(biome);
        if (map == null || map.remove(key) == null) return false;
        if (map.isEmpty()) values.remove(biome);
        saveBiomes();
        return true;
    }

    void load() {
        regions.clear();
        values.clear();
        ConfigurationSection section = YamlConfiguration.loadConfiguration(regionsFile).getConfigurationSection("regions");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                List<Integer> min = s == null ? List.of() : s.getIntegerList("min");
                List<Integer> max = s == null ? List.of() : s.getIntegerList("max");
                if (!ID.matcher(id).matches() || min.size() != 3 || max.size() != 3 || s.getString("world") == null) {
                    plugin.getLogger().warning("Skipping invalid region: " + id);
                    continue;
                }
                BoundingBox box = new BoundingBox(min.get(0), min.get(1), min.get(2), max.get(0) + 1, max.get(1) + 1, max.get(2) + 1);
                Set<NamespacedKey> biomes = new HashSet<>();
                for (String raw : s.getStringList("biomes")) {
                    NamespacedKey key = NamespacedKey.fromString(raw);
                    if (key == null) continue;
                    if (!isBiome(key)) plugin.getLogger().warning("Region " + id + ": unknown biome " + raw);
                    biomes.add(key);
                }
                regions.put(id, new RegionImpl(id, s.getString("world"), box, s.getInt("priority"), biomes));
            }
        }
        for (Map<?, ?> entry : YamlConfiguration.loadConfiguration(biomesFile).getMapList("biomes")) {
            NamespacedKey biome = NamespacedKey.fromString(String.valueOf(entry.get("biome")));
            if (biome == null || !(entry.get("values") instanceof Map<?, ?> map)) continue;
            Map<String, String> target = new ConcurrentHashMap<>();
            map.forEach((k, v) -> {
                if (k != null && v != null) target.put(k.toString(), v.toString());
            });
            if (!target.isEmpty()) values.put(biome, target);
        }
    }

    void saveRegions() {
        YamlConfiguration config = new YamlConfiguration();
        for (RegionImpl region : regions.values().stream().sorted(Comparator.comparing(RegionImpl::getId)).toList()) {
            BoundingBox box = region.getBox();
            ConfigurationSection s = config.createSection("regions." + region.getId());
            s.set("world", region.getWorld());
            s.set("min", List.of((int) box.getMinX(), (int) box.getMinY(), (int) box.getMinZ()));
            s.set("max", List.of((int) box.getMaxX() - 1, (int) box.getMaxY() - 1, (int) box.getMaxZ() - 1));
            s.set("priority", region.getPriority());
            s.set("biomes", region.getBiomes().stream().map(NamespacedKey::asString).sorted().toList());
        }
        save(config, regionsFile);
    }

    void saveBiomes() {
        List<Map<String, Object>> list = new ArrayList<>();
        values.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().asString())).forEach(e -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("biome", e.getKey().asString());
            entry.put("values", new TreeMap<>(e.getValue()));
            list.add(entry);
        });
        YamlConfiguration config = new YamlConfiguration();
        config.set("biomes", list);
        save(config, biomesFile);
    }

    private void save(YamlConfiguration config, File file) {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + file.getName(), e);
        }
    }

    @Override
    public Optional<Region> getRegionAt(Location location) {
        return Optional.ofNullable(regionAt(location));
    }

    @Override
    public Optional<Region> getRegion(String id) {
        return Optional.ofNullable(regions.get(id));
    }

    @Override
    public Collection<Region> getRegions() {
        return List.copyOf(regions.values());
    }

    @Override
    public Optional<Region> getRegion(Player player) {
        Tracked t = tracked.get(player.getUniqueId());
        return Optional.ofNullable(t == null ? null : t.region());
    }

    @Override
    public NamespacedKey getBiomeAt(Location location) {
        return biomeAt(location);
    }

    @Override
    public Optional<String> getBiomeValue(NamespacedKey biome, String key) {
        Map<String, String> map = values.get(biome);
        return Optional.ofNullable(map == null ? null : map.get(key));
    }

    @Override
    public Optional<String> getBiomeValue(Location location, String key) {
        return getBiomeValue(biomeAt(location), key);
    }

    @Override
    public int getBiomeInt(NamespacedKey biome, String key, int def) {
        try {
            return getBiomeValue(biome, key).map(s -> Integer.parseInt(s.trim())).orElse(def);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public double getBiomeDouble(NamespacedKey biome, String key, double def) {
        try {
            return getBiomeValue(biome, key).map(s -> Double.parseDouble(s.trim())).orElse(def);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public boolean getBiomeBoolean(NamespacedKey biome, String key, boolean def) {
        return getBiomeValue(biome, key).map(s -> s.trim().toLowerCase())
                .map(s -> s.equals("true") ? Boolean.TRUE : s.equals("false") ? Boolean.FALSE : null)
                .orElse(def);
    }
}
