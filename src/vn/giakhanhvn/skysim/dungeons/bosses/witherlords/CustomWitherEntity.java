package vn.giakhanhvn.skysim.dungeons.bosses.witherlords;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import lombok.Getter;
import lombok.var;
import net.minecraft.server.v1_8_R3.DamageSource;
import net.minecraft.server.v1_8_R3.EntityArmorStand;
import net.minecraft.server.v1_8_R3.EntityLargeFireball;
import net.minecraft.server.v1_8_R3.EntityWither;
import net.minecraft.server.v1_8_R3.EntityWitherSkull;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityHeadRotation;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
import net.minecraft.server.v1_8_R3.WorldServer;
import universal.SConsts;
import vn.giakhanhvn.skysim.SkySimEngine;
import vn.giakhanhvn.skysim.dungeons.systems.Dungeon;
import vn.giakhanhvn.skysim.dungeons.systems.DungeonUser;
import vn.giakhanhvn.skysim.dungeons.systems.classes.DungeonClass.EnumClass;
import vn.giakhanhvn.skysim.entity.EntityFunction;
import vn.giakhanhvn.skysim.entity.EntityStatistics;
import vn.giakhanhvn.skysim.entity.SEntity;
import vn.giakhanhvn.skysim.entity.SEntityType;
import vn.giakhanhvn.skysim.entity.nms.SNMSEntity;
import vn.giakhanhvn.skysim.user.User;
import vn.giakhanhvn.skysim.util.EntityManager;
import vn.giakhanhvn.skysim.util.PacketEntity;
import vn.giakhanhvn.skysim.util.STask;
import vn.giakhanhvn.skysim.util.Sputnik;
import vn.giakhanhvn.skysim.util.TimedActions;
import vn.giakhanhvn.skysim.util.STask.RunMode;
import vn.giakhanhvn.skysim.util.customentities.GiantItemDisplay;
import vn.giakhanhvn.skysim.util.SUtil;

public abstract class CustomWitherEntity extends EntityWither implements SNMSEntity, EntityFunction, EntityStatistics {
	protected Wither wither;
	int r;
	
	@Getter
	private boolean shield = false;
	public boolean frozen = false;
	public double hoverHeight = 1.5d;
	
	public int DIALOG_GAP = 50;
	
	public PacketEntity hologramEntity;
	public PacketEntity dialougeEntity;
	
	public Dungeon entityOwner;
	
	public CustomWitherEntity(WorldServer world, int r) {
		super(world, false);
		
		this.grief = false;
		this.skysim = true;
		
		this.wither = (Wither) this.getBukkitEntity();
		this.knockback = false;
		this.skull = false;
		
		this.r = r;
	}
	
	public abstract String witherName();
	
	/**
	 * Set the shield state (the aura around wither)
	 * @param active
	 */
	public void setShield(boolean active) {
		this.shield = active;
		Sputnik.sendPacket(wither.getWorld(), new PacketPlayOutEntityMetadata(getId(), datawatcher, true));
	}
	
	/**
	 * @return the boss defense value, in percentage
	 */
	public abstract double getBossDefensePercentage();
	
	public abstract void onTickAsync();
	
	/**
	 * Deals damage to a player with message
	 * @param user
	 * @param attackName
	 * @param damage
	 * @param trueDamage
	 */
	public void damagePlayer(User user, String attackName, double damage, boolean trueDamage) {
		if (user.isAGhost()) return; 
		// damage absorbed by player
		double absorbedDmg = damage;
		
		if (trueDamage) {
			// true dmg, ignore all shields (excluding True DEFENSE)
			user.damage(absorbedDmg, DamageCause.ENTITY_ATTACK, wither);
		} else {
			// normal dmg, can be reduced by normal defense
			absorbedDmg = user.normalDamage(damage, DamageCause.ENTITY_ATTACK, wither);
		}
		
		// send message
		if (attackName != null) user.send("&c%name%&7's %skill% &7hit you for &c%dmg% &7%true%damage."
			.replace("%dmg%", SUtil.commaify(absorbedDmg))
			.replace("%name%", witherName())
			.replace("%true%", trueDamage ? "true " : "")
			.replace("%skill%", attackName)
		);
	}
	
