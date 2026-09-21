package main;

import arc.struct.Seq;
import mindustry.gen.Player;

public class TeamInfo {

    private String leaderUuid;
    private final Seq<Player> teamMembers;
    private final Seq<String> teamMembersUuids = new Seq<>();
    private final long creationTime;

    public TeamInfo(String leadUuid, Seq<Player> teamMembers) {
        this.leaderUuid = leadUuid;
        this.teamMembers = teamMembers;
        this.teamMembers.forEach(p -> teamMembersUuids.add(p.uuid()));
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * Removes players from the team that are not in the cache.<br>
     * Made to remove offline players since {@code Player} object becomes
     * invalid when player leaves the game.
     */
    public void orphanPlayerCleanup() {
        for (Player member : teamMembers) {
            if (!(Cache.playerTeams.containsKey(member.uuid()) || member.isAdded())) {
                teamMembers.remove(member);
            }
        }
    }
    public void clear() {
        teamMembers.clear();
        teamMembersUuids.clear();
        leaderUuid = null;
    }

    public String getLeaderUuid() {
        return leaderUuid;
    }
    public Player getPlayerByUuid(String uuid) {
        return teamMembers.find(p -> p.uuid().equals(uuid));
    }
    public long getCreationTime() {
        return creationTime;
    }

    public void addPlayer(Player player) {
        if (teamMembersUuids.contains(player.uuid())) {
            return;
        }
        teamMembers.add(player);
        teamMembersUuids.add(player.uuid());
    }
    public void removePlayer(Player player) {
        if (!teamMembersUuids.contains(player.uuid())) {
            return;
        }
        teamMembers.remove(player);
        teamMembersUuids.remove(player.uuid());
    }
    public void removePlayerByUuid(String uuid) {
        if (!teamMembersUuids.contains(uuid)) {
            return;
        }
        byte done = 0;
        for (Player p : teamMembers) {
            if (p.uuid().equals(uuid)) {
                teamMembers.remove(p);
                done = 1;
                break;
            }
        }
        if (done == 0) orphanPlayerCleanup();
        teamMembersUuids.remove(uuid);
    }


    public void setLeaderUuid(String uuid) {
        leaderUuid = uuid;
    }

}
