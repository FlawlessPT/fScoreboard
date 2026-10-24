package pt.flawless.fscoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Sidebar num Scoreboard do Bukkit por jogador. As equipas do fTab (tab e nome por cima da
 * cabeça) são registadas por ele também no scoreboard de cada jogador, por isso continuam a
 * aparecer (com atraso até ao próximo ciclo dele). Cada linha é uma equipa com
 * prefixo/sufixo (até 32 caracteres) sobre uma entrada invisível única.
 */
final class Sidebar {
    private static final String OBJECTIVE = "fsb";

    private record State(Scoreboard board, String[] lines) {}

    private final String title;
    private final Map<UUID, State> shown = new HashMap<>();

    Sidebar(String title) {
        this.title = cut(title, 32);
    }

    /** Mostra ou atualiza as linhas do jogador. Thread principal. */
    void render(Player player, String[] lines) {
        State last = shown.get(player.getUniqueId());
        Scoreboard board;
        if (last == null || last.lines().length != lines.length) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                    LegacyComponentSerializer.legacySection().deserialize(title));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank()); // esconde os números nos clientes 1.20.3+
            for (int i = 0; i < lines.length; i++) {
                Team team = board.registerNewTeam("fsb_" + i);
                team.addEntry(entry(i));
                setText(team, lines[i]);
                objective.getScore(entry(i)).setScore(lines.length - i);
            }
            player.setScoreboard(board);
        } else {
            board = last.board();
            for (int i = 0; i < lines.length; i++)
                if (!lines[i].equals(last.lines()[i])) setText(board.getTeam("fsb_" + i), lines[i]);
        }
        shown.put(player.getUniqueId(), new State(board, lines));
    }

    /** Remove a sidebar do jogador. */
    void hide(Player player) {
        if (shown.remove(player.getUniqueId()) == null) return;
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    void forget(Player player) {
        shown.remove(player.getUniqueId());
    }

    @SuppressWarnings("deprecation") // setPrefix/setSuffix(String): o texto já vem em § legado
    private static void setText(Team team, String text) {
        String line = cut(text, 32);
        int split = Math.min(16, line.length());
        if (split > 0 && line.charAt(split - 1) == ChatColor.COLOR_CHAR) split--; // não parte um código de cor
        String prefix = line.substring(0, split);
        team.setPrefix(prefix);
        team.setSuffix(split < line.length() ? ChatColor.getLastColors(prefix) + line.substring(split) : "");
    }

    private static String entry(int index) {
        return ChatColor.values()[index].toString() + ChatColor.RESET;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
