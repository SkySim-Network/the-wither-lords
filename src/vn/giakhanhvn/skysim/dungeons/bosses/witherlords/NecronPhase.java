package vn.giakhanhvn.skysim.dungeons.bosses.witherlords;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftArmorStand;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftWither;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.google.common.util.concurrent.AtomicDouble;

import lombok.var;
import net.minecraft.server.v1_8_R3.WorldServer;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.WitherLordsHandle.WitherLordPhase;
import vn.giakhanhvn.skysim.dungeons.systems.DungeonUser;
import vn.giakhanhvn.skysim.entity.SEntity;
import vn.giakhanhvn.skysim.entity.SEntityType;
import vn.giakhanhvn.skysim.extra.beam.Beam;
import vn.giakhanhvn.skysim.user.User;
import vn.giakhanhvn.skysim.util.BlockFallAPI;
import vn.giakhanhvn.skysim.util.STask;
import vn.giakhanhvn.skysim.util.SUtil;
import vn.giakhanhvn.skysim.util.Sputnik;
import vn.giakhanhvn.skysim.util.TimedActions;
import vn.giakhanhvn.skysim.util.STask.RunMode;
import vn.giakhanhvn.skysim.util.customentities.GiantItemDisplay;

public class NecronPhase implements WitherLordPhase {
	public WitherLordsHandle lords;
	public Wither wither;
	public NecronWither necronWither;
	
	private World world;
	
	// state flags
	boolean[] platformDestroyed = new boolean[6];
	boolean[] towerCharged = new boolean[6]; // actually there's no tower at index 0, but i keep this here for indexing purposes
	
	public NecronPhase(WitherLordsHandle handle) {
		this.lords = handle;
		this.world = lords.getHandle().getWorld();
	}
	
	@Override
	public void test(User executor, String[] args) {
		necronWither.resetAbilities();
		necronWither.resetAbilitiesCooldown();
	}
	
	public DungeonUser getRandomPlayer() {
		return SUtil.getRandom(
			lords.getHandle().getAlivePlayersList()
		);
	}
	
	public void toggleTowerCharge(int index) {
		if (index == 0) throw new IllegalArgumentException("there's no goddamn tower at 0");
		towerCharged[index] = !towerCharged[index];
		
		// only on charge, activate the animation core
		if (!towerCharged[index]) return;
		
		Location towerOrigin = lords.necronTowers[index];
		new STask().run(s -> {
			// 27 is the height of the tower
			for (int i = 0; i < 28; i++) {
				Location target = towerOrigin.clone()
					// warp around to prevent it from shooting out of the tower
					.add(0, (s.counter + i) % 28, 0)
				;
				
				// the entire segment of the tower
				var cuboids = Sputnik.cuboid(
					target.clone().add(1, 0, 1),
					target.clone().add(-1, 0, -1)
				);
				
				// the first predicate is for "clearing" the tower when its disabled
				// 5 blocks gap between the sea lanterns
				// FOR FUTURE ME:
				// the reason why i do "i mod 7" is because, the sea lantern will spawn once every
				// 6 blocks. Why? because in hypixel, the gap is 5, BUT the height of the lantern
				// is 2 blocks, so we space it out for that
				if (!towerCharged[index] || i % 7 != 0) {
					// clear the tower by setting iron blocks
					cuboids.forEach(b -> {
						b.setType(Material.IRON_BLOCK);
					});
					continue;
				}
				
				// the "charging up effects"
				cuboids.forEach(b -> {
					// well, as we do index % 7, we need to cover the upper one too
					b.getRelative(BlockFace.UP).setType(Material.SEA_LANTERN);
					b.setType(Material.SEA_LANTERN);
				});
				// skip the block above so it wont be consumed
				// by the IRON_BLOCK setter up there
				i++;
			}
			
			// kill the task (after shit been cleared)
			if (!towerCharged[index]) {
				s.kill();
				return;
			}
			
			s.counter++; // move it up
		}).startLoop(RunMode.SYNC, 0, 2);
	}
	
	public void dropPlatform(int platformIndex) {
		if (platformDestroyed[platformIndex]) return;
		List<Location> platform = lords.necronPlatforms[platformIndex];
		// scan through the entire platform and delete the whole thing (not just the top layer)
		
		for (Location loc : platform) {
			Material mat = loc.getBlock().getType();
			byte data = loc.getBlock().getData();
			
			// destroy the block itself
			loc.getBlock().setType(Material.AIR);
			// spawn a falling block (1/3 chance)
			if (SUtil.random(0, 2) == 0) BlockFallAPI.sendBlock(
				Sputnik.getBlockMatchPos(loc), 
				mat, data, world, 30
			);
			
			// loop from it downwards to clear blocks beneath it
			for (int yOffset = -1; yOffset >= -4; yOffset--) {
				Block block = loc.clone().add(0, yOffset, 0).getBlock();
				// dont break if air (or lava)
				if (
					block.getType() == Material.AIR 
					|| block.getType() == Material.LAVA
					|| block.getType() == Material.STATIONARY_LAVA
				) continue;
				// remove it
				block.setType(Material.AIR);
			}
		}
		// finished
		platformDestroyed[platformIndex] = true;
	}
	
	@Override
	public void start() {
		// normal initialization
		lords.bossbar.setTitle("&c&lNecron");
		
		// spawn
		wither = (Wither) new SEntity(lords.necronSpawnPoint, SEntityType.NECRON_TECH_DEMO,
			Sputnik.getNMSWorld(lords.necronSpawnPoint), this
		).getEntity();
		// assignments
		necronWither = (NecronWither) ((CraftWither) wither).getHandle();

		// the core task loop
		new STask().run(s -> {
			// if the current phase isnt necron, stop the global ticker
			if (!(lords.currentPhase instanceof NecronPhase)) {
				s.kill();
				return;
			}
			
			if (wither.isDead()) {
				s.kill();
				this.end();
				return;
			}
			
			s.counter++;
		}).startLoop(RunMode.SYNC, 0, 0);
	}
	
