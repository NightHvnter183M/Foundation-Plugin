package main;

import arc.util.Timer;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public class restart {

    private static int voterestarttime = 300;
    private static boolean isrestartvoting = false;
    private static Timer.Task voteTask;

    public static void startrestartvote(Player player){
        if(isrestartvoting) return;
        isrestartvoting = true;
        Call.sendMessage(player.name + " started a voting of restart. Voting will last five minutes");
        Cache.restartVotes.clear();
        Cache.restartVotes.add(player.uuid());
        voteTask = Timer.schedule(() -> {
            if (isrestartvoting) {
                isrestartvoting = false;
                Call.sendMessage("[red]Not enough votes for restart. Voting is over");
                Cache.restartVotes.clear();
            }
        }, voterestarttime);
    }

    public static void addvotes(Player player) {
        if (!isrestartvoting){
            startrestartvote(player);
            return;
        }
        if(Cache.restartVotes.contains(player.uuid())){
            player.sendMessage("[#F08080]You have already voted for restart!");
            return;
        }
        Cache.restartVotes.add(player.uuid());
        int votesNeeded = (int) (Groups.player.size() * 0.6) + 1;
        int currentVotes = Cache.restartVotes.size;
        Call.sendMessage("[accent]" + player.name + "[white] Voted for restart. ([green]" + currentVotes + "[white]/[orange]" + votesNeeded + "[white])");
        if(currentVotes >= votesNeeded) doingrestart();

    }

    public static void doingrestart(){
        isrestartvoting = false;
        if (voteTask != null) voteTask.cancel();
        Cache.playerTeams.clear();
        MapVote.start();
        Groups.player.each(p -> p.team(Team.all[0]));
    }


}
