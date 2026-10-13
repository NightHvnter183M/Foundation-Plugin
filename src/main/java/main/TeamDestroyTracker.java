package main;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Iconc;
import mindustry.world.blocks.storage.CoreBlock;

import static main.Localisation.local;
import static mindustry.Vars.state;

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
        announceDestroyed(victimTeam, destroyer);
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
        MenuManager.refreshTeamPanels();
    }

    public static void surrenderTeam(Team team) {

        if (team == null || team == Team.derelict || destroyedTeams.contains(team)) {
            return;
        }

        destroyedTeams.add(team);
        announceSurrender(team);
        kill_team(team);
        Time.run(60f, () -> destroyedTeams.remove(team));
    }

    private static void announceDestroyed(Team victim, Team destroyer) {
        if (!TeamInfo.isPlayerTeam(victim)) return;

        String victimName = Ui.teamTitle(victim), victimLife = Ui.duration(lifetime(victim));
        boolean byTeam = TeamInfo.isPlayerTeam(destroyer);
        String destroyerName = byTeam ? Ui.teamTitle(destroyer) : null;
        String destroyerLife = byTeam ? Ui.duration(lifetime(destroyer)) : null;
        boolean byWaves = destroyer != null && destroyer == state.rules.waveTeam;

        Groups.player.each(p -> {
            String text = byTeam ? local(p, "uiTeamDestroyedBy", destroyerName, victimName, victimLife, destroyerLife)
                    : byWaves ? local(p, "uiTeamDestroyedByWaves", victimName, victimLife)
                    : local(p, "uiTeamDestroyed", victimName, victimLife);
            p.sendMessage("[#8B008B]" + Iconc.modePvp + "[] " + text);
        });
    }

    private static void announceSurrender(Team team) {
        if (!TeamInfo.isPlayerTeam(team)) return;
        String name = Ui.teamTitle(team), life = Ui.duration(lifetime(team));
        Groups.player.each(p -> p.sendMessage("[lightgray]" + Iconc.modePvp + "[] " + local(p, "uiTeamSurrendered", name, life)));
    }

    private static int lifetime(Team team) {
        TeamInfo info = Cache.teamsInfo.get(team);
        return info == null ? 0 : info.lifetimeSeconds();
    }
}