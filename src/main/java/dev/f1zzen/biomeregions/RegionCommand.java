package dev.f1zzen.biomeregions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

final class RegionCommand implements TabExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS = List.of("wand", "create", "delete", "list", "info", "addbiome",
            "removebiome", "setpriority", "redefine", "biome", "debug", "test", "reload");
    private static final Set<String> REGION_ARG = Set.of("delete", "info", "addbiome", "removebiome", "setpriority", "redefine");

    private final RegionService service;
    private final Wand wand;
    private final Tracker tracker;

    RegionCommand(RegionService service, Wand wand, Tracker tracker) {
        this.service = service;
        this.wand = wand;
        this.tracker = tracker;
    }

    private static void msg(CommandSender sender, String template, Object... args) {
        Object[] escaped = Arrays.stream(args).map(a -> MM.escapeTags(String.valueOf(a))).toArray();
        sender.sendMessage(MM.deserialize(template.formatted(escaped)));
    }

    private static void err(CommandSender sender, String template, Object... args) {
        msg(sender, "<red>" + template, args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(Wand.PERMISSION)) {
            err(sender, "Недостаточно прав.");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> wand(sender);
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "addbiome", "removebiome" -> changeBiomes(sender, args);
            case "setpriority" -> setPriority(sender, args);
            case "redefine" -> redefine(sender, args);
            case "biome" -> biome(sender, args);
            case "debug" -> debug(sender);
            case "test" -> test(sender, args);
            case "reload" -> {
                service.load();
                service.recheckAll();
                msg(sender, "<green>Перезагружено: <white>%s</white> регионов.", service.regions().size());
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<gold>BiomeRegions:"));
        for (String line : List.of(
                "/rg wand для топорика",
                "/rg create <id> [priority] - регион из выделения",
                "/rg delete <id>, /rg list, /rg info [id]",
                "/rg addbiome|removebiome <id> <biome|here>",
                "/rg setpriority <id> <n>, /rg redefine <id>",
                "/rg biome set <biome|here> <key> <value>",
                "/rg biome unset <biome|here> <key>, /rg biome info <biome|here>",
                "/rg debug, /rg test [игрок], /rg reload")) {
            msg(sender, "<yellow>%s", line);
        }
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        err(sender, "Команда только для игрока.");
        return null;
    }

    private RegionImpl region(CommandSender sender, String id) {
        RegionImpl region = service.region(id.toLowerCase(Locale.ROOT));
        if (region == null) err(sender, "Регион <white>%s</white> не найден.", id);
        return region;
    }

    private NamespacedKey biome(CommandSender sender, String arg) {
        if (arg.equalsIgnoreCase("here")) {
            Player player = player(sender);
            return player == null ? null : RegionService.biomeAt(player.getLocation());
        }
        NamespacedKey key = NamespacedKey.fromString(arg.toLowerCase(Locale.ROOT));
        if (key == null || !RegionService.isBiome(key)) {
            err(sender, "Неизвестный биом: <white>%s</white>", arg);
            return null;
        }
        return key;
    }

    private Wand.Selection selection(Player player) {
        Wand.Selection selection = wand.selection(player.getUniqueId());
        if (selection == null || !selection.complete()) {
            err(player, "Сначала выдели две точки топориком (/rg wand).");
            return null;
        }
        if (!selection.sameWorld()) {
            err(player, "Точки выделения в разных мирах.");
            return null;
        }
        return selection;
    }

    private static Integer parseInt(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            err(sender, "Не число: <white>%s</white>", raw);
            return null;
        }
    }

    private static String describe(BoundingBox box) {
        return "%d %d %d - %d %d %d".formatted((int) box.getMinX(), (int) box.getMinY(), (int) box.getMinZ(),
                (int) box.getMaxX() - 1, (int) box.getMaxY() - 1, (int) box.getMaxZ() - 1);
    }

    private static String coords(Location loc) {
        return loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }

    private void wand(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return;
        player.getInventory().addItem(wand.create()).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        msg(player, "<green>Топорик выдан! <gray>Использование можно найти в описании топорика.");
    }

    private void create(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) return;
        if (args.length < 2) {
            err(player, "Использование: /rg create \\<id> [priority]");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!RegionService.ID.matcher(id).matches()) {
            err(player, "id может содержать только a-z, 0-9, _ и -.");
            return;
        }
        if (service.region(id) != null) {
            err(player, "Регион <white>%s</white> уже существует.", id);
            return;
        }
        Integer priority = args.length > 2 ? parseInt(player, args[2]) : Integer.valueOf(0);
        if (priority == null) return;
        Wand.Selection selection = selection(player);
        if (selection == null) return;
        RegionImpl region = service.create(id, selection.pos1().getWorld().getName(), selection.box(), priority);
        msg(player, "<green>Регион <white>%s</white> создан: <white>%s</white> [%s], %s блоков, priority %s.",
                id, region.getWorld(), describe(region.getBox()), (long) region.volume(), priority);
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            err(sender, "Использование: /rg delete \\<id>");
            return;
        }
        RegionImpl region = region(sender, args[1]);
        if (region == null) return;
        service.delete(region.getId());
        msg(sender, "<green>Регион <white>%s</white> удалён.", region.getId());
    }

    private void list(CommandSender sender) {
        List<RegionImpl> regions = service.regions().stream().sorted(Comparator.comparing(RegionImpl::getId)).toList();
        if (regions.isEmpty()) {
            msg(sender, "<gray>Регионов нет.");
            return;
        }
        msg(sender, "<gold>Регионы (%s):", regions.size());
        for (RegionImpl r : regions) {
            msg(sender, "<yellow>%s <gray>- %s, priority %s, биомов: %s", r.getId(), r.getWorld(), r.getPriority(),
                    r.getBiomes().isEmpty() ? "все" : r.getBiomes().size());
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Player player = player(sender);
            if (player == null) return;
            Location loc = player.getLocation();
            RegionImpl region = service.regionAt(loc);
            msg(player, "<gold>Биом: <white>%s", RegionService.biomeAt(loc).asString());
            if (region == null) msg(player, "<gold>Регион: <gray>вне регионов");
            else msg(player, "<gold>Регион: <white>%s</white> <gray>(priority %s)", region.getId(), region.getPriority());
            return;
        }
        RegionImpl r = region(sender, args[1]);
        if (r == null) return;
        BoundingBox box = r.getBox();
        msg(sender, String.join("\n<reset>",
                "<gold>Регион <white>%s",
                "<gray>Мир: <white>%s",
                "<gray>Кубоид: <white>%s</white> (%sx%sx%s, %s блоков)",
                "<gray>Priority: <white>%s",
                "<gray>Биомы: <white>%s"),
                r.getId(), r.getWorld(), describe(box), (int) box.getWidthX(), (int) box.getHeight(), (int) box.getWidthZ(),
                (long) r.volume(), r.getPriority(),
                r.getBiomes().isEmpty() ? "все" : r.getBiomes().stream().map(NamespacedKey::asString).sorted().collect(Collectors.joining(", ")));
    }

    private void changeBiomes(CommandSender sender, String[] args) {
        boolean add = args[0].equalsIgnoreCase("addbiome");
        if (args.length < 3) {
            err(sender, "Использование: /rg %s \\<id> \\<biome|here>", args[0].toLowerCase(Locale.ROOT));
            return;
        }
        RegionImpl region = region(sender, args[1]);
        if (region == null) return;
        NamespacedKey known = add ? null : NamespacedKey.fromString(args[2].toLowerCase(Locale.ROOT));
        NamespacedKey biome = known != null && region.getBiomes().contains(known) ? known : biome(sender, args[2]);
        if (biome == null) return;
        Set<NamespacedKey> biomes = new HashSet<>(region.getBiomes());
        if (add ? !biomes.add(biome) : !biomes.remove(biome)) {
            err(sender, add ? "Биом <white>%s</white> уже в регионе." : "Биома <white>%s</white> нет в регионе.", biome.asString());
            return;
        }
        region.setBiomes(biomes);
        service.regionsChanged();
        msg(sender, add ? "<green>Биом <white>%s</white> добавлен в <white>%s</white>." : "<green>Биом <white>%s</white> убран из <white>%s</white>.",
                biome.asString(), region.getId());
        if (!add && biomes.isEmpty()) msg(sender, "<yellow>Список биомов пуст - регион снова действует во всех биомах.");
    }

    private void setPriority(CommandSender sender, String[] args) {
        if (args.length < 3) {
            err(sender, "Использование: /rg setpriority \\<id> \\<n>");
            return;
        }
        RegionImpl region = region(sender, args[1]);
        if (region == null) return;
        Integer priority = parseInt(sender, args[2]);
        if (priority == null) return;
        region.setPriority(priority);
        service.regionsChanged();
        msg(sender, "<green>Priority <white>%s</white> = <white>%s</white>.", region.getId(), priority);
    }

    private void redefine(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) return;
        if (args.length < 2) {
            err(player, "Использование: /rg redefine \\<id>");
            return;
        }
        RegionImpl region = region(player, args[1]);
        if (region == null) return;
        Wand.Selection selection = selection(player);
        if (selection == null) return;
        region.define(selection.pos1().getWorld().getName(), selection.box());
        service.regionsChanged();
        msg(player, "<green>Регион <white>%s</white> переопределён: <white>%s</white> [%s], %s блоков.",
                region.getId(), region.getWorld(), describe(region.getBox()), (long) region.volume());
    }

    private void biome(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        int need = switch (action) {
            case "set" -> 5;
            case "unset" -> 4;
            case "info" -> 3;
            default -> -1;
        };
        if (need < 0 || args.length < need) {
            err(sender, "Использование: /rg biome set \\<biome|here> \\<key> \\<value> | unset \\<biome|here> \\<key> | info \\<biome|here>");
            return;
        }
        NamespacedKey biome = biome(sender, args[2]);
        if (biome == null) return;
        switch (action) {
            case "set" -> {
                String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                service.setValue(biome, args[3], value);
                msg(sender, "<green>%s: <white>%s</white> = <white>%s", biome.asString(), args[3], value);
            }
            case "unset" -> {
                if (service.unsetValue(biome, args[3])) msg(sender, "<green>%s: ключ <white>%s</white> удалён.", biome.asString(), args[3]);
                else err(sender, "У биома %s нет ключа <white>%s</white>.", biome.asString(), args[3]);
            }
            default -> {
                Map<String, String> values = service.values(biome);
                msg(sender, "<gold>Биом <white>%s</white>%s", biome.asString(), values.isEmpty() ? " <gray>- значений нет" : ":");
                values.forEach((k, v) -> msg(sender, "<yellow>%s <gray>= <white>%s", k, v));
            }
        }
    }

    private void debug(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return;
        msg(player, tracker.toggleDebug(player.getUniqueId()) ? "<green>Отладка включена." : "<yellow>Отладка выключена.");
    }

    private void test(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                msg(sender, "<gray>Онлайн никого нет.");
                return;
            }
            msg(sender, "<gold>Онлайн (%s):", Bukkit.getOnlinePlayers().size());
            for (Player p : Bukkit.getOnlinePlayers()) {
                Location loc = p.getLocation();
                RegionImpl region = service.regionAt(loc);
                msg(sender, "<yellow>%s <gray>- %s [%s], %s, %s", p.getName(), loc.getWorld().getName(), coords(loc),
                        RegionService.biomeAt(loc).asString(), region == null ? "вне регионов" : "регион " + region.getId());
            }
            return;
        }
        Player p = Bukkit.getPlayer(args[1]);
        if (p == null) {
            err(sender, "Игрок <white>%s</white> не в сети.", args[1]);
            return;
        }
        Location loc = p.getLocation();
        NamespacedKey biome = RegionService.biomeAt(loc);
        Map<String, String> values = service.values(biome);
        RegionImpl winner = service.regionAt(loc);
        msg(sender, "<gold>Тест: <white>%s", p.getName());
        msg(sender, "<gray>Мир: <white>%s</white>, координаты: <white>%s", loc.getWorld().getName(), coords(loc));
        msg(sender, "<gray>Биом: <white>%s", biome.asString());
        msg(sender, "<gray>Значения: <white>%s", values.isEmpty() ? "нет" : values.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
        if (winner == null) msg(sender, "<gray>Итог: <white>вне регионов");
        else msg(sender, "<gray>Итог: <green>%s</green> (priority %s, %s блоков)", winner.getId(), winner.getPriority(), (long) winner.volume());

        List<RegionImpl> inBox = service.regions().stream().filter(r -> r.inBox(loc)).sorted(RegionService.ORDER).toList();
        if (inBox.isEmpty()) msg(sender, "<gray>Ни в один кубоид не попадает.");
        else msg(sender, "<gray>Кубоиды с игроком:");
        for (RegionImpl r : inBox) {
            String reason;
            if (r == winner) reason = "<green>победил";
            else if (!r.getBiomes().isEmpty() && !r.getBiomes().contains(biome)) reason = "<red>в кубоиде, но биом не в списке";
            else if (r.getPriority() != winner.getPriority())
                reason = "<red>проиграл по priority (" + r.getPriority() + " < " + winner.getPriority() + ")";
            else if (r.volume() != winner.volume())
                reason = "<red>проиграл по объёму (" + (long) r.volume() + " > " + (long) winner.volume() + ")";
            else reason = "<red>проиграл по id при равных priority и объёме";
            msg(sender, " <yellow>%s <gray>[p %s, %s бл.] - " + reason, r.getId(), r.getPriority(), (long) r.volume());
        }

        RegionService.Tracked cached = service.tracked(p.getUniqueId());
        String cachedId = cached == null || cached.region() == null ? null : cached.region().getId();
        String winnerId = winner == null ? null : winner.getId();
        if (cached == null) msg(sender, "<gray>Кэш: <red>записи нет");
        else if (Objects.equals(cachedId, winnerId) && biome.equals(cached.biome())) msg(sender, "<gray>Кэш: <green>совпадает");
        else msg(sender, "<gray>Кэш: <red>расходится</red> - в кэше регион <white>%s</white>, биом <white>%s",
                    cachedId == null ? "нет" : cachedId, cached.biome().asString());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(Wand.PERMISSION)) return List.of();
        String sub = args[0].toLowerCase(Locale.ROOT);
        Stream<String> options = Stream.empty();
        if (args.length == 1) options = SUBCOMMANDS.stream();
        else if (args.length == 2 && REGION_ARG.contains(sub)) options = service.regions().stream().map(RegionImpl::getId);
        else if (args.length == 2 && sub.equals("biome")) options = Stream.of("set", "unset", "info");
        else if (args.length == 2 && sub.equals("test")) options = Bukkit.getOnlinePlayers().stream().map(Player::getName);
        else if (args.length == 3 && sub.equals("addbiome")) options = biomeOptions();
        else if (args.length == 3 && sub.equals("removebiome")) {
            RegionImpl region = service.region(args[1].toLowerCase(Locale.ROOT));
            options = Stream.concat(Stream.of("here"), region == null ? Stream.empty() : region.getBiomes().stream().map(NamespacedKey::asString));
        } else if (args.length == 3 && sub.equals("biome")) options = biomeOptions();
        else if (args.length == 4 && sub.equals("biome") && !args[1].equalsIgnoreCase("info")) {
            NamespacedKey key = args[2].equalsIgnoreCase("here") && sender instanceof Player p
                    ? RegionService.biomeAt(p.getLocation()) : NamespacedKey.fromString(args[2].toLowerCase(Locale.ROOT));
            options = key == null ? Stream.empty() : service.values(key).keySet().stream();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        options.filter(o -> {
            String lower = o.toLowerCase(Locale.ROOT);
            return lower.startsWith(prefix) || lower.substring(lower.indexOf(':') + 1).startsWith(prefix);
        }).sorted().forEach(result::add);
        return result;
    }

    private static Stream<String> biomeOptions() {
        return Stream.concat(Stream.of("here"), RegionService.biomes().keyStream().map(NamespacedKey::asString));
    }
}