	@Override
	public void end() {
		
	}
	
	/**
	 * The wither representing this phase
	 * @author GiaKhanhVN
	 */
	public static class NecronWither extends CustomWitherEntity {
		// the reference to outside systems
		private NecronPhase core;
		static final double NECRON_VECTOR_SPEED = StormPhase.STORM_VECTOR_SPEED * 2;
		
		// flags
		public boolean frenzyActivated; // if the boss frenzy is already active
		public boolean dying; // if the boss is dying
		
		// after explosion ended (frenzy end), he will stun for 10s, during this period
		// none of his special ability can be used
		public boolean stun = false;
		
		// necron floor y
		public int necronFloorY;
		
		// construct
		public NecronWither(WorldServer world, NecronPhase storm) {
			super(world, 1);
			this.core = storm;
			this.setShield(true);
			this.entityOwner = core.lords.getHandle();
			this.hoverHeight = 1f;
			this.frozen = true;
			this.DIALOG_GAP = 50;
			
			// get the floor Y
			this.necronFloorY = core.lords.necronHoverLocations.get(0).getBlockY() - 2;
		}
		
		@Override
		public String witherName() {
			return "Necron";
		}
		
		// dialogs
		static String[] WELCOME_DIALOG = new String[] { 
			"Welcome to Necron's Core, you have chosen, OR HAVE BEEN CHOSEN TO GET FUCK IN THE ASS.",
			"FUCK YOU, FUCK YOU SO MUCH, LEAVE THE FUCKING CORE NOOOOW!",
			"GET BACK IN THE FUCKING ENTRANCE AND GO BACK WHERE YOU FUCKING CAME FROM YOU FUCKING ASSHOLES",
			"I fucking hate you so much... Fuck you %party_leader%. I hope you get RAN OVER BY A FUCKING TRAIN!"
		};
		
		static String IMPRESSIVE_TRICK = "That's a very impressive trick. I guess I'll have to handle this myself.";
		
		static String[] NUCLEAR_FRENZY = new String[] {
			"Sometimes when you have a problem, you just need to destroy it all and start again.",
			"WITNESS MY RAW NUCLEAR POWER!"
		};
		
		static String[] RANDOM_FRENZY_DIALOG = new String[] {
			"BOOOOOOOOOOOOOOOOOOOOOOOOOM",
			"HASTA LA VISTA, BABY!",
			"IF YOU WANT TO STAY COOL, DON'T LOOK!"
		};
		
		static String FRENZY_FAILS = "ARGH!";
		static String FRENZY_BREAKS_PLATFORM = "Let's make some space!";
		
		static String GREATSWORDS_USE = "I ensure you, these blades are far sharper than Goldor's ever were.";
		
		static String[] RANDOM_DIALOG = new String[] {
			"Fight for your life!!",
			"Show me how you beat Storm!!!",
			"Not just Midgard, but the entire Nine Realms will end!",
			"I - Necron - was destined to rule over Mankind! Dead or Alive!",
			"The Catacombs made you stronger, but can you beat me?!",
			"You merely adopted the Catacombs! I was molded by them!"
		};
		
		static String[] DEATH = new String[] {
			"All this, for nothing...",
			"I understand your words now, my master.",
			"The Wither Lords... are no more."
		};
		
		@Override
		public String getEntityName() {
			return witherName();
		}
		
		// internal timer
		private long counter = 0;
		// count the hits to manage the frenzy attack
		private int hitCounter = 0;
		// for greatsword spin
		public static final double RADIANS_PER_TICK = (2f * Math.PI) / 100f;
		
		// abilities flags
		public boolean[] activatedAbilities = new boolean[4];
		public int[] cooldownAbilities = new int[4];
		
		// TODO: Abilities controller
		static final int RAPID_FIRE = 0;
		static final int LASER_BEAMS = 1;
		static final int LIGHTNING_FIREBALL = 2;
		static final int GREATSWORDS = 3;
		
		static final int RAPID_FIRE_COOLDOWN = 15 * 20; // 25s
		static final int LIGHTNING_FIREBALL_COOLDOWN = 20 * 20; // 40s
		static final int GREATSWORDS_COOLDOWN = 25 * 20; // 60s
		static final int LASERS_COOLDOWN = 30 * 20; // 80s
		
		// return true if one of the special abilities is activated
		public boolean isSpecialAbilityActive() {
			for (int i = 0; i < activatedAbilities.length; i++) {
				if (activatedAbilities[i]) return true;
			}
			return false;
		}
		
		// reset the activation state of all abilities
		public void resetAbilities() {
			for (int i = 0; i < activatedAbilities.length; i++) {
				activatedAbilities[i] = false;
			}
		}
		
		public void resetAbilitiesCooldown() {
			cooldownAbilities[RAPID_FIRE] = RAPID_FIRE_COOLDOWN;
			cooldownAbilities[LIGHTNING_FIREBALL] = LIGHTNING_FIREBALL_COOLDOWN;
			cooldownAbilities[GREATSWORDS] = GREATSWORDS_COOLDOWN;
			cooldownAbilities[LASER_BEAMS] = LASERS_COOLDOWN;
		}
		
		// activate the frenzy on 80% and 10% hp
		public void onHealthReachesThreshold(double percent) {
			this.frenzyAttack();
		}
		
