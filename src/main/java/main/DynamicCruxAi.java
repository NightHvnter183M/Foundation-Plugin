package main;

import arc.util.Interval;
import mindustry.Vars;
import mindustry.ai.Pathfinder;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;

public class DynamicCruxAi {

    public static class dynamicCruxAI extends AIController {
        private Teamc currentTarget = null;
        private final Interval timer = new Interval(1);

        @Override
        public void updateMovement() {
            if (unit == null) return;
            if (timer.get(0, 30f) || !isValidTarget(currentTarget)) {
                currentTarget = findBestTarget();
            }
            this.target = currentTarget;
            if (currentTarget != null) {
                float range = unit.type.range * 0.85f;

                if (unit.isFlying()) {
                    if (!unit.type.circleTarget) {
                        float radiusOffset = (unit.id % 4) * 20f;
                        float attackRadius = unit.type.range * 0.7f + radiusOffset;
                        circle(currentTarget, attackRadius);
                        faceTarget();
                    } else {
                        moveTo(currentTarget, 0f);
                    }
                } else {
                    faceTarget();
                    if (unit.within(currentTarget, range)) {
                        moveTo(currentTarget, range);
                    } else {
                        pathfind(Pathfinder.fieldCore);
                    }
                }
            } else {
                if (!unit.isFlying()) {
                    pathfind(Pathfinder.fieldCore);
                }
            }
            updateWeapons();
        }

        private boolean isEnemyTeam(Team team) {
            return team != null && team.id > 5 && team != unit.team;
        }

        private boolean isValidTarget(Teamc target) {
            if (target == null) return false;
            if (!isEnemyTeam(target.team())) return false;

            return !Units.invalidateTarget(target, unit.team, unit.x, unit.y);
        }

        private Teamc findBestTarget() {
            float aggroRadius = 400f;
            Building nearbyBuilding = Units.findEnemyTile(
                    unit.team, unit.x, unit.y, aggroRadius,
                    tile -> isEnemyTeam(tile.team)
            );
            if (nearbyBuilding != null) return nearbyBuilding;

            Unit nearbyUnit = Units.closest(
                    null, unit.x, unit.y, aggroRadius,
                    u -> isEnemyTeam(u.team) && !u.dead
            );
            if (nearbyUnit != null) return nearbyUnit;

            Building closestCore = null;
            float minDstSq = Float.MAX_VALUE;

            for (var data : Vars.state.teams.getActive()) {
                if (isEnemyTeam(data.team) && data.hasCore()) {
                    Building core = data.core();
                    float dstSq = unit.dst2(core);
                    if (dstSq < minDstSq) {
                        minDstSq = dstSq;
                        closestCore = core;
                    }
                }
            }
            if (closestCore != null) return closestCore;

            Building anyBuilding = Units.findEnemyTile(
                    unit.team, unit.x, unit.y, Float.MAX_VALUE,
                    tile -> isEnemyTeam(tile.team)
            );
            if (anyBuilding != null) return anyBuilding;

            return Units.closest(
                    null, unit.x, unit.y, Float.MAX_VALUE,
                    u -> isEnemyTeam(u.team) && !u.dead
            );
        }
    }
}