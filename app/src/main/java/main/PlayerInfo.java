package main;

import mindustry.game.Team;
///PlayerInfo class to store information about each player
public class PlayerInfo {
    public Team team;
    public String uuid; 
    public Integer respawn_coldown = Integer.valueOf(0);
    public boolean leader = false;
}
