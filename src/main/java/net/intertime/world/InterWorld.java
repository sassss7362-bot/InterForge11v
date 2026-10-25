package net.intertime.world;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InterWorld extends JavaPlugin {

    private static final String P = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "InterWorld " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // світи створюємо після повного запуску сервера
        Bukkit.getScheduler().runTask(this, () -> {
            for (String name : getConfig().getStringList("worlds")) {
                if (Bukkit.getWorld(name) == null) createWorld(name, null);
            }
        });
    }

    // Для bukkit.yml: worlds: <назва>: generator: InterWorld
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new InterGenerator();
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(String worldName, String id) {
        return new InterBiomeProvider();
    }

    private World createWorld(String name, Long seed) {
        WorldCreator wc = new WorldCreator(name)
                .generator(new InterGenerator())
                .biomeProvider(new InterBiomeProvider());
        if (seed != null) wc.seed(seed);
        return wc.createWorld();
    }

    private void say(CommandSender s, String msg) {
        s.sendMessage(P + msg);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("intertime.world.admin")) {
            say(s, ChatColor.RED + "Немає прав.");
            return true;
        }
        String sub = a.length == 0 ? "help" : a[0].toLowerCase();
        switch (sub) {
            case "create": {
                if (a.length < 2 || !a[1].matches("[A-Za-z0-9_\\-]{1,32}")) {
                    say(s, "Використання: /iw create <назва> [seed]  (латиниця, цифри, _ -)");
                    return true;
                }
                String name = a[1];
                if (Bukkit.getWorld(name) != null) {
                    say(s, ChatColor.RED + "Світ з такою назвою вже існує.");
                    return true;
                }
                Long seed = null;
                if (a.length > 2) {
                    try {
                        seed = Long.parseLong(a[2]);
                    } catch (NumberFormatException ex) {
                        seed = (long) a[2].hashCode();
                    }
                }
                say(s, "Створюю світ " + ChatColor.WHITE + name + ChatColor.GRAY + "... (можливе короткe підвисання)");
                World w = createWorld(name, seed);
                if (w == null) {
                    say(s, ChatColor.RED + "Не вдалося створити світ.");
                    return true;
                }
                List<String> list = new ArrayList<>(getConfig().getStringList("worlds"));
                if (!list.contains(name)) {
                    list.add(name);
                    getConfig().set("worlds", list);
                    saveConfig();
                }
                say(s, ChatColor.GREEN + "Світ створено! " + ChatColor.GRAY + "Телепорт: " + ChatColor.WHITE + "/iw tp " + name);
                return true;
            }
            case "tp": {
                if (!(s instanceof Player)) {
                    say(s, "Тільки для гравців.");
                    return true;
                }
                if (a.length < 2) {
                    say(s, "Використання: /iw tp <назва>");
                    return true;
                }
                World w = Bukkit.getWorld(a[1]);
                if (w == null) {
                    say(s, ChatColor.RED + "Такого світу немає. Список: /iw list");
                    return true;
                }
                Location spawn = w.getSpawnLocation();
                int y = w.getHighestBlockYAt(spawn) + 1;
                ((Player) s).teleport(new Location(w, spawn.getX(), y, spawn.getZ()));
                say(s, ChatColor.GREEN + "Телепортовано у світ " + ChatColor.WHITE + w.getName());
                return true;
            }
            case "list": {
                StringBuilder sb = new StringBuilder();
                for (World w : Bukkit.getWorlds()) {
                    boolean ours = w.getGenerator() instanceof InterGenerator;
                    sb.append(ours ? ChatColor.LIGHT_PURPLE : ChatColor.GRAY).append(w.getName()).append(ChatColor.DARK_GRAY).append(", ");
                }
                say(s, "Світи: " + sb + ChatColor.GRAY + "(фіолетові - InterWorld)");
                return true;
            }
            case "here": {
                if (!(s instanceof Player)) {
                    say(s, "Тільки для гравців.");
                    return true;
                }
                Player p = (Player) s;
                Location l = p.getLocation();
                say(s, "Біом: " + ChatColor.WHITE + l.getBlock().getBiome()
                        + ChatColor.GRAY + ", висота ландшафту: " + ChatColor.WHITE
                        + Terrain.of(p.getWorld().getSeed()).height(l.getBlockX(), l.getBlockZ()));
                return true;
            }
            default:
                say(s, "Команди:");
                s.sendMessage(ChatColor.LIGHT_PURPLE + " /iw create <назва> [seed]" + ChatColor.GRAY + " - створити світ");
                s.sendMessage(ChatColor.LIGHT_PURPLE + " /iw tp <назва>" + ChatColor.GRAY + " - телепорт");
                s.sendMessage(ChatColor.LIGHT_PURPLE + " /iw list" + ChatColor.GRAY + " - список світів");
                s.sendMessage(ChatColor.LIGHT_PURPLE + " /iw here" + ChatColor.GRAY + " - біом і висота тут");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(Arrays.asList("create", "tp", "list", "here"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("tp")) for (World w : Bukkit.getWorlds()) out.add(w.getName());
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        List<String> res = new ArrayList<>();
        for (String x : out) if (x.toLowerCase().startsWith(last)) res.add(x);
        return res;
    }
}
