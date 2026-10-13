package main;

import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import mindustry.game.Team;

public class Cache {

    /** uuid -> team the player was in when they left (only teams that were still alive). */
    public static ObjectMap<String, Team> playerTeams = new ObjectMap<>();
    public static ObjectMap<Team, TeamInfo> teamsInfo = new ObjectMap<>();
    public static ObjectMap<String, String> teamRequests = new ObjectMap<>();
    public static ObjectSet<String> restartVotes = new ObjectSet<>();
}