		@Override
		public void t_() {
			super.t_();
			
			this.hologramEntity.teleport(wither.getLocation().add(0, WitherLordsHandle.NAME_OFFSET, 0));
			this.dialougeEntity.teleport(wither.getLocation().add(0, WitherLordsHandle.DIABOX_OFFSET, 0));
			
			// sync the bossbar
			core.lords.bossbar.setProgress(wither.getHealth() / wither.getMaxHealth());
			
			// before the players enter core, just ignore the entire tick function
			// keep this t_() alive as a bare minimum
			if (!this.finishedPreFight) return;
			
			// the internal clock wont be incremented on dummy state
			counter++;
			
			// dying animation
			if (dying && counter % 3 == 0) {
				wither.damage(0.0001D);
				return;
			}
			
			// frenzy tick
			if (frenzyActivated) {
				this.frenzyTick();
			}
			
			// tick down ability cooldowns (ONLY if none of them is active atm)
			if (!isSpecialAbilityActive()) // TODO: REVOKE IF NEEDED
			for (int i = 0; i < cooldownAbilities.length; i++) {
				if (cooldownAbilities[i] > 0) cooldownAbilities[i]--;
			}
			
			// say random message
			if (!stun && !dying && counter % SUtil.random(150, 260) == 0 // every 5.5s to 13s
			 && this.currentDialog == null // only if there's no current dialog
			) {
				// frenzy uses another set of messages
				this.say(SUtil.randArr(frenzyActivated ? RANDOM_FRENZY_DIALOG : RANDOM_DIALOG), DIALOG_GAP);
			}
			
			// abilities and normal attacks should not be triggered
			// during frenzy, while stun (the ? on top) and dying (anim)
			if (frenzyActivated || stun || dying) return;
			
			/* NORMAL ABILITIES / ATTACKS */
			// poops tnt every 5s, the only exception is during special abilities (rapid fire, laser etc)
			if (!isSpecialAbilityActive() && counter % 100 == 0) {
				this.witherTNT();
			}
			
			// default wither skulls attack (only if not during special ability)
			if (!isSpecialAbilityActive() && wither.getTarget() != null) {
				if (counter % SUtil.random(10, 15) == 0) {
					playHiss();
					// rapidly fire
					new TimedActions().add(() -> shoot(wither.getTarget(), 1)) // shoot the middle-head first
					.wait(3).add(() -> {
						// shoot the other 2 heads
						shoot(wither.getTarget(), 0);
						shoot(wither.getTarget(), 2);
					}).run();
				}
			}
			
			/* SPECIAL ABILITIES */
			if (SUtil.random(0, 8) == 0) {
				this.rapidFire();
			}
			
			if (SUtil.random(0, 8) == 0) {
				this.fireballAttack();
			}
			// SIDE EFFECTS OF FIREBALL ATTACK
			// during fireball attack, strike lightning to all players every 2s
			if (activatedAbilities[LIGHTNING_FIREBALL] && counter % 40 == 0) {
				this.staticField();
			}
			
			if (SUtil.random(0, 8) == 0) {
				this.laserWallAttack();
			}
			
			if (SUtil.random(0, 8) == 0) {
				this.greatswordActive();
			}
			// GREATSWORDS TICK
			int activeSwords = countActiveSwords();
			for (int i = 0; i < flyingSwords.length; i++) {
				if (flyingSwords[i] == null) continue;
				Location loc = this.circleLoc(
					wither.getLocation(), 3,
					// for every tick, moves RAD_PER_TICK
					// and keep distance i * (2pi / 4) with
					// the other swords
					(RADIANS_PER_TICK * counter) + (i * (2 * Math.PI / activeSwords))
				);
				// moves them down by 1 block
				flyingSwords[i].teleport(loc.add(0, -1, 0));
				// effects
				if (counter % 8 == 0) {
					Location effectLoc = loc.clone().add(0, -0.75, 0);
					// play the greatsword particles
					effectLoc.getWorld().spigot().playEffect(
						effectLoc, Effect.VILLAGER_THUNDERCLOUD, 
						0, 1, 255.0f / 255.0f, 255.0f / 255.0f, 255.0f / 255.0f, 0, 0, 20
					);
				}
			}
		}
		
		// shit code
		public int countActiveSwords() {
			int active = 0;
			for (int i = 0; i < flyingSwords.length; i++) {
				if (flyingSwords[i] != null) active++; 
			}
			return active;
		}
		
		// TODO: Rotating Beams | voidgloom ahh beam
		final static double HALF_CIRCLE = Math.PI;
		final static double QUART_CIRCLE = Math.PI / 2d;
		
		final static double BEAM_HEIGHT = 20;
		final static double BEAM_AMOUNT = 20;
		final static double BEAM_RADIUS = 22;
		
		final static int IBEAM_DIAMETER = (int)Math.ceil(BEAM_RADIUS * 2);
		final static double BEAM_GAP = BEAM_HEIGHT / BEAM_AMOUNT;
		final static double BEAM_RAD_PER_SEC = (2f * Math.PI) / 300f;
		
