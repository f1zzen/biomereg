package dev.f1zzen.biomeregions.api;

import java.util.Collection;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/*
    основа
    Optional<Region> getRegionAt(Location location) - регион, на локации;
    Optional<Region> getRegion(String id); - просто регион зная айди
    Optional<Region> getRegion(Player p) - регион где находится игрок. ес чо не нагружает
    NamespacedKey getBiomeAt(Location loc) - конкретный биом майнкрафта + кастомный выдаёт
    
    Optional<String> getBiomeValue(NamespacedKey biome, String key)
    Optional<String> getBiomeValue(Location loc, String key)  
    int getBiomeInt(NamespacedKey biome, String key, int def)
    double  getBiomeDouble(NamespacedKey biome, String key, double def)
    boolean getBiomeBoolean(NamespacedKey biome, String key, boolean def)

    то что выше это как доп прикол. условно, можешь сделать биому (через biomes.yml) температуру
    и проверять в своём плагине температуру какого-то биома. или "уровень опасности".
    можно ещё добавить значение где мобы сильнее, где эффекты. ну кароче подумаешь
*/
public interface BiomeRegionsAPI {

    Optional<Region> getRegionAt(Location location);

    Optional<Region> getRegion(String id);

    Collection<Region> getRegions();

    Optional<Region> getRegion(Player player);

    NamespacedKey getBiomeAt(Location location);

    Optional<String> getBiomeValue(NamespacedKey biome, String key);

    Optional<String> getBiomeValue(Location location, String key);

    int getBiomeInt(NamespacedKey biome, String key, int def);

    double getBiomeDouble(NamespacedKey biome, String key, double def);

    boolean getBiomeBoolean(NamespacedKey biome, String key, boolean def);
}