	@Override
	public void onSpawn(LivingEntity entity, SEntity sEntity) {
		entity.setRemoveWhenFarAway(false);
		
		// Initial setups
		entity.setMetadata(SConsts.NO_SYS_HOLONAME, new FixedMetadataValue(SkySimEngine.getPlugin(), true));
		entity.setCustomNameVisible(false);
		
		// Sets up Boss Defense values
		EntityManager.setEntityDefense(entity, getBossDefensePercentage());
		
		// Sets up Hologram entity (for name)
		EntityArmorStand hologramName = new EntityArmorStand(this.world);
		hologramName.setInvisible(true);
		((ArmorStand) hologramName.getBukkitEntity()).setMarker(true);
		hologramName.setBasePlate(false);

		this.hologramEntity = new PacketEntity(hologramName, 100, entity.getLocation().add(0, WitherLordsHandle.NAME_OFFSET, 0));
		this.hologramEntity.setNameVisible(true);
		this.hologramEntity.setCustomName(Sputnik.trans("&e﴾ &c&l" + witherName() + " &e﴿"));

		// Sets up Dialouge Entity (for Dialouge)
		EntityArmorStand hologramDialouge = new EntityArmorStand(this.world);
		hologramDialouge.setInvisible(true);
		((ArmorStand) hologramDialouge.getBukkitEntity()).setMarker(true);
		hologramDialouge.setBasePlate(false);

		this.dialougeEntity = new PacketEntity(hologramDialouge, 100, entity.getLocation().add(0, WitherLordsHandle.DIABOX_OFFSET, 0));
		this.dialougeEntity.setNameVisible(false);
		
		new STask().run(s -> {
			if (wither.isDead()) {
				s.kill();
				SUtil.delayAsync(() -> {
					this.dialougeEntity.destroy();
					this.hologramEntity.destroy();
				}, 20);
				return;
			}
			this.onTickAsync();
		}).startLoop(RunMode.ASYNC, 0, 0);
	}
	
	/**
	 * Say bs
	 * @param what what to say
	 * @param removeIn removes after x ticks
	 */
	public String currentDialog;
	public void say(String what, int removeIn) {
		if (wither.isDead()) return;
		this.currentDialog = what;
		// if dialog == null, we remove the dialog bubble
		if (what == null) {
			this.dialougeEntity.setCustomName("");
			this.dialougeEntity.setNameVisible(false);
			return;
		}
		
		this.dialougeEntity.setCustomName(Sputnik.trans("&4&l" + what));
		this.dialougeEntity.setNameVisible(true);
		entityOwner.broadcast("&4[BOSS] " + witherName() + ": &c" + what);
		// remove the dialog bubble
		if (removeIn > 0) {
			SUtil.delay(() -> this.say(null), removeIn);
		}
	}
	
	public void sayIfVacant(String what, int removeIn) {
		if (currentDialog == null) say(what, removeIn); 
	}
	
	public void say(String what) {
		this.say(what, -1);
	}
	
	public void followAndLookTarget() {
		LivingEntity target = this.wither.getTarget();
		
		if (target == null || frozen || isMoving) return;
		
		// if the player is dead, ignore it
		if (target instanceof Player) {
			Player p = (Player) target;
			User user = User.getUser(p);
			if (user.isAGhost()) {
				selectTarget();
				return;
			}
		}
		
		Location targetLocation = target.getLocation();
		Location witherLocation = wither.getLocation();
 		
		this.getControllerLook().a(
			targetLocation.getX(), target.getEyeLocation().getY(), targetLocation.getZ(), 10.0F,
			(float) bQ()
		);
		
		// 4 blocks psacing
		double distance = NumberConversions.square(targetLocation.getX() - witherLocation.getX()) + NumberConversions.square(targetLocation.getZ() - witherLocation.getZ());
		if (distance <= 16) return;
		
		// (vt - vs) / || vt - vs ||
		double speedScalarFactor = 0.45d;
		double heightAddition = hoverHeight;
		
		Vector directionVector = targetLocation.add(0, heightAddition, 0)
			.toVector().subtract(this.wither.getLocation().toVector())
		.normalize();
		
		Vector lookVector = target.getLocation()
			.toVector().subtract(this.wither.getLocation().toVector())
		.normalize();
		
		Location newPath = wither.getLocation().add(directionVector.multiply(speedScalarFactor));
		newPath.setDirection(lookVector);
		
		this.wither.teleport(
			newPath
		);
	}
	
