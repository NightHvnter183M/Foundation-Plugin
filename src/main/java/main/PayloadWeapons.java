package main;

import arc.Events;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.CreateBulletCallPacket;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Payloadc;
import mindustry.gen.Sounds;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.type.Weapon;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.payloads.UnitPayload;

import static mindustry.Vars.state;

///A new class for payload unit update - imitates real units
public class PayloadWeapons {

    private static final float RETARGET_TIME = 15f;
    private static final Seq<Unit> carriers = new Seq<>();

    public static void init() {
        Events.run(EventType.Trigger.update, PayloadWeapons::update);
    }

    private static void update() {
        if (!state.isGame() || state.isPaused() || !state.rules.unitPayloadUpdate) return;

        Groups.unit.each(u -> u instanceof Payloadc p && p.hasPayload(), carriers::add);
        for (int i = 0; i < carriers.size; i++) {
            Unit carrier = carriers.get(i);
            Seq<Payload> payloads = ((Payloadc) carrier).payloads();
            for (int j = 0; j < payloads.size; j++) {
                if (payloads.get(j) instanceof UnitPayload payload) {
                    updateCarried(carrier, payload.unit);
                }
            }
        }
        carriers.clear();
    }

    private static void updateCarried(Unit carrier, Unit unit) {
        if (unit == null || unit.type == null || !unit.hasWeapons()) return;

        unit.set(carrier.x, carrier.y);
        unit.rotation = carrier.rotation;
        unit.vel.setZero();

        for (WeaponMount mount : unit.mounts) {
            Weapon weapon = mount.weapon;
            if (!canFire(weapon)) continue;

            mount.reload = Math.max(mount.reload - Time.delta * unit.reloadMultiplier, 0f);

            float mountX = unit.x + Angles.trnsx(unit.rotation - 90f, weapon.x, weapon.y);
            float mountY = unit.y + Angles.trnsy(unit.rotation - 90f, weapon.x, weapon.y);
            float range = weapon.range();
            boolean air = unit.type.targetAir && weapon.bullet.collidesAir;
            boolean ground = unit.type.targetGround && weapon.bullet.collidesGround;

            if (mount.target != null && (Units.invalidateTarget(mount.target, unit.team, mountX, mountY, range)
                    || (mount.target instanceof Unit u && !u.checkTarget(air, ground)))) {
                mount.target = null;
            }
            if (mount.target == null) {
                // searched on a timer: with nothing in range this would scan every tick
                mount.retarget -= Time.delta;
                if (mount.retarget > 0f) continue;
                mount.retarget = RETARGET_TIME;
                mount.target = findTarget(unit, mountX, mountY, range, air, ground);
                if (mount.target == null) continue;
            }

            mount.aimX = mount.target.x();
            mount.aimY = mount.target.y();
            if (mount.reload > 0f) continue;

            mount.reload = weapon.reload;
            shoot(carrier, unit, mount);
        }
    }

    private static boolean canFire(Weapon weapon) {
        BulletType bullet = weapon.bullet;
        return bullet != null && weapon.controllable && weapon.aiControllable && !weapon.noAttack
                && !weapon.autoTarget && !weapon.shootOnDeath && !bullet.killShooter;
    }

    private static Teamc findTarget(Unit unit, float x, float y, float range, boolean air, boolean ground) {
        if (!air && !ground) return null;
        return Units.closestTarget(unit.team, x, y, range, u -> u.checkTarget(air, ground), b -> ground);
    }

    private static void shoot(Unit carrier, Unit unit, WeaponMount mount) {
        float[] lastDelay = {-1f};
        mount.weapon.shoot.shoot(mount.barrelCounter, (xOffset, yOffset, angleOffset, delay, mover) -> {
            mount.totalShots++;
            boolean effects = delay != lastDelay[0];
            lastDelay[0] = delay;
            if (delay > 0f) {
                Time.run(delay, () -> {
                    if (isCarrying(carrier, unit)) bullet(unit, mount, xOffset, yOffset, angleOffset, effects);
                });
            } else {
                bullet(unit, mount, xOffset, yOffset, angleOffset, effects);
            }
        }, () -> mount.barrelCounter++);
    }

    private static boolean isCarrying(Unit carrier, Unit unit) {
        return carrier.isValid() && ((Payloadc) carrier).payloads().contains(p -> p instanceof UnitPayload up && up.unit == unit);
    }

    private static void bullet(Unit unit, WeaponMount mount, float xOffset, float yOffset, float angleOffset, boolean effects) {
        Weapon weapon = mount.weapon;
        BulletType type = weapon.bullet;

        if (mount.target instanceof Healthc target && target.isValid()) {
            mount.aimX = mount.target.x();
            mount.aimY = mount.target.y();
        }
        unit.aimX = mount.aimX;
        unit.aimY = mount.aimY;

        float mountX = unit.x + Angles.trnsx(unit.rotation - 90f, weapon.x, weapon.y);
        float mountY = unit.y + Angles.trnsy(unit.rotation - 90f, weapon.x, weapon.y);
        float weaponRotation = Angles.angle(mountX, mountY, mount.aimX, mount.aimY) - 90f;
        float xSpread = Mathf.range(weapon.xRand);
        float bulletX = mountX + Angles.trnsx(weaponRotation, weapon.shootX + xOffset + xSpread, weapon.shootY + yOffset);
        float bulletY = mountY + Angles.trnsy(weaponRotation, weapon.shootX + xOffset + xSpread, weapon.shootY + yOffset);
        float angle = Angles.angle(bulletX, bulletY, mount.aimX, mount.aimY) + angleOffset
                + Mathf.range(weapon.inaccuracy + type.inaccuracy);
        float velocityScl = 1f - weapon.velocityRnd + Mathf.random(weapon.velocityRnd);
        float lifetimeScl = type.scaleLife ? Mathf.clamp(Mathf.dst(bulletX, bulletY, mount.aimX, mount.aimY) / type.range) : 1f;

        Bullet bullet = type.create(unit, unit.team, bulletX, bulletY, angle, -1f, velocityScl, lifetimeScl, null);

        if (!Vars.net.server()) return;

        if (bullet != null) {
            CreateBulletCallPacket packet = new CreateBulletCallPacket();
            packet.type = type;
            packet.team = unit.team;
            packet.x = bulletX;
            packet.y = bulletY;
            packet.angle = angle;
            packet.damage = bullet.damage;
            packet.velocityScl = velocityScl;
            packet.lifetimeScl = lifetimeScl;
            Vars.net.send(packet, false);
        }
        if (!effects) return;
        if (type.shootEffect != Fx.none) Call.effect(type.shootEffect, bulletX, bulletY, angle, type.hitColor);
        if (type.smokeEffect != Fx.none) Call.effect(type.smokeEffect, bulletX, bulletY, angle, type.hitColor);
        if (weapon.shootSound != Sounds.none) {
            Call.soundAt(weapon.shootSound, bulletX, bulletY, 1f, Mathf.random(weapon.soundPitchMin, weapon.soundPitchMax));
        }
    }
}