		public void laserWallAttack() {
			// if one special ability is already active OR on cooldown, return
			if (isSpecialAbilityActive() || cooldownAbilities[LASER_BEAMS] > 0) return;
			
			// flags
			activatedAbilities[LASER_BEAMS] = true;
			
			boolean stateBefore = this.frozen;
			this.frozen = true;
			
			// hmmm
			moveToLocation(chooseAttackPosition(), NECRON_VECTOR_SPEED, () -> {
				/** FOR THE SMOKE ARC VISUAL EFFECT **/
				// https://www.desmos.com/calculator/hxbwsyknwc
				List<Double> xPositions = new ArrayList<>(); // sampled points X
				List<Double> yPositions = new ArrayList<>(); // Y 
				List<Location> arcParticlesLocations = new ArrayList<>(); // for particles
				
				// generate a parabolic arc of points for "arc effects" trajectory
				for (double t = 0; t < (20 * Math.PI) / 5; t += 0.33) {
					xPositions.add(t);
					// very shit and oddly specific parabolic function 
					yPositions.add((-0.4 * Math.pow(t - 1.75, 2)) + 3.5);
				}
				
				// the origin vector (the normalized vector that points behind the wither)
				Vector lookVec = wither.getLocation().getDirection().normalize().multiply(-1);
				// make 6 arcs, starts at 0 deg offset and marches to 360
				for (int deg = 0; deg < 360; deg += 60) {
					// rotate the imaginary "plane" so that we can slap the xPos and yPos later
					Vector plane = Sputnik.rotateAroundAxisY(lookVec.clone(), deg);
					for (int i = 0; i < xPositions.size(); i++) {
						// next position along arc
						Location l1 = wither.getLocation().add(0, 2, 0).add(plane.clone().multiply(xPositions.get(i)));
						l1.setY(l1.getY() + yPositions.get(i));
						
						// cache for replay later on
						arcParticlesLocations.add(l1);
					}
				}
				
				/** FOR THE ACTUAL MOVING BEAMS **/
				// the head of the wither
				Location origin = wither.getLocation().add(0, 5, 0);
				// the two laser walls
				List<Beam> firstLaserSet = new ArrayList<>();
				List<Beam> secondLaserSet = new ArrayList<>();
				// the laser rotation
				AtomicDouble rotationOffset = new AtomicDouble(0); // in radians
				
				// spawn beams
				for (int i = 0; i < BEAM_AMOUNT; i++) {
					firstLaserSet.add(new Beam(origin, origin).start());
					secondLaserSet.add(new Beam(origin, origin).start());
				}
				
				/**
				 * A persistent HashSet reference used for efficient player collision detection.
				 * Instead of creating a new HashSet every scan cycle, we reuse this set 
				 * and clear it (O(1)) before each use to reduce memory allocation overhead.
				 * 
				 * - This pointer is passed into `updateBeamSetLocations()` every 5 ticks.
				 * - When `hitByWallPointerHashSet` is non-null, it collects players hit by the laser wall.
				 * - Cleared before reuse to maintain efficiency and prevent stale data.
				 */
				HashSet<Player> hitByWallPointerHashSet = new HashSet<>();
				
				new STask().run(s -> {
					// stop this after 15s or the thing is terminated by signal
					// signal shutdown ability
					if (!activatedAbilities[LASER_BEAMS]) {
						s.kill();
						// cleanup the beams
						firstLaserSet.forEach(Beam::stop);
						secondLaserSet.forEach(Beam::stop);
						return;
					}
					
					if (wither.isDead() || s.counter >= 300) {
						s.kill();
						this.frozen = stateBefore;
						// stop firing
						this.activatedAbilities[LASER_BEAMS] = false;
						// initiate the cooldown method
						this.cooldownAbilities[LASER_BEAMS] = LASERS_COOLDOWN;
						// cleanup the beams
						firstLaserSet.forEach(Beam::stop);
						secondLaserSet.forEach(Beam::stop);
						return;
					}
					
					// calculate the angles of the begin & end location of each beams
					double startAngle = rotationOffset.get();
					double endAngle = rotationOffset.get() + HALF_CIRCLE;
					
					// ones that were caught in the wall, we only scan for players every 5 ticks (0.25s)
					// to prevent damage stacking
					HashSet<Player> hitByWall = null;
					if (counter % 5 == 0) {
						// pointer shenanigans
						hitByWall = hitByWallPointerHashSet;
						hitByWall.clear();
					}
					
					// the first set of laser (the one thats 0 degrees offset)
					Location firstColBegin 	= circleLoc(origin, BEAM_RADIUS, startAngle);
					Location firstColEnd 	= circleLoc(origin, BEAM_RADIUS, endAngle);
					
					// update the 1st set of beams
					this.updateBeamSetLocations(hitByWall, firstLaserSet, firstColBegin, firstColEnd);
					
					// the second set of laser (45 degrees offset)
					Location secondColBegin = circleLoc(origin, BEAM_RADIUS, QUART_CIRCLE + startAngle);
					Location secondColEnd 	= circleLoc(origin, BEAM_RADIUS, QUART_CIRCLE + endAngle);
					
					// update the 2nd set of beams
					this.updateBeamSetLocations(hitByWall, secondLaserSet, secondColBegin, secondColEnd);
					
					// deal damage to ones got hit
					if (hitByWall != null && hitByWall.size() > 0) {
						// damage everyone
						hitByWall.forEach(p -> {
							// damage caught players by 25% of their HP
							damagePlayer(User.getUser(p),
								"Laser Wall", 
								p.getMaxHealth() * 0.25, true // 25% true dmg
							);
						});
					}
					
					// increment the angle
					rotationOffset.addAndGet(BEAM_RAD_PER_SEC);
					
					// play the particles
					if (s.counter % 10 == 0) {
						arcParticlesLocations.forEach(l -> {
							l.getWorld().spigot().playEffect(
								l, Effect.LARGE_SMOKE, 0,
								1, 255.0f / 255.0f, 255.0f / 255.0f, 255.0f / 255.0f, 0, 0, 20
							);
						});
					}
					
					s.counter++;
				}).startLoop(RunMode.ASYNC, 0, 1);
			});
		}
		
		// update all beams in a set and collison checking (thread safe)
		private void updateBeamSetLocations(HashSet<Player> hitByWall, List<Beam> beamSet, Location colBegin, Location colEnd) {
			for (int i = 0; i < beamSet.size(); i++) {
				Beam beam = beamSet.get(i);
				double yOffset = i * BEAM_GAP;
				
				// update the location by offsetting downwards from the origins, 
				// each beam will go down BEAM_GAP blocks before reaching the end
				beam.setStartingPosition(colBegin.clone().add(0, -yOffset, 0));
				beam.setEndingPosition(colEnd.clone().add(0, -yOffset, 0));
				
				// the hash set is not null every 10 ticks
				if (hitByWall != null && i == beamSet.size() - 1) { // the last beam is used for collison checking
					// raycast to create a 44 blocks long ray
					this.rayCastAndCollide(hitByWall, beam.rayCast(IBEAM_DIAMETER));
				}
			}
		}
		