	/**
	 * Normal .teleport() with accurate head rotation for the
	 * clients
	 * @param loc
	 */
	public void teleportStrict(Location loc) {
		this.wither.teleport(loc);
		// update the rotation
		Sputnik.sendPacket(wither.getWorld(), new PacketPlayOutEntityHeadRotation(
			this, (byte) MathHelper.d(wither.getLocation().getYaw() * 256.0F / 360.0F)
		));
	}
	
	@Override
	public void m() { // on tick (external)
		if (this.bD()) {
			// set gravity all to 0 (disable gravity)
			this.aY = false;
			this.aZ = 0.0F;
			this.ba = 0.0F;
			this.bb = 0.0F;
		} else if (this.bM()) {
			this.doTick();
		}
	}
	
	@Override
	public void b(int i, int j) {
		// This datawatcher serves as a look controller of
		// the 2 secondary skulls. (17 = main skull, 17 + i = secondaries)
		// The value of this datawatcher is the entity ID of which
		// the specified skull looks at
		this.datawatcher.watch(17 + i, 0);
	}
	
	@Override
	public int cl() {
		// NOTE
		return 0; 
		// cl is the "invulnerablity" tick of a wither. This "invul" field is
		// used to change the size and color of a wither.
		// cl() determines if a wither can be damaged or not (> 0 = no damage)
		// We override cl() and set to 0 so wither extending this class can be damaged regardless.
	}
	
	@Override
	public void t_() {
		super.t_();
		followAndLookTarget();
		// override the invul tag
		this.r(r);
	}
	
	/**
	 * [SS Simple] Move to specified location
	 * @param loc
	 * @param vectorSpeed
	 * @param doWhenReach
	 */
	public void moveToLocation(Location loc, double vectorSpeed, Runnable doWhenReach) {
		this.moveToLocation(loc, vectorSpeed, doWhenReach, null, null);
	}
	
	// the accelerator to be multipled with vector speed
	public float speedAcceleratorFactor = 1.0f;
	
	// flag for halting
	private boolean haltAllMovingTask = false;
	private STask movingTask;
	
	/**
	 * Stop EVERY move to location action
	 */
	public void stopMovingToLocation() {
		this.haltAllMovingTask = true;
		if (this.movingTask != null) {
			this.movingTask.kill();
		}
		// allow again
		this.isMoving = false;
		this.haltAllMovingTask = false;
	}
	
	// flag for moving status
	public boolean isMoving = false;
	/**
	 * Move to specified location
	 * 
	 * @param loc
	 * @param vectorSpeed
	 * @param doWhenReach
	 * @param haltSignal
	 * @param onError
	 */
	public void moveToLocation(Location loc, double vectorSpeed, Runnable doWhenReach, AtomicBoolean haltSignal, Runnable onError) {
		if (wither.isDead()) {
			if (onError != null) onError.run();
			return;
		}
		
		if (isMoving) {
			if (onError != null) onError.run();
			return;
		}
		
		// freeze the thing first
		this.isMoving = true;
		boolean stateBefore = frozen;
		this.frozen = true;
		
		this.movingTask = new STask().run(st -> {
			// if the thang is dead, gone
			if (wither.isDead()) {
				st.kill();
				isMoving = false;
				return;
			}
			
			// on halt signal or emergency halt (entity-wide)
			if (haltAllMovingTask || (haltSignal != null && haltSignal.get())) {
				this.frozen = stateBefore;
				isMoving = false;
				// execute "onError"
				if (onError != null) onError.run();
				st.kill();
				// stop
				return;
			}
			
			if (wither.getLocation().distanceSquared(loc) <= 1) {
				this.frozen = stateBefore;
				
				// assure the location
				Location temp = loc.clone();
				
				// he will keep his yaw & pitch
				temp.setYaw(wither.getLocation().getYaw()); // set the yaw & pitch to that of the wither
				temp.setPitch(wither.getLocation().getPitch());
				
				// tp
				teleportStrict(temp);
				
				// stop the task
				st.kill();
				isMoving = false;
				
				// do when reach
				if (doWhenReach != null && !wither.isDead()) {
					doWhenReach.run();
				}
				
				return;
			}
			
			// get the next location by mul vecSpeed * accelerator
			Location nextLocation = wither.getLocation()
				.add(loc.toVector().subtract(wither.getLocation().toVector())
				.normalize().multiply(vectorSpeed * speedAcceleratorFactor)
			);
			
			// move and look at the location
			lookAtLocation(loc);
			wither.teleport(nextLocation);
			
			wither.teleport(wither.getLocation()
				.setDirection(loc.toVector().subtract(wither.getLocation()
			.toVector())));
			
			// increment the counter
			st.counter++;
		}).startLoop(RunMode.SYNC, 0, 0);
	}
	
