package main;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.world.blocks.storage.CoreBlock;

public class TeamDestroyTracker {

    private static final float KILL_CREDIT_TIME = 60f * 30f;
    private static final ObjectMap<Team, LastHit> lastHits = new ObjectMap<>();
    private static final ObjectSet<Team> destroyedTeams = new ObjectSet<>();

    private static final class LastHit {
        Team attacker;
        float time;
    }

    public static void init() {

        Events.on(EventType.ResetEvent.class, e -> {
            lastHits.clear();
            destroyedTeams.clear();
        });

        Events.on(EventType.BuildDamageEvent.class, event -> {
            if (!(event.build instanceof CoreBlock.CoreBuild) || event.source == null) return;

            Team attacker = event.source.team;
            Team victim = event.build.team;
            if (attacker == victim || attacker == Team.derelict) return;

            LastHit hit = lastHits.get(victim);
            if (hit == null) {
                hit = new LastHit();
                lastHits.put(victim, hit);
            }
            hit.attacker = attacker;
            hit.time = Time.time;
        });

        Events.on(EventType.CoreChangeEvent.class, event -> {
            if (event.core == null) return;

            Team victimTeam = event.core.team;
            if (victimTeam == Team.derelict) return;

            Time.runTask(1, () -> checkElimination(victimTeam));
        });
    }

    public static boolean hasMainCore(Team team) {
        return team.cores().contains(c -> c.block == Blocks.coreNucleus || c.block == Blocks.coreFoundation);
    }

    private static void checkElimination(Team victimTeam) {
        if (destroyedTeams.contains(victimTeam) || hasMainCore(victimTeam)) return;

        destroyedTeams.add(victimTeam);
        Team destroyer = findDestroyer(victimTeam);
        if (destroyer != null) {
            PointsManager.pointCounter(destroyer, victimTeam);
        }
        kill_team(victimTeam);
        Time.run(60f, () -> destroyedTeams.remove(victimTeam));
    }

    private static Team findDestroyer(Team victimTeam) {
        LastHit hit = lastHits.remove(victimTeam);
        if (hit == null || Time.time - hit.time > KILL_CREDIT_TIME) return null;
        return hit.attacker;
    }

    public static void kill_team(Team team) {

        team.data().destroyToDerelict();

        Groups.player.each(p -> p.team() == team, p -> {
            p.team(Team.derelict);
            if (p.unit() != null) {
                p.unit().kill();
            }
        });

        TeamInfo info = Cache.teamsInfo.get(team);
        if (info != null) {
            String leader = info.getLeaderUuid();
            if (leader != null) {
                for (var it = Cache.teamRequests.entries(); it.hasNext(); ) {
                    if (leader.equals(it.next().value)) it.remove();
                }
            }
            info.setLeaderUuid(null);
        }
        for (var it = Cache.playerTeams.entries(); it.hasNext(); ) {
            if (it.next().value == team) it.remove();
        }
    }

    public static void surrenderTeam(Team team) {

        if (team == null || team == Team.derelict || destroyedTeams.contains(team)) {
            return;
        }

        destroyedTeams.add(team);
        kill_team(team);
        Time.run(60f, () -> destroyedTeams.remove(team));
    }
}