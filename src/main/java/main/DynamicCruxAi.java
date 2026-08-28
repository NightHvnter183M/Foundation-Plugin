package main;

import arc.math.geom.Vec2;
import arc.util.Interval;
import mindustry.Vars;
import mindustry.ai.Pathfinder;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.world.Tile;

public class DynamicCruxAi {

    public static class dynamicCruxAI extends AIController {

        private Teamc currentTarget;
        private Tile spawnPoint;
        private final Vec2 laneDestination = new Vec2();
        private final Vec2 strategicCenter = new Vec2();

        private boolean laneInitialized = false;
        private final Interval timer = new Interval(2);
        private static final float targetUpdateTime = 30f;
        private static final float stratUpdateTime = 120f;
        private static final float aggRad = 500f;
        private static final float tSwitchMult = 1.30f;
        private static final float mLaneAngle = 65f;
        private static final float lBonus = 500f;
        private static final float wLane = 350f;
        private static final float ncBonus = 800f;
        private static final float fBonus = 700f;
        private static final float tBonus = 500f;
        private static final float uBonus = 100f;
        @Override
        public void init() {
            super.init();
            spawnPoint = getClosestSpawner();
            updateStrategicCenter();
            if (spawnPoint != null) {
                updateLaneDestination();
                laneInitialized = true;
            }
        }
        @Override
        public void updateMovement() {

            if (unit == null || unit.dead) {
                return;
            }
            if (!laneInitialized) {
                spawnPoint = getClosestSpawner();
                if (spawnPoint != null) {
                    updateStrategicCenter();
                    updateLaneDestination();
                    laneInitialized = true;
                }
            }
            if (timer.get(1, stratUpdateTime)) {
                updateStrategicCenter();
                if (spawnPoint != null) {
                    updateLaneDestination();
                }
            }
            if (!isValidTarget(currentTarget)) {
                currentTarget = findBestTarget();
            } else if (timer.get(0, targetUpdateTime)) {
                Teamc newTarget = findBestTarget();
                if (newTarget != null && shouldSwitchTarget(newTarget)) {
                    currentTarget = newTarget;
                }
            }

            target = currentTarget;
            if (currentTarget != null) {
                float attackRange = getAttackRange();
                if (unit.isFlying()) {
                    updateFlyingMovement(currentTarget, attackRange);
                } else {
                    updateGroundMovement(currentTarget, attackRange);
                }
            } else {
                advanceAlongLane();
            }
            updateWeapons();
        }
        private void updateStrategicCenter() {
            float totalX = 0f;
            float totalY = 0f;
            int count = 0;
            for (var data : Vars.state.teams.getActive()) {
                if (!isEnemyTeam(data.team)) {
                    continue;
                }
                for (var core : data.cores) {
                    if (core == null || core.dead()) {
                        continue;
                    }
                    if (core.block == Blocks.coreShard) {
                        continue;
                    }
                    totalX += core.x;
                    totalY += core.y;
                    count++;
                }
            }
            if (count > 0) {

                strategicCenter.set(totalX / count, totalY / count);
                return;
            }
            strategicCenter.set(Vars.world.width() * Vars.tilesize / 2f, Vars.world.height() * Vars.tilesize / 2f);
        }
        private void updateLaneDestination() {

            if (spawnPoint == null) {
                return;
            }
            float spawnX = spawnPoint.worldx();
            float spawnY = spawnPoint.worldy();
            float dx = strategicCenter.x - spawnX;
            float dy = strategicCenter.y - spawnY;

            float length = (float)Math.sqrt(dx * dx + dy * dy);

            if (length < 1f) {
                laneDestination.set(strategicCenter);
                return;
            }
            dx /= length;
            dy /= length;
            float advanceDistance = length * 0.55f;
            laneDestination.set(spawnX + dx * advanceDistance, spawnY + dy * advanceDistance);
        }

        private void advanceAlongLane() {

            if (!laneInitialized) {
                if (!unit.isFlying()) {
                    pathfind(Pathfinder.fieldCore);
                }
                return;
            }
            faceMovement();
            moveTo(laneDestination, 20f);
        }

        private void updateGroundMovement(
                Teamc target,
                float attackRange
        ) {

            if (!isValidTarget(target)) {
                return;
            }
            faceTarget();
            moveTo(target, attackRange);
        }

        private void updateFlyingMovement(Teamc target, float attackRange) {
            if (!isValidTarget(target)) {
                return;
            }

            if (unit.type == UnitTypes.flare || unit.type == UnitTypes.horizon || unit.type == UnitTypes.quad) {
                moveTo(target, 0f);
                faceTarget();
                return;
            }

            if (unit.type == UnitTypes.zenith) {
                moveTo(target, attackRange * 0.7f);
                faceTarget();
                return;
            }

            if (unit.type.circleTarget) {
                float radiusOffset = (unit.id % 5) * 12f;
                float orbitRadius = Math.max(attackRange * 0.75f, 35f) + radiusOffset;
                circle(target, orbitRadius);
                faceTarget();
            } else {
                moveTo(target, Math.max(attackRange * 0.85f, 25f));
                faceTarget();
            }
        }