	public void enrage() {
		entityOwner.sendSubtitleWithChat( 
			"&c⚠ %d is enraged! ⚠".replace("%d", witherName()),
		0, 50, 0);
	}
	
	// strike a lightning and deal 3x boss dmg
	public void strikeLightning(Location l, boolean damagePlayers) {
		SUtil.lightningLater(l, true, 0);
		// scan for nearby players
		if (damagePlayers) Sputnik.hittablePlayers(l, 2, 4, 2).forEach(p -> {
			User user = User.getUser(p);
			if (user == null) return;
			damagePlayer(
				user, "Static Field", 
				getDamageDealt() * 3, false
			);
		});
	}
	
	// tnt poop
	// maxor and necron poops out tnt every 5s dealing double the dmg
	public void witherTNT() {
		TNTPrimed tnt = wither.getWorld().spawn(wither.getLocation(), TNTPrimed.class);
		tnt.setFuseTicks(72);
		// explodes
		SUtil.delay(() -> {
			// sound
			tnt.getLocation().playSound(Sound.EXPLODE, 1, 1);
			// animation
			playLargeExplosion(tnt.getLocation());
			// damage players
			Sputnik.hittablePlayers(tnt.getLocation(), 5, 5, 5).forEach(p -> {
				User user = User.getUser(p);
				if (user == null)
					return;
				damagePlayer(user, "Wither TNT", getDamageDealt() * 2, false);
			});
			// remove tnt after
			tnt.remove();
		}, 70);
	}
	
	// storm & necron
	public void stormShootFireball() {
		Dungeon dungeon = this.entityOwner;
		DungeonUser duser = SUtil.getRandom(dungeon.getAlivePlayersList());
		
		if (duser == null) return;
		
		wither.teleport(
			wither.getLocation().setDirection(duser.getInternal().toBukkitPlayer().getEyeLocation()
			.toVector().subtract(wither.getEyeLocation().toVector())
		));
		
		shootFireball(wither.getLocation().add(0, -1, 0)).onDeath(f -> {
			// explodes the fireball and deal dmg
			Fireball fireball = (Fireball) f.getBukkitEntity();
			
			// explosion effects
			fireball.getLocation().playEffect(Effect.EXPLOSION_HUGE, 0);
			fireball.getLocation().playSound(Sound.EXPLODE, 1, 0);
			SUtil.lightningLater(fireball.getLocation(), true, 0);
			// strike 6 consecutive 
			strikeLightningsConsecutively(fireball.getLocation(), 6);
			
			// dmg will be 5% of max hp
			Sputnik.hittablePlayers(fireball.getLocation(), 5, 5, 5).forEach(p -> {
				User user = User.getUser(p);
				if (user == null) return;
				
				double absorbedDmg = user.toBukkitPlayer().getMaxHealth() * 0.05;
				damagePlayer(user, "Lightning Fireball", absorbedDmg, true);
			});
		});
	}
	
	public void strikeLightningsConsecutively(Location l, int lightningCount) {
		TimedActions actions = new TimedActions();
		for (int i = 0; i < lightningCount; i++) {
			actions.add(() -> {
				// striek lightning around 5 blocks of the player
				l.add(SUtil.randInt(-5, 5), 0, SUtil.randInt(-5, 5));
				// execute
				strikeLightning(l, true);
			}).wait(2);
		}
		actions.run();
	}
	
	// static field ability, strike lightning around alive players
	// used by Storm & Necron
	public void staticField() {
		Dungeon dungeon = this.entityOwner;
		dungeon.getAlivePlayersList().forEach(u -> {
			int lightingCount = 8; // strike 8 consecutive lightnings
			strikeLightningsConsecutively(u.getInternal().getLocation(), lightingCount);
		});
	}
	
