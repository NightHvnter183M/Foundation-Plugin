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

    private static final IntMap<Team> teamDestroyer = new IntMap<>();
    private static final ObjectSet<Team> destroyedTeams = new ObjectSet<>();

    public static void init() {

        Events.on(EventType.ResetEvent.class, e -> {
            teamDestroyer.clear();
            destroyedTeams.clear();
        });

        Events.on(EventType.BuildDamageEvent.class, event -> {
            if (event.build instanceof CoreBlock.CoreBuild && event.source != null) {

                Team attacker = event.source.team;
                Team victim = event.build.team();

                if (attacker != victim && attacker != Team.derelict) {
                    teamDestroyer.put(event.build.pos(), attacker);
                }
            }
        });
        Events.on(EventType.CoreChangeEvent.class, event -> {

            if (event.core == null) {
                return;
            }

            Team victimTeam = event.core.team();

            if (victimTeam == Team.all[0] || victimTeam == Team.derelict) {
                return;
            }

            Time.runTask(1, () -> {

                if (destroyedTeams.contains(victimTeam)) {
                    return;
                }
                boolean hasNucleus = victimTeam.cores().contains(
                        c -> c.block == Blocks.coreNucleus
                );

                boolean hasFoundation = victimTeam.cores().contains(
                        c -> c.block == Blocks.coreFoundation
                );

                if (hasNucleus || hasFoundation) {
                    return;
                }
                destroyedTeams.add(victimTeam);
                Team destroyer = findDestroyer(victimTeam);
                if (destroyer != null && destroyer != Team.derelict) {
                    PointsManager manager = new PointsManager();
                    manager.pointCounter(destroyer, victimTeam);
                }

                kill_team(victimTeam);

                if (Cache.teams_Info.containsKey(victimTeam)) {
                    Cache.teams_Info.get(victimTeam).leaderUuid = "";
                }

                Time.run(60f, () -> destroyedTeams.remove(victimTeam));
            });
        });
    }

    private static Team findDestroyer(Team victimTeam) {
        Team result = Team.derelict;

        for (var entry : teamDestroyer) {
            Team attacker = entry.value;

            if (attacker != null &&
                    attacker != victimTeam &&
                    attacker != Team.derelict) {

                result = attacker;
            }
        }

        return result;
    }

    public static void kill_team(Team team) {

        team.data().destroyToDerelict();

        if (team.data().players != null) {

            Groups.player.each(p -> p.team() == team, p -> {

                p.team(Team.all[0]);

                var unit = p.unit();

                if (unit != null) {
                    unit.kill();
                }
            });
        }
    }

    public static void surrenderTeam(Team team) {

        if (team == null || team == Team.all[0]) {
            return;
        }

        destroyedTeams.add(team);

        kill_team(team);

        if (Cache.teams_Info.containsKey(team)) {
            Cache.teams_Info.get(team).leaderUuid = "";

            Time.run(60f, () -> destroyedTeams.remove(team));
        }
    }
}