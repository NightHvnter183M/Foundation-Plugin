package main;

import arc.graphics.Color;
import arc.util.Strings;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public final class Ui {

    public static final float WIDTH = 330f;
    public static final float PANE_WIDTH = WIDTH + 30f;
    private static final float SOFTEN = 0.5f;

    public static String colored(Team team, String text) {
        Color soft = team.color.cpy().lerp(Color.white, SOFTEN);
        soft.a = 1f;
        return "[#" + soft + "]" + text + "[]";
    }

    public static String teamTitle(Team team) {
        Player leader = onlineLeader(team);
        TeamInfo info = Cache.teamsInfo.get(team);
        String name = leader != null ? Strings.stripColors(leader.name)
                : info != null && info.getLeaderName() != null ? info.getLeaderName() : "#" + team.id;
        return colored(team, name);
    }

    public static String duration(int seconds) {
        seconds = Math.max(seconds, 0);
        return seconds >= 3600
                ? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public static Player onlineLeader(Team team) {
        TeamInfo info = Cache.teamsInfo.get(team);
        return info == null ? null : Groups.player.find(info::isLeader);
    }

    public static String time(int seconds) {
        seconds = Math.max(seconds, 0);
        return String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}