	// launch the greatsword
	public void launchGreatswordToLoc(GiantItemDisplay sword, Location l, Runnable onReach) {
		if (sword == null) return;
		
		new STask().run(st -> {
			// if the thang is dead, gone
			if (sword.isDead()) {
				st.kill();
				return;
			}
			
			if (sword.getLocation().distanceSquared(l) <= 1) {
				// assure the location
				sword.teleport(l);
				st.kill();
				
				// do when reach
				if (onReach != null && !sword.isDead()) {
					onReach.run();
				}
				
				// play a clank sound
				l.playSound(Sound.ITEM_BREAK, 10, 0);
				
				return;
			}
			
			// get the next location by mul vecSpeed * accelerator
			Location nextLocation = sword.getLocation()
				.add(l.toVector().subtract(sword.getLocation().toVector())
				.normalize().multiply(1.25) // 2bps
			);
			
			// move and look at the location
			sword.teleport(nextLocation);
			
			// increment the counter
			st.counter++;
		}).startLoop(RunMode.SYNC, 0, 0);
	}
	
	public TimedActions saySequencedDialog(String[] dialog) {
		TimedActions dialogScheduler = new TimedActions();
		for (String message : dialog) {
			dialogScheduler.add(() -> say(message)).wait(DIALOG_GAP);
		}
		return dialogScheduler;
	}
	
	/**
	 * Get the right head location
	 * @return the location of the right head
	 */
	public Location leftHeadLocation() {
		Vector vec = wither.getLocation().getDirection().normalize();
		return wither.getEyeLocation().add(Sputnik.rotateAroundAxisY(vec, -90).normalize().multiply(1));
	}
	
	
	/**
	 * Get the right head location
	 * @return the location of the right head
	 */
	public Location rightHeadLocation() {
		Vector vec = wither.getLocation().getDirection().normalize();
		return wither.getEyeLocation().add(Sputnik.rotateAroundAxisY(vec, 90).normalize().multiply(1));
	}
	
	/**
	 * Shoot a wither skull from a specified skull to an entity
	 * @param target
	 * @param skullIndex
	 */
	public void shoot(LivingEntity target, int skullIndex) {
		if (target == null) return;
		
		wither.teleport(
			wither.getLocation().setDirection(target.getEyeLocation()
			.toVector().subtract(wither.getEyeLocation().toVector())
		));
		this.getControllerLook().a(
			target.getLocation().getX(), target.getEyeLocation().getY(), target.getLocation().getZ(), 10.0F,
			(float) bQ()
		);
		
		if (skullIndex == 0 || skullIndex > 2) { 
			WitherSkull leftSkull = shootWitherSkull(leftHeadLocation().add(0, -0.3, 0));
			leftSkull.setShooter((ProjectileSource) wither);
		}
		
		if (skullIndex == 1 || skullIndex > 2) { 
			WitherSkull middleSkull = shootWitherSkull(wither.getEyeLocation().add(0, -0.3, 0));
			middleSkull.setShooter((ProjectileSource) wither);
		}
		
		if (skullIndex == 2 || skullIndex > 2) { 
			WitherSkull rightSkull = shootWitherSkull(rightHeadLocation().add(0, -0.3, 0));
			rightSkull.setShooter((ProjectileSource) wither);
		}
	}
	
	/**
	 * Controller look at location (janky asf)
	 * @param loc
	 */
	public void lookAtLocation(Location loc) {
		// look
		this.getControllerLook().a(
			loc.getX(), loc.getY(), loc.getZ(),
			10.0F,
			(float) bQ()
		);
	}
	
	/**
	 * Play the wither hiss sound
	 */
	public void playHiss() {
		wither.getWorld().playSound(wither.getLocation(), Sound.WITHER_SHOOT, 1, 1);
	}
	
