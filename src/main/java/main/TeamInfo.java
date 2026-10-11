package main;

import mindustry.game.Team;
import mindustry.gen.Player;

public class TeamInfo {

    public static final int FIRST_PLAYER_TEAM_ID = 7;
    private String leaderUuid;
    private final long creationTime = System.currentTimeMillis();
    private float rating;

    public TeamInfo(String leaderUuid) {
        this.leaderUuid = leaderUuid;
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

    public boolean isLeader(Player player) {
        return player != null && player.uuid().equals(leaderUuid);
    }

    public long getCreationTime() {
        return creationTime;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }
}