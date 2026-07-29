package main;

import arc.Events;
import arc.struct.IntMap;
import arc.struct.ObjectSet;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.world.blocks.storage.CoreBlock;

public class TeamDestroyTracker {
    private static final IntMap<Team> teamDestroyer = new IntMap<Team>();
    private static final ObjectSet<Team> destroyedTeams = new ObjectSet<>();

    public static void init(){
        Events.on(EventType.ResetEvent.class, e -> teamDestroyer.clear());
        Events.on(EventType.BuildDamageEvent.class, event -> {
            if (event.build instanceof CoreBlock.CoreBuild && event.source != null) {
                if (event.source.team != event.build.team) {
                    teamDestroyer.put(event.build.pos(), event.source.team);
                }
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
            if(event.tile.block() instanceof CoreBlock && event.tile.team() != Team.all[0]){
                Team victimTeam = event.tile.team();
                if (destroyedTeams.contains(victimTeam)) {
                    return;
                }
                int pos = event.tile.pos();
                Team teamDestroy = teamDestroyer.get(pos, Team.derelict);
                teamDestroyer.remove(pos);
                Time.run(10f, () -> {
                    if (destroyedTeams.contains(victimTeam)) {
                        return;
                    }
                    boolean hasAtom = victimTeam.cores().contains(c -> c.block == Blocks.coreNucleus);
                    boolean hasHQ = victimTeam.cores().contains(c -> c.block == Blocks.coreFoundation);

                    if (!hasAtom && !hasHQ) {
                        destroyedTeams.add(victimTeam);
                        PointsManager manager = new PointsManager();
                        manager.pointCounter(teamDestroy, victimTeam);
                        kill_team(victimTeam);
                        Groups.player.each(p -> p.team() == victimTeam, p -> {
                            p.team(Team.all[0]);
                            if (Cache.teams_Info.containsKey(victimTeam)) {
                                Cache.teams_Info.get(victimTeam).leaderUuid = "";
                            }
                        });
                        Time.run(60f, () -> destroyedTeams.remove(victimTeam));
                    }
                });
            }
        });
    }

    public static void kill_team(Team team) {
        team.data().destroyToDerelict();
        if (team.data().players != null){
            Groups.player.each(p -> p.team() == team, p -> {
                p.team(Team.all[0]);
                var unit = p.unit();
                if (unit != null) {
                    unit.kill();
                }
            });
        }

    }
}
