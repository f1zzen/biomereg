package dev.f1zzen.biomeregions;

import dev.f1zzen.biomeregions.api.BiomeRegionsAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class BiomeRegionsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        RegionService service = new RegionService(this);
        service.load();
        Wand wand = new Wand(this);
        Tracker tracker = new Tracker(this, service);
        getServer().getPluginManager().registerEvents(wand, this);
        getServer().getPluginManager().registerEvents(tracker, this);
        RegionCommand command = new RegionCommand(service, wand, tracker);
        PluginCommand rg = getCommand("rg");
        rg.setExecutor(command);
        rg.setTabCompleter(command);
        getServer().getServicesManager().register(BiomeRegionsAPI.class, service, this, ServicePriority.Normal);
        service.recheckAll();
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }
}
