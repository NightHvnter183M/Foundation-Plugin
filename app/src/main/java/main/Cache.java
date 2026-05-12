package main;

import arc.struct.ObjectIntMap;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import mindustry.game.Team;

public class Cache {

    public static int teamMenuId, joinMenuId, acceptMenuId, denyMenuId, kickMenuId, WelcomeMenuId, mapVoteMenuId;
    public static ObjectMap<String, Team> playerTeams = new ObjectMap<>();
    public static ObjectMap<Team, TeamInfo> teams_Info = new ObjectMap<>();
    public static ObjectMap<String, String> teamRequests = new ObjectMap<>();
    public static ObjectSet<String> restartVotes = new ObjectSet<>();
    public static ObjectIntMap<Integer> votes = new ObjectIntMap<>();
}