        private float getAttackRange() {

            return Math.max(
                    unit.type.range * 0.82f,
                    20f
            );
        }
        private boolean isEnemyTeam(Team team) {

            if (team == null) {
                return false;
            }
            return team.id > 5 && team != unit.team;
        }
        private boolean isValidTarget(Teamc target) {

            if (target == null) {
                return false;
            }
            if (target == unit) {
                return false;
            }
            if (!isEnemyTeam(target.team())) {
                return false;
            }
            return !Units.invalidateTarget(target, unit.team, unit.x, unit.y);
        }
        private Teamc findBestTarget() {

            Teamc bestTarget = null;
            float bestScore = Float.NEGATIVE_INFINITY;
            Building building = Units.findEnemyTile(unit.team, unit.x, unit.y, aggRad, tile -> isEnemyTeam(tile.team()));
            if (building != null) {
                float score = scoreTarget(building);
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = building;
                }
            }
            Unit enemyUnit = Units.closest(null, unit.x, unit.y, aggRad, u -> isValidTarget(u) && !u.dead);

            if (enemyUnit != null) {
                float score = scoreTarget(enemyUnit);
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = enemyUnit;
                }
            }
            for (var data : Vars.state.teams.getActive()) {
                if (!isEnemyTeam(data.team)) {
                    continue;
                }
                for (var core : data.cores) {
                    if (core == null || core.dead()) {
                        continue;
                    }
                    if (core.block == Blocks.coreShard) {
                        continue;
                    }
                    float score = scoreTarget(core);
                    if (score > bestScore) {
                        bestScore = score;
                        bestTarget = core;
                    }
                }
            }
            if (bestTarget == null) {

                Building anyBuilding = Units.findEnemyTile(unit.team, unit.x, unit.y, Float.MAX_VALUE, tile -> isEnemyTeam(tile.team()));
                if (anyBuilding != null) {
                    float score = scoreTarget(anyBuilding);
                    if (score > bestScore) bestScore = score;bestTarget = anyBuilding;
                }
            }
            if (bestTarget == null) {
                Unit anyUnit = Units.closest(null, unit.x, unit.y, Float.MAX_VALUE, u -> isValidTarget(u) && !u.dead);

                if (anyUnit != null) {
                    bestTarget = anyUnit;
                }
            }
            return bestTarget;
        }

        private float scoreTarget(Teamc target) {

            if (target == null) {
                return Float.NEGATIVE_INFINITY;
            }
            float distance = unit.dst(target);
            float score = 1000f - distance;
            float laneAlignment = getLaneAlignment(target);
            score += laneAlignment * lBonus;
            if (laneAlignment < 0.25f) {
                score -= wLane;
            }

            if (target instanceof Building building) {

                if (building.block == Blocks.coreNucleus) score += ncBonus;

                else if (building.block == Blocks.coreFoundation) score += fBonus;



                else if (building.block == Blocks.coreShard) score += 25f;


                else if (
                        building.block instanceof mindustry.world.blocks.defense.turrets.Turret
                ) {

                    score += tBonus;
                }

                else {

                    score += 75f;
                }
            }
            else if (target instanceof Unit enemy) {

                score += uBonus;

                score += enemy.hitSize * 3f;

                if (enemy.health > 0f) {

                    float healthRatio = enemy.health / Math.max(enemy.maxHealth, 1f);

                    if (healthRatio < 0.35f) {
                        score += 150f;
                    }
                }

                if (enemy.isFlying()) {
                    score += 50f;
                }
            }

            return score;
        }

        private float getLaneAlignment(Teamc target) {

            if (spawnPoint == null || target == null) {
                return 0.5f;
            }

            float spawnX = spawnPoint.worldx();
            float spawnY = spawnPoint.worldy();

            float laneX = spawnX - strategicCenter.x;
            float laneY = spawnY - strategicCenter.y;

            float targetX = target.x() - strategicCenter.x;
            float targetY = target.y() - strategicCenter.y;

            float laneLength = (float)Math.sqrt(laneX * laneX + laneY * laneY);

            float targetLength = (float)Math.sqrt(targetX * targetX + targetY * targetY);

            if (laneLength < 1f || targetLength < 1f) {
                return 1f;
            }

            laneX /= laneLength;
            laneY /= laneLength;

            targetX /= targetLength;
            targetY /= targetLength;

            float dot = laneX * targetX + laneY * targetY;

            dot = Math.max(-1f, Math.min(1f, dot));

            float angle = (float)Math.toDegrees(Math.acos(dot));

            float alignment = 1f - Math.min(angle / mLaneAngle, 1f);

            return alignment;
        }

        private boolean shouldSwitchTarget(Teamc newTarget) {

            if (!isValidTarget(newTarget)) {
                return false;
            }

            if (!isValidTarget(currentTarget)) {
                return true;
            }

            float currentScore = scoreTarget(currentTarget);

            float newScore = scoreTarget(newTarget);

            return newScore > currentScore * tSwitchMult;
        }
    }
}