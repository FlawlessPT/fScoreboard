package pt.flawless.fscoreboard;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import pt.flawless.fapi.economy.Economies;
import pt.flawless.fapi.economy.EconomyApi;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.events.PlayerAuthEvent.PlayerAuthEvent;
import pt.flawless.fapi.logs.FConsoleLogger;
import pt.flawless.fapi.ranks.RankApi;
import pt.flawless.fapi.ranks.Ranks;
import pt.flawless.fapi.reload.Reloads;
import org.bukkit.scheduler.BukkitTask;

public class FScoreboard extends JavaPlugin implements Listener {
    private static final int MAX_LINES = 15;

    private final FConsoleLogger consoleLogger = new FConsoleLogger(this.getName());
    private EconomyApi economy;
    /** Opcional (fRankup); sem ele {rank} fica vazio. */
    private RankApi ranks;
    private Sidebar sidebar;
    private List<String> lines;
    private BukkitTask task;

    @Override
    public void onEnable() {
        try {
            economy = Economies.get();
        } catch (RuntimeException e) {
            consoleLogger.sendMessage("economia indisponível: " + e.getMessage(), EMessageTypeEnum.ERROR);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("fRankup") != null) ranks = Ranks.get();
        saveDefaultConfig();
        applyConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Reloads.register(this, () -> {
            reloadConfig();
            for (Player p : Bukkit.getOnlinePlayers()) sidebar.hide(p);
            applyConfig();
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
        });
        for (Player p : Bukkit.getOnlinePlayers()) update(p); // /reload
        consoleLogger.sendEnablePluginMessage();
    }

    /** Lê linhas, título e período do config; recria a sidebar e a tarefa periódica. */
    private void applyConfig() {
        lines = getConfig().getStringList("lines");
        if (lines.size() > MAX_LINES) lines = lines.subList(0, MAX_LINES);
        sidebar = new Sidebar(color(getConfig().getString("title", "")));
        if (task != null) task.cancel();
        long period = Math.max(1, getConfig().getInt("update-seconds", 5)) * 20L;
        task = getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
        }, period, period);
    }

    @Override
    public void onDisable() {
        if (sidebar == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) sidebar.hide(p);
        consoleLogger.sendDisablePluginMessage();
    }

    /** A conta de economia só existe depois do login: a sidebar aparece nessa altura. */
    @EventHandler
    public void onAuth(PlayerAuthEvent e) {
        update(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sidebar.forget(e.getPlayer());
    }

    /** Pede o saldo (assíncrono) e volta à thread principal para enviar os pacotes. */
    private void update(Player player) {
        UUID uuid = player.getUniqueId();
        CompletableFuture<String> rankLabel = ranks == null ? CompletableFuture.completedFuture("")
                : ranks.getRankLabel(uuid).exceptionally(e -> "");
        economy.getBalance(uuid).thenCombine(rankLabel, (cents, rank) -> new Object[]{cents, rank}).whenComplete((both, error) -> {
            String saldo = economy.format(error == null ? (Long) both[0] : 0L);
            String rank = error == null ? (String) both[1] : "";
            try {
                Bukkit.getScheduler().runTask(this, () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) return;
                    sidebar.render(p, lines.stream()
                            .map(l -> color(l.replace("{saldo}", saldo)).replace("{rank}", rank)).toArray(String[]::new));
                });
            } catch (RuntimeException ex) {
                // plugin desligado entretanto
            }
        });
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
