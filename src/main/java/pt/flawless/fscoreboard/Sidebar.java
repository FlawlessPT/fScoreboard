package pt.flawless.fscoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.v1_8_R1.IScoreboardCriteria;
import net.minecraft.server.v1_8_R1.Packet;
import net.minecraft.server.v1_8_R1.PacketPlayOutScoreboardDisplayObjective;
import net.minecraft.server.v1_8_R1.PacketPlayOutScoreboardObjective;
import net.minecraft.server.v1_8_R1.PacketPlayOutScoreboardScore;
import net.minecraft.server.v1_8_R1.PacketPlayOutScoreboardTeam;
import net.minecraft.server.v1_8_R1.Scoreboard;
import net.minecraft.server.v1_8_R1.ScoreboardObjective;
import net.minecraft.server.v1_8_R1.ScoreboardScore;
import net.minecraft.server.v1_8_R1.ScoreboardTeam;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_8_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * Sidebar enviada por pacotes: não toca no scoreboard do Bukkit do jogador, por isso as
 * equipas do fTab (tab e nome por cima da cabeça) continuam a funcionar. Cada linha é uma
 * equipa com prefixo/suffix (até 32 caracteres) sobre uma entrada invisível única.
 */
final class Sidebar {
    private static final String OBJECTIVE = "fsb";
    private static final Scoreboard NMS = new Scoreboard(); // só serve para construir os objetos NMS

    private final String title;
    private final Map<UUID, String[]> shown = new HashMap<>();

    Sidebar(String title) {
        this.title = cut(title, 32);
    }

    /** Mostra ou atualiza as linhas do jogador. Thread principal. */
    void render(Player player, String[] lines) {
        String[] last = shown.get(player.getUniqueId());
        ScoreboardObjective objective = new ScoreboardObjective(NMS, OBJECTIVE, IScoreboardCriteria.b);
        objective.setDisplayName(title);
        if (last == null || last.length != lines.length) {
            if (last != null) hide(player);
            send(player, new PacketPlayOutScoreboardObjective(objective, 0));
            send(player, new PacketPlayOutScoreboardDisplayObjective(1, objective));
            for (int i = 0; i < lines.length; i++) {
                send(player, new PacketPlayOutScoreboardTeam(team(i, lines[i]), 0));
                ScoreboardScore score = new ScoreboardScore(NMS, objective, entry(i));
                score.setScore(lines.length - i);
                send(player, new PacketPlayOutScoreboardScore(score));
            }
        } else {
            for (int i = 0; i < lines.length; i++)
                if (!lines[i].equals(last[i])) send(player, new PacketPlayOutScoreboardTeam(team(i, lines[i]), 2));
        }
        shown.put(player.getUniqueId(), lines);
    }

    /** Remove a sidebar do jogador. */
    void hide(Player player) {
        String[] last = shown.remove(player.getUniqueId());
        if (last == null) return;
        send(player, new PacketPlayOutScoreboardObjective(
                new ScoreboardObjective(NMS, OBJECTIVE, IScoreboardCriteria.b), 1));
        for (int i = 0; i < last.length; i++) send(player, new PacketPlayOutScoreboardTeam(team(i, ""), 1));
    }

    void forget(Player player) {
        shown.remove(player.getUniqueId());
    }

    private static ScoreboardTeam team(int index, String text) {
        ScoreboardTeam team = new ScoreboardTeam(NMS, "fsb_" + index);
        String line = cut(text, 32);
        int split = Math.min(16, line.length());
        if (split > 0 && line.charAt(split - 1) == ChatColor.COLOR_CHAR) split--; // não parte um código de cor
        String prefix = line.substring(0, split);
        team.setPrefix(prefix);
        team.setSuffix(split < line.length() ? ChatColor.getLastColors(prefix) + line.substring(split) : "");
        team.getPlayerNameSet().add(entry(index));
        return team;
    }

    private static String entry(int index) {
        return ChatColor.values()[index].toString() + ChatColor.RESET;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void send(Player player, Packet packet) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }
}