		// add all players caught in the laser wall to the set
		private void rayCastAndCollide(HashSet<Player> set, Location[] ray) {
			for (int i = 0; i < ray.length; i++) {
				// basically what i do here is:
				// the casted "ray" is at the bottom of the laser wall,
				// to check for players, a bounding box of size 0.5x22x0.5 is constructed
				// for each individual sample point in "ray"
				Sputnik.hittablePlayers(ray[i], 0.5, BEAM_HEIGHT, 0.5)
				.forEach(p -> {
					set.add(p);
				});
			}
		}
		
		// greatsword thingy
		// the greatswords array (swords that will fly around necron) -- max 4
		public GiantItemDisplay[] flyingSwords = new GiantItemDisplay[4];
		
		// TODO: Greatsword | throwing swords and shieet
		public void greatswordActive() {
			// if one special ability is already active OR on cooldown, return
			if (isSpecialAbilityActive() || cooldownAbilities[GREATSWORDS] > 0) return;
			
			// flags
			activatedAbilities[GREATSWORDS] = true;
			
			boolean stateBefore = this.frozen;
			this.frozen = true;
			
			// move to the center and spawn swords
			moveToLocation(chooseAttackPosition(), NECRON_VECTOR_SPEED, () -> {
				// dialog
				this.say(GREATSWORDS_USE, DIALOG_GAP);
				
				// spawn swords and shiet (4 per round)
				TimedActions actions = spawnSwordsAndThrow().wait(10).add(() -> {
					// after complete the sword throwing shenanigans, resume
					activatedAbilities[GREATSWORDS] = false;
					this.frozen = stateBefore;
					// cooldown
					cooldownAbilities[GREATSWORDS] = GREATSWORDS_COOLDOWN;
				});
				
				actions.doWhile(() -> {
					if (!activatedAbilities[GREATSWORDS]) {
						// stop the ability if a signal is received
						actions.halt();
						// cleanup the remaining swords
						this.cleanUpSwords();
					}
				})
				.run();
			});
		}
		
		// remove all swords
		public void cleanUpSwords() {
			for (int i = 0; i < flyingSwords.length; i++) {
				if (flyingSwords[i] == null) continue; 
				flyingSwords[i].remove();
				flyingSwords[i] = null;
			}
		}
		
		public TimedActions spawnSwordsAndThrow() {
			TimedActions ta = new TimedActions();
			for (int t = 0; t < 2; t++) {
				// we spawn 4 swords in 40 ticks (2s)
				for (int i = 0; i < 4; i++) {
					ta.add(() -> replenishFlyingSword(1))
					.wait(10); // 10 ticks per swords
				}
				ta.wait(10);
				// throw to players
				for (int i = 0; i < 4; i++) {
					ta.add(() -> launchGreatswordToPlayer())
					.wait(30);
				}
			}
			return ta;
		}
		
		public void launchGreatswordToPlayer() {
			// select a target (there's always 1)
			User target = core.getRandomPlayer().getInternal();
			
			// find an appropriate sword
			for (int i = 0; i < flyingSwords.length; i++) {
				GiantItemDisplay sword = flyingSwords[i];
				if (sword == null) continue;
				
				// detach the sword from the main wither
				flyingSwords[i] = null;
				
				// launch towards the TNT basket
				launchGreatswordToLoc(sword, target.getLocation(), () -> {
					// remove the sword
					sword.remove();
					
					// damage in a cuboid (5x5x5) arena
					// play effects & sound
					sword.getLocation().playSound(Sound.EXPLODE, 2.f, 0);
					sword.getLocation().playEffect(Effect.EXPLOSION_HUGE, 0);
					
					// damage the surrounding players
					Sputnik.hittablePlayers(sword.getLocation(), 5, 5, 5).forEach(p -> {
						User user = User.getUser(p);
						// damage by 10 TIMES the boss DPS
						damagePlayer(
							user, "Empowered Greatsword", 
							getDamageDealt() * 10, false
						);
					});
				});
				
				return;
			}
		}
		
		// replenish the flying swords
		public void replenishFlyingSword(int amount) {
			if (amount <= 0 || amount > 4) return;
			// do a for loop, simple
			for (int i = 0; i < flyingSwords.length; i++) {
				if (amount <= 0) break;
				// if there's already one in this slot, skips
				if (flyingSwords[i] != null) continue;
				
				// replenish this sword (because its empty)
				flyingSwords[i] = new GiantItemDisplay(
					wither.getLocation(),
					SUtil.enchant(new ItemStack(Material.DIAMOND_SWORD, 1))
				);
				
				amount--;
			}
		}
		
		// TODO: Fireball attack | the exact same as storm's with the exception of
		// adding the "static field attack" during this period
		public void fireballAttack() {
			// if one special ability is already active OR on cooldown, return
			if (isSpecialAbilityActive() || cooldownAbilities[LIGHTNING_FIREBALL] > 0) return;
			
			// flags
			activatedAbilities[LIGHTNING_FIREBALL] = true;
			
			boolean stateBefore = this.frozen;
			this.frozen = true;
			
			// move to the center and then shoot fireballs for 8-14 times
			moveToLocation(chooseAttackPosition(), NECRON_VECTOR_SPEED, () -> {
				TimedActions ta = new TimedActions();
				for (int i = 0; i < SUtil.randInt(8, 14); i++) {
					ta.add(() -> {
						if (dying) return;
						stormShootFireball();
					}).wait(15);
				}
				ta.add(() -> {
					// after complete, resume
					activatedAbilities[LIGHTNING_FIREBALL] = false;
					this.frozen = stateBefore;
					// cooldown
					cooldownAbilities[LIGHTNING_FIREBALL] = LIGHTNING_FIREBALL_COOLDOWN;
				});
				ta.doWhile(() -> {
					if (!activatedAbilities[LIGHTNING_FIREBALL]) {
						// stop the ability if a signal is received
						ta.halt();
					}
				});
				ta.run();
			});
		}
		
		// TODO: RAPID FIRE | the exact same as maxor's
		public void rapidFire() {
			// if one special ability is already active OR on cooldown, return
			if (isSpecialAbilityActive() || cooldownAbilities[RAPID_FIRE] > 0) return;
			
			this.activatedAbilities[RAPID_FIRE] = true;
			
			boolean stateBefore = this.frozen;
			this.frozen = true;
			
			new STask().run(st -> {
				// signal shutdown ability
				if (!activatedAbilities[RAPID_FIRE]) {
					st.kill();
					return;
				}
				
				if (wither.isDead() || st.counter >= 200) {
					st.kill();
					this.frozen = stateBefore;
					// stop firing
					this.activatedAbilities[RAPID_FIRE] = false;
					// initiate the cooldown method
					this.cooldownAbilities[RAPID_FIRE] = RAPID_FIRE_COOLDOWN;
					return;
				}
				
				if (st.counter % 2 == 0) {
					this.playHiss();
					shoot(wither.getTarget(), (int) (Math.random() * 3d));
				}
				st.counter++;
			}).startLoop(RunMode.SYNC, 1, 1);
		}
		
		public Location chooseAttackPosition() {
			return SUtil.randArr(core.lords.necronPositions);
		}
		
		// TODO: Frenzy | increase by 1 block every 1.25s
		int explosionRadius = 1;
		public void frenzyAttack() {
			// stop all abilities
			this.stopMovingToLocation(); // halt movement systems
			this.resetAbilities();
			
			// blind the players first
			core.lords.getHandle().getAlivePlayersList().forEach(p -> {
				// blind for 2s first
				p.getInternal().addBukkitPotionEffect(PotionEffectType.BLINDNESS, 1, 40);
			});
			// tp necron to the center and engage the attack
			wither.teleport(core.lords.necronSpawnPoint);
			
			// dialog & side effects
			this.say(SUtil.randArr(NUCLEAR_FRENZY), DIALOG_GAP);
			this.chargePlatform();
			
			// initiate the variables
			this.frenzyActivated = true;
			this.explosionRadius = 5;
			this.hitCounter = -5; // prevent the boss from dying too soon
			
			// freeze the wither during this period
			this.frozen = true;
		}
		
		// utils for frenzy
		public void frenzyTick() {
			// the visual effects of the boss
			if (counter % 10 == 0) this.playFrenzyVisuals();
			// increment every 1.25s (24 ticks)
			if (counter % 24 == 0) {
				// max 60 blocks
				explosionRadius = Math.min(60, explosionRadius + 1);
			}
			// damage players every 1s
			if (counter % 20 == 0) {
				Sputnik.hittablePlayers(wither.getLocation(), explosionRadius, 30, explosionRadius)
				.forEach(p -> {
					damagePlayer(
						User.getUser(p), "Frenzy",
						p.getMaxHealth() * 0.35,// 35% of the HP
						true
					);
				});
			}
			// check for frenzy end
			if (explosionRadius <= 0) {
				this.endFrenzy();
				return;
			}
			// strike lightning every quarter of a second
			if (chargedPlatform > 0 && counter % 5 == 0) {
				List<Location> platform = core.lords.necronPlatforms[this.chargedPlatform];
				// strike a random location of the platform
				strikeLightning(SUtil.getRandom(platform), true);
			}
		}
		
		public void endFrenzy() {
			// prevent shit from breaking by only allow ending once
			// started
			if (!this.frenzyActivated) return;
			
			// stop the main loop
			this.frenzyActivated = false;
			
			// dialog
			this.say(FRENZY_FAILS, DIALOG_GAP);
			
			// enrage (stun) for 10 seconds
			// spawn the enraged animation
			this.stun = true;
			ArmorStand bubble = spawnEnragedBubble();
			
			new STask().run(st -> {
				// if the wither is dying, disintegrated,
				// the time has exceeded (10s) OR if frenzy
				// is activated during "stun", stop this loop
				if (wither.isDead() || dying || st.counter >= 10 * 20 || frenzyActivated) {
					st.kill();
					bubble.remove();
					// end of enrage, he will destroy one platform
					// only end enrage if he isnt in frenzy mode
					if (!dying && !frenzyActivated) this.endEnrage();
					return;
				}
				
				if (st.counter % 5 == 0) {
					bubble.teleport(Sputnik.setYawOf(
						wither.getLocation().add(0, WitherLordsHandle.NAME_OFFSET - 0.45, 0),
						bubble.getLocation().getYaw() + 55)
					);
				}
				st.counter++;
			}).startLoop(RunMode.SYNC, 0, 1);
			
			// stop tower charges only if the index tower is valid
			if (chargedPlatform > 0) {
				this.core.toggleTowerCharge(chargedPlatform);
				// reset the value to stop r/c
				this.chargedPlatform = -1;
			}
		}
		
		// the current activated (charged) platform
		int chargedPlatform = -1;
		public void chargePlatform() {
			// charge up a platform (continously striking it with lightning)
			List<Integer> platforms = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
			// dont strike ones that is already destroyed
			platforms.removeIf(i -> core.platformDestroyed[i]);
			
			if (platforms.size() == 0) {
				// if cant find an active platform, ignore
				this.chargedPlatform = -1;
				return;
			}
			
			// activate the tower corresponds to that platform
			this.chargedPlatform = SUtil.getRandom(platforms);
			core.toggleTowerCharge(this.chargedPlatform);
		}
		