	/**
	 * Spawn mobs with animation (F7)
	 */
	public void arcSpawnMobs() {
		WitherLordsHandle handle = (WitherLordsHandle)entityOwner.getDungeonBoss();
		if (handle.activeWitherSkeletons >= WitherLordsHandle.WITHER_CAP - 2) return;  
		
		List<Double> xPositions = new ArrayList<>();
		List<Double> yPositions = new ArrayList<>();
		
		// generate a parabolic arc of points for the mob spawning trajectory
		for (double t = 2.0; t < (35 * Math.PI) / 4; t += 0.22) {
			xPositions.add(t);
			yPositions.add((-0.28 * Math.pow(t - 4, 2)) + 3.5);
		}
		
		Vector v = wither.getLocation().getDirection().normalize().multiply(-1);
		
		// calculate two directions offset by +45 and -45 degrees for arc spawning
		Vector v1 = Sputnik.rotateAroundAxisY(v, +45);
		Vector v2 = Sputnik.rotateAroundAxisY(v1.clone(), -90);
		
		TimedActions actions = new TimedActions();
		boolean v1Reached = false, v2Reached = false;
		
		// determine the number of mobs to spawn per arc
		int sum = WitherLordsHandle.WITHER_CAP - handle.activeWitherSkeletons; // Total mobs we can spawn
		int p1Spawn = sum / 2; // half of them for the first arc
		
		for (int i = 0; i < xPositions.size(); i++) {
			final int t = i;
			
			// next position along arc 1 (v1)
			Location l1 = wither.getLocation().add(v1.clone().multiply(xPositions.get(t)));
			l1.setY(l1.getY() + yPositions.get(t));
			
			// next position along arc 2 (v2)
			Location l2 = wither.getLocation().add(v2.clone().multiply(xPositions.get(t)));
			l2.setY(l2.getY() + yPositions.get(t));
			
			if (!v1Reached) {
				if (!l1.getBlock().getType().isSolid()) {
					// if hasnt hit a solid block, play a particle
					// only every 3 sample to reduce the amount of particles
					// and delay
					if (i % 3 == 0) actions.add(() -> {
						playSummoningParticles(l1);
						return;
					});
				} else {
					// otherwise, if a solid block is reached, spawn mobs
					v1Reached = true;
					actions.add(() -> {
						Location spawnLoc = l1.clone().add(0, 2, 0);
						if (spawnLoc.getBlock().getType().isSolid()) return;
						for (int j = 0; j < p1Spawn; j++) {
							// prechecks
							spawnSkeletonWorkers(handle, spawnLoc);
						}
					});
				}
			}
			
			// same logic as 1
			if (!v2Reached) {
				if (!l2.getBlock().getType().isSolid()) {
					// if hasnt hit a solid block, play a particle
					// only every 3 sample to reduce the amount of particles
					// and delay
					if (i % 3 == 0) actions.add(() -> {
						playSummoningParticles(l2);
						return;
					});
				} else {
					// set the flag to stop overlap
					v2Reached = true;
					actions.add(() -> {
						Location spawnLoc = l2.clone().add(0, 2, 0);
						if (spawnLoc.getBlock().getType().isSolid()) return;
						for (int j = 0; j < sum - p1Spawn; j++) {
							// prechecks
							spawnSkeletonWorkers(handle, spawnLoc);
						}
					});
				}
			}
		}
		// exec
		actions.run();
	}
	
	/**
	 * Same shit
	 * @param handle
	 * @param spawnLoc
	 */
	private void spawnSkeletonWorkers(WitherLordsHandle handle, Location spawnLoc) {
		handle.activeWitherSkeletons++;
		LivingEntity e = handle.spawnWitherMinerOrGuard(spawnLoc);
		
		e.onDeath(n -> {
			handle.spawnedSkeletons.remove(e);
			handle.activeWitherSkeletons--;
		});
	}
	
	/**
	 * Boilerplate go brr
	 * @param loc
	 */
	void playSummoningParticles(Location loc) {
		World w = loc.getWorld();
		for (int i = 0; i < 12; i++) {
			w.spigot().playEffect(
				loc, Effect.LARGE_SMOKE, 0,
				1, 255.0f / 255.0f, 255.0f / 255.0f, 255.0f / 255.0f, 0, 0, 20
			);
			w.spigot().playEffect(
				loc, Effect.WITCH_MAGIC, 0,
				1, 255.0f / 255.0f, 255.0f / 255.0f, 255.0f / 255.0f, 0, 0, 20
			);
			w.playEffect(
				loc, Effect.POTION_SWIRL, 0
			);
			w.spigot().playEffect(
				loc, Effect.COLOURED_DUST,
				0, 1, 254.0f / 255.0f, 33.0f / 255.0f, 1.0f / 255.0f, 1, 0, 20
			);
			w.spigot().playEffect(
				loc, Effect.COLOURED_DUST,
				0, 1, 254.0f / 255.0f, 33.0f / 255.0f, 1.0f / 255.0f, 1, 0, 20
			);
		}
	}
	
