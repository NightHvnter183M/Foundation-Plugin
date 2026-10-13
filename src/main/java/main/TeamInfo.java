package main;

import arc.util.Strings;
import mindustry.game.Team;
import mindustry.gen.Player;

import static mindustry.Vars.state;
///Now TeamInfo contains team life duration
public class TeamInfo {

    public static final int FIRST_PLAYER_TEAM_ID = 7;
    private String leaderUuid;
    private String leaderName;
    private final double createdTick = state.tick;
    private float rating;

    public TeamInfo(Player leader) {
        leaderUuid = leader.uuid();
        leaderName = Strings.stripColors(leader.name);
    }

    public static boolean isPlayerTeam(Team team) {
        return team != null && team.id >= FIRST_PLAYER_TEAM_ID;
    }

    public String getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(String uuid) {
        leaderUuid = uuid;
    }
    public void setLeader(Player leader) {
        leaderUuid = leader.uuid();
        leaderName = Strings.stripColors(leader.name);
    }

    public String getLeaderName() {
        return leaderName;
    }

    public boolean isLeader(Player player) {
        return player != null && player.uuid().equals(leaderUuid);
    }

    public int lifetimeSeconds() {
        return (int) Math.max(0, (state.tick - createdTick) / 60.0);
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }
}