		public void endEnrage() {
			// dialog
			this.say(FRENZY_BREAKS_PLATFORM, DIALOG_GAP);
			
			// destroy a platform
			List<Integer> platforms = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
			// dont destroy ones that is already destroyed
			platforms.removeIf(i -> core.platformDestroyed[i]);
			
			if (platforms.size() == 0) {
				// in case no platforms can be seen
				this.resumeHarassingPlayers();
				return;
			}
			
			int toDestroy = SUtil.getRandom(platforms);
			
			// launches 8 fireballs
			TimedActions actions = new TimedActions();
			for (int i = 0; i < 8; i++) {
				final int index = i;
				actions.add(() -> {
					Location platform = core.lords.necronTowers[toDestroy];
					// LOOK at the tower and launch fireballs at it
					this.teleportStrict(
						wither.getLocation().setDirection(platform
						.toVector().subtract(wither.getEyeLocation().toVector())
					));
					
					// shoot fireball (cosmetic)
					shootFireball(wither.getEyeLocation());
					
					// breaks the platform at the 5th fireball
					if (index == 5) core.dropPlatform(toDestroy);
				}).wait(10);
			}
			
			actions.wait(10).add(() -> {
				// resume attacking players
				this.resumeHarassingPlayers();
			}).run();
		}
		
		public void resumeHarassingPlayers() {
			this.frozen = false;
			this.stun = false; // his abilities can be used now
		}
		
		public void playFrenzyVisuals() {
			// particles
			for (double theta = 0; theta <= 2 * Math.PI; theta += Math.PI / 20d) {
				// play the fire dot effect
				Location fireDot = circleLoc(wither.getLocation(), explosionRadius, theta).add(0, 1, 0);
				wither.getWorld().spigot().playEffect(
					fireDot, Effect.FLAME, 0, 1, 
					0, 0, 0, 0, 0, 100
				);
			}
			// explosion
			playLargeExplosion(wither.getLocation(), explosionRadius);
		}
		
		public void attemptToReduceFrenzy() {
			if (!frenzyActivated) return; // if not during frenzy, dont do shit
			if (++hitCounter < 5) return; // every 3 hits reduce
			explosionRadius = Math.max(0, explosionRadius - 1);
			// reset the hit counter
			this.hitCounter = 0;
		}
		
		// this will be toggled to true after Necron drops user to lava pit
		boolean finishedPreFight = false;
		List<ArmorStand> hoveringPlayers = new ArrayList<>();
		
		// hover the players during intro dialog
		public void hoveringPlayer() {
			// spawn armorstands for all players
			int index = 0;
			// make sure the player locations are random
			Collections.shuffle(core.lords.necronHoverLocations);
			
			// for each player (including ghosts), spawn an armorstand
			for (DungeonUser du : core.lords.getHandle().getCurrentPlayersList()) {
				Player p = du.getInternal().toBukkitPlayer();
				Location hoverLocation = core.lords.necronHoverLocations.get(index);
				
				// spawn the base for the player to ride on
				ArmorStand base = p.getWorld().spawn(p.getLocation(), ArmorStand.class);
				base.setVisible(false);
				base.setGravity(false);
				
				// add to the handler map
				hoveringPlayers.add(base);
				
				// workaround for some goddamn reasons wtf??
				// without the delayed eject(), the player will not
				// mount properly (appears to be a client bug)
				base.setPassenger(p);
				SUtil.delay(() -> base.eject(), 1);
				
				// for the necron's arm animation, we first sample a few points and shove
				// them to an array for later uses
				List<Double> xPositions = new ArrayList<>(); // sampled points X
				List<Double> yPositions = new ArrayList<>(); // Y 
				List<Location> armParticlesLocations = new ArrayList<>(); // for particles
				
				// generate the arm using f(x) = necronsArmFunc(x) {0.5 <= x <= 37 | delta = 0.75}
				for (double x = 0.5; x <= 37; x += 0.75) {
					xPositions.add(x);
					// very shit and oddly specific parabolic function 
					yPositions.add(necronsArmFunc(x));
				}
				
				// basis vector
				Vector basis = wither.getLocation().toVector().subtract(hoverLocation.toVector()).normalize();
				for (int i = 0; i < xPositions.size(); i++) {
					// next position along arc
					Location l1 = hoverLocation.clone().add(0, -2, 0).add(basis.clone().multiply(xPositions.get(i)));
					l1.setY(l1.getY() + yPositions.get(i));
					
					// cache for replay later on
					armParticlesLocations.add(l1);
				}
				
				AtomicBoolean reached = new AtomicBoolean(false);
				// responsible for hovering the player & stops them from exiting
				new STask().run(s -> {
					if (base.isDead()) {
						s.kill();
						return;
					}
					
					// stop the player from exiting
					base.setPassenger(p);
					
					// movement tasks
					if (!reached.get() && base.getLocation().distanceSquared(hoverLocation) > 0.0625) {
						// move quickly to the hover location
						Vector dir = hoverLocation.toVector().subtract(base.getLocation().toVector()).normalize().multiply(0.5);
						
						Location newLoc = base.getLocation().add(dir);
						((CraftArmorStand)base).getHandle().setPositionRotation(newLoc.getX(), newLoc.getY(), newLoc.getZ(), 0, 0);
						
						return;
					}
					
					// to stop the code block above, once its there, dont keep moving
					reached.set(true);
					
					// if the base is already within hover location, move up and down (sin wave)
					double yoffset = Math.sin(s.counter / 3f) / 1.25;
					// tp up and down
					Location newLoc = Sputnik.setYTo(hoverLocation, hoverLocation.getY() + yoffset);
					((CraftArmorStand)base).getHandle().setPositionRotation(newLoc.getX(), newLoc.getY(), newLoc.getZ(), 0, 0);
					
					// animate the arm every 20 ticks
					if (s.counter % 20 == 0) {
						this.playParticlesForArms(armParticlesLocations);
					}
					
					// for animating the sine wave
					s.counter++;
				}).startLoop(RunMode.SYNC, 0, 1);
				
				index++;
			}
		}
		
		// play the smoke animation by giving it the cached arm locations
		public void playParticlesForArms(List<Location> arms) {
			new STask().run(s -> {
				if (s.counter >= arms.size()) {
					s.kill();
					return;
				}
				// play one of them at a time
				Location al = arms.get(s.counter);
				this.blobOfEffect(Effect.LARGE_SMOKE, al);
				// next frame
				s.counter++;
			}).startLoop(RunMode.ASYNC, 0, 0);
		}
		