	/**
	 * Play hyperion-style explosion
	 * @param loc
	 */
	public void playLargeExplosion(Location loc) {
		Sputnik.sendPacket(wither.getWorld(), 
			new PacketPlayOutWorldParticles(EnumParticle.EXPLOSION_LARGE, true,
				(float) loc.getX(), (float) loc.getY(),
				(float) loc.getZ(), 0, 0, 0, 7, 6
			)
		);
	}
	
	/**
	 * Play hyperion-style explosion with radius
	 * @param loc
	 */
	public void playLargeExplosion(Location loc, float radius) {
		Sputnik.sendPacket(wither.getWorld(), 
			new PacketPlayOutWorldParticles(EnumParticle.EXPLOSION_LARGE, true,
				(float) loc.getX(), (float) loc.getY(),
				(float) loc.getZ(), 0, 0, 0, radius, 6
			)
		);
	}
	
	/**
	 * Shoot a wither skull from a specified location
	 * @param launchLocation
	 * @return
	 */
	public WitherSkull shootWitherSkull(Location launchLocation) {
		Location location = launchLocation;
		Vector direction = location.getDirection().multiply(10);

		var launch = new EntityWitherSkull(world, this, direction.getX(), direction.getY(),
				direction.getZ());
		
		((EntityWitherSkull) launch).projectileSource = (ProjectileSource)this.bukkitEntity;
		launch.setPositionRotation(location.getX(), location.getY(), location.getZ(), location.getYaw(),
				location.getPitch());
		
		this.world.addEntity(launch);
		
		return (WitherSkull)launch.getBukkitEntity();
	}
	
	/**
	 * Shoot a fireball from a specified location
	 * @param launchLocation
	 * @return
	 */
	public LargeFireball shootFireball(Location launchLocation) {
		Location location = launchLocation;
		Vector direction = location.getDirection().multiply(10);

		var launch = new EntityLargeFireball(world, this, direction.getX(), direction.getY(),
				direction.getZ());
		
		((EntityLargeFireball) launch).projectileSource = (ProjectileSource)this.bukkitEntity;
		launch.setPositionRotation(location.getX(), location.getY(), location.getZ(), location.getYaw(),
				location.getPitch());
		
		this.world.addEntity(launch);
		
		return (LargeFireball)launch.getBukkitEntity();
	}
	
	/**
	 * Will run on wither suffocate (hit a wall)
	 */
	public void onHitAWall() {}
	
	@Override
	public boolean damageEntity(DamageSource damagesource, float f) {
		if (damagesource == DamageSource.STUCK) {
			onHitAWall();
			return false;
		}
		return super.damageEntity(damagesource, f);
	}
	
	/**
	 * Subject to change later on
	 */
	public void selectTarget() {
		if (wither.getTarget() == null) {
			// prioritize MAGE first
			for (DungeonUser du : entityOwner.getAlivePlayersList()) {
				if (du.getDungeonClass() == EnumClass.MAGE) {
					wither.setTarget(du.getInternal().toBukkitPlayer());
					break;
				}
			}
			
			// if not select random
			if (wither.getTarget() == null) {
				wither.setTarget(
					SUtil.getRandom(entityOwner.getAlivePlayersList())
					.getInternal().toBukkitPlayer()
				);
			}
		}
	}
	
	public ArmorStand spawnEnragedBubble() {
		ArmorStand armorStand = wither.getWorld()
			.spawn(wither.getLocation().add(0, WitherLordsHandle.NAME_OFFSET - 0.45, 0),
		ArmorStand.class);
		armorStand.setBasePlate(false);
		armorStand.setVisible(false);
		armorStand.setGravity(false);
		armorStand.setHelmet(SUtil.getSkullURL(WitherLordsHandle.ENRAGED_TEXTURE));
		return armorStand;
	}
	
	@Override
	public LivingEntity spawn(Location location) {
		this.world = ((CraftWorld) location.getWorld()).getHandle();
		this.setPosition(location.getX(), location.getY(), location.getZ());
		this.world.addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
		return (LivingEntity) this.getBukkitEntity();
	}
}