package main;

import java.util.HashMap;
import arc.struct.ObjectSet;
import mindustry.game.Team;
import mindustry.net.Administration;

public class Cache {
    
    public static HashMap<String, Administration.PlayerInfo> players_Info = new HashMap<>();
    public static HashMap<String, Team> playerTeams = new HashMap<>();
    public static HashMap<Team, TeamInfo> teams_Info = new HashMap<>();
    public static HashMap<String, String> teamRequests = new HashMap<>();
    public static ObjectSet<String> restartVotes = new ObjectSet<>();
}