		private void blobOfEffect(Effect effect, Location al) {
			for (int i = 0; i < 3; i++) {
				al.getWorld().spigot().playEffect(
					al, effect, 0,
					0, 0, 0, 0, 0, 0, 
					100
				);
			}
		}
		
		// https://www.desmos.com/calculator/fahbzzsogw
		private double necronsArmFunc(double x) {
			if (x >= 1/2 && x < 20) {
				// B1 in desmos
				return -Math.log(x) + 4;
			}
			// B2 in desmos
			if (x <= 37) return -Math.log(-x + 38) + 3.9;
			// error'ed
			return 0;
		}
		
		boolean fightStarted = false;
		// TODO: NECRON START | actually starting the fight
		public void startNecronPhase() {
			if (fightStarted) return;
			this.fightStarted = true;
			
			// player bobbin - chimmy
			// this also handles the arm animation too
			hoveringPlayer();
			
			// introduction dialog
			saySequencedDialog(WELCOME_DIALOG).add(() -> {
				say(null);
				// the boss is ready
				// drop all the players down by killing the armorstands that hold them
				hoveringPlayers.forEach(Entity::remove);
					
				// activate necron from his slumber
				this.frozen = false;
				this.resetAbilitiesCooldown();
				this.finishedPreFight = true;
				
				// select a target
				this.selectTarget();
				
				// hypixel random ass dialog, idk why they bother adding it
				SUtil.delay(() -> sayIfVacant(IMPRESSIVE_TRICK, DIALOG_GAP), 80);
			}).run();
			
			// throwing the fireballs
			// launches 8 fireballs
			TimedActions actions = new TimedActions().wait(30);
			for (int i = 0; i < 8; i++) {
				final int index = i;
				actions.add(() -> {
					Location platform = core.lords.necronEntrancePlatform.clone().add(0, -7, 0);
					// LOOK at the tower and launch fireballs at it
					this.teleportStrict(
						wither.getLocation().setDirection(platform
						.toVector().subtract(wither.getEyeLocation().toVector())
					));
					
					// shoot fireball (cosmetic)
					shootFireball(wither.getEyeLocation());
					
					// breaks the platform at the 5th fireball
					if (index == 5) core.dropPlatform(0);
				}).wait(10);
			}
			actions.run();
		}
		
		@Override
		public void onSpawn(LivingEntity entity, SEntity sEntity) {
			super.onSpawn(entity, sEntity);
			// the only thing to do during spawn is, check if the user entered core, if yes
			// trigger the actual fight
			new STask().run(s -> {
				for (DungeonUser du : core.lords.getHandle().getAlivePlayersList()) {
					// this checks if the user is on the core's floor or not
					if (du.getInternal().getLocation().getY() <= necronFloorY) {
						// start the fight
						this.startNecronPhase();
						s.kill();
						break;
					}
				}
			}).startLoop(RunMode.SYNC, 10, 15);
		}
		
		Queue<Double> damageThresholds = new LinkedList<>(Arrays.asList(0.8, 0.1)); // percentage of hp
		@Override
		public void onDamage(SEntity sEntity, Entity damager, EntityDamageByEntityEvent e, AtomicDouble damage) {
			wither.setTarget((LivingEntity)damager);
			
			if (!finishedPreFight || frenzyActivated || dying) {
				this.attemptToReduceFrenzy();
				// nullify damage
				damage.set(0);
				return;
			}
			
			double dmg = damage.get();
			// Damage threshold system ensures Necron requires at least 2 cycles to die
			if (damageThresholds.size() > 0) {
				// the idea is to progressively allow damage only if the remaining health is above certain limits
				if (wither.getHealth() - damage.get() <= (damageThresholds.peek() * wither.getMaxHealth())) {
					// first cycle: adjust damage to prevent health from dropping below the current threshold
					// if after applying damage the health would be lower than the threshold, 
					// we adjust the damage so the health equals the threshold exactly
					dmg = Math.max(0, wither.getHealth() - (damageThresholds.peek() * wither.getMaxHealth()));
					damage.set(dmg);
					
					// Once the first threshold is reached, remove it to progress to the next phase
					// the next threshold will be lower, eventually allowing normal damage to occur
					this.onHealthReachesThreshold(this.damageThresholds.poll());
				}
			}
			
			// prevent him from evaporating
			// if the HP AFTER DAMAGED is below 5, trigger death sequence
			if (wither.getHealth() - dmg <= 5 && !dying) {
				// prevent player from damaging it
				damage.set(wither.getHealth() - 5);
				this.dying = true;
				
				// stop the wither immediately
				this.stopMovingToLocation();
				// stop all abilities
				this.resetAbilities();
				
				// turn off wither shield & freeze the thing
				this.setShield(false);
				this.frozen = true;
				
				// dialog for necron's death
				saySequencedDialog(DEATH)
				.wait(40).add(() -> {
					say(null);
					setHealth(0); // kill the wither
					// pops tnt out // TODO
				}).run();
			}
		}
		
		@Override
		public double getEntityMaxHealth() {
			return 1_100_000_000;
		}

		@Override
		public double getDamageDealt() {
			return 30_000;
		}

		@Override
		public double getXPDropped() {
			return 0;
		}

		@Override
		public void onTickAsync() {}
		
		public Location circleLoc(Location center, double radius, double angleInRadian) {
			double x = center.getX() + radius * Math.cos(angleInRadian);
			double z = center.getZ() + radius * Math.sin(angleInRadian);
			double y = center.getY();
			
			Location loc = new Location(center.getWorld(), x, y, z);
			Vector difference = center.toVector().clone().subtract(loc.toVector());
			loc.setDirection(difference);
			
			return loc;
		}
	}
}
