package dev.f1zzen.biomeregions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

final class Wand implements Listener {

    static final String PERMISSION = "biomeregions.admin";

    record Selection(Location pos1, Location pos2) {
        boolean complete() {
            return pos1 != null && pos2 != null;
        }

        boolean sameWorld() {
            return complete() && pos1.getWorld().equals(pos2.getWorld());
        }

        BoundingBox box() {
            return BoundingBox.of(pos1.getBlock(), pos2.getBlock());
        }
    }

    private final NamespacedKey key;
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();

    Wand(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "wand");
    }

    ItemStack create() {
        ItemStack item = ItemStack.of(Material.GOLDEN_AXE);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Топорик", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text("ЛКМ - точка 1", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("ПКМ - точка 2", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    boolean isWand(ItemStack item) {
        return item != null && item.getType() == Material.GOLDEN_AXE && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    Selection selection(UUID uuid) {
        return selections.get(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null || event.getHand() != EquipmentSlot.HAND || !isWand(event.getItem()) || !player.hasPermission(PERMISSION)) return;
        boolean first;
        switch (event.getAction()) {
            case LEFT_CLICK_BLOCK -> first = true;
            case RIGHT_CLICK_BLOCK -> first = false;
            default -> {
                return;
            }
        }
        event.setCancelled(true);
        Location pos = block.getLocation();
        Selection selection = selections.compute(player.getUniqueId(), (k, old) -> first
                ? new Selection(pos, old == null ? null : old.pos2())
                : new Selection(old == null ? null : old.pos1(), pos));
        String size = selection.sameWorld() ? " <gray>(" + (long) selection.box().getVolume() + " блоков)" : "";
        player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>Точка " + (first ? 1 : 2) + ": <white>"
                + pos.getBlockX() + " " + pos.getBlockY() + " " + pos.getBlockZ() + size));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isWand(event.getPlayer().getInventory().getItemInMainHand()) && event.getPlayer().hasPermission(PERMISSION)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
    }
}
