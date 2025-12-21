package vn.giakhanhvn.skysim.dungeons.bosses.witherlords;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import com.google.common.util.concurrent.AtomicDouble;

import lombok.var;
import net.minecraft.server.v1_8_R3.WorldServer;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.WitherLordsHandle.WitherLordPhase;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.animations.Phase3DropAnimation;
import vn.giakhanhvn.skysim.dungeons.systems.Dungeon;
import vn.giakhanhvn.skysim.entity.SEntity;
import vn.giakhanhvn.skysim.entity.SEntityType;
import vn.giakhanhvn.skysim.user.User;
import vn.giakhanhvn.skysim.util.BlockFallAPI;
import vn.giakhanhvn.skysim.util.STask;
import vn.giakhanhvn.skysim.util.SUtil;
import vn.giakhanhvn.skysim.util.Sputnik;
import vn.giakhanhvn.skysim.util.TimedActions;
import vn.giakhanhvn.skysim.util.STask.RunMode;

public class StormPhase implements WitherLordPhase {
	// constants
	static final int PILLAR_MIN_HEIGHT = 3; // the minimal length of it (because in hypixel they only go up with a 8 block gap above)
	static final int PILLAR_MAX_HEIGHT = 23; // the actual length of the crusher (from 5 block to the ground)
	
	static final int PILLAR_GAP = PILLAR_MAX_HEIGHT - 1; // the gap is the amount of blocks you need to subtract so that the crusher can reach the floor, in this case, 23 - 1 = 22
	static final int PILLAR_SPEED = 4; // the unit of movement (4 ticks per move)
	
	static final int CRUSHER_GRACE_PERIOD = Sputnik.core().getDungeonPreferences().isDebug() ? 80 : 30; // REAL hypixel's default is 30 ticks
	
	static final int GREEN_CRUSHER = 0; // indicies
	static final int YELLOW_CRUSHER = 1;
	static final int PURPLE_CRUSHER = 2;
	
	// storm speed
	static final double STORM_VECTOR_SPEED = 0.32d;
	
	// handlers
	public WitherLordsHandle lords;
	public Wither storm;
	private World world;
	List<BlockSimple> crusherBase;
	
	// flags for systems
	public boolean[] crusherMovesUp = new boolean[3];
	public boolean[] softLock = new boolean[3];
	public boolean[] crusherDestroyed = new boolean[3];
	public boolean[] crusherCrushable = new boolean[3];
	
	// internal tasks
	public BukkitTask[] crusherReleaseTasks = new BukkitTask[3];
	
	// the current length of crushers
	public AtomicInteger greenLength = new AtomicInteger(0);
	public AtomicInteger yellowLength = new AtomicInteger(0);
	public AtomicInteger purpleLength = new AtomicInteger(0);
	
	// center of the arena, calculated by .mid()
	Location centerArena;
	
	Phase3DropAnimation goldorDrop;
	
	public StormPhase(WitherLordsHandle handle) {
		this.lords = handle;
		this.world = lords.getHandle().getWorld();
		this.initializeCrushersLength();
		// calc
		this.centerArena = lords.stormCirclePath[0].mid(lords.stormCirclePath[2]);
		// initiate goldor drop animation
		// handler
		goldorDrop = new Phase3DropAnimation(lords.goldorDropper);
	}
	
	// run on phase tick (the global tick)
	public void onPhaseTick(int counter) {
		if (counter % PILLAR_SPEED == 0) onPadTick();
	}
	
	public void onPadTick() {
		// tick individual pads
		padTicking(GREEN_CRUSHER, lords.greenPad, greenLength);
		padTicking(YELLOW_CRUSHER, lords.yellowPad, yellowLength);
		padTicking(PURPLE_CRUSHER, lords.purplePad, purpleLength);
	}
	
	public void padTicking(int index, Location origin, AtomicInteger targetStorage) {
		if (crusherDestroyed[index]) return; 
		
		var pad = world.getNearbyEntities(origin, 3, 5, 3);
		this.filterPadPlayers(pad);
		
		// if there's people in the pad and isn't in softlock period
		if (pad.size() > 0 && !softLock[index]) {
			// do shit
			boolean state = crusherMovesUp[index];
			if (index == GREEN_CRUSHER) greenCrusherActive(state);
			else if (index == YELLOW_CRUSHER) yellowCrusherActive(state);
			else if (index == PURPLE_CRUSHER) purpleCrusherActive(state);
			
			// if the crusher is moving down and "can crush storm" status
			// is false, set it to true and then set it to false 1s later
			if (!crusherMovesUp[index] && !crusherCrushable[index]) {
				crusherCrushable[index] = true;
				//Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "[DEBUG] Crusher Status CAN CRUSH: Moving down");
				
				// make sure no overlap
				if (crusherReleaseTasks[index] != null) {
					crusherReleaseTasks[index].cancel();
				}
				
				// 1s later restore
				this.crusherReleaseTasks[index] = SUtil.delay(() -> {
					crusherCrushable[index] = false;
					// clear task
					crusherReleaseTasks[index] = null;
					//Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "[DEBUG] Crusher Status CANNOT CRUSH: Grace Timeout!");
				}, CRUSHER_GRACE_PERIOD); 
				// the period that the players can still crush storm afer the pad is released
			}
			// the crusher cannot crush if its moving upwards
			else if (crusherMovesUp[index] && crusherCrushable[index]) {
				crusherCrushable[index] = false;
				// make sure no overlap
				if (crusherReleaseTasks[index] != null) {
					crusherReleaseTasks[index].cancel();
					// release the task if theres any
					crusherReleaseTasks[index] = null;
				}
				//Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "[DEBUG] Crusher Status CANNOT CRUSH: Moving up!");
			}
			
			// once reached critical heights (0 or 23)
			// softlock the pad until 1s later
			if (targetStorage.get() >= PILLAR_MAX_HEIGHT || targetStorage.get() <= PILLAR_MIN_HEIGHT) {
				crusherMovesUp[index] = targetStorage.get() >= PILLAR_MAX_HEIGHT;
				// softlock for 1 second before continuing
				softLock[index] = true;
				// after 1s, release softlock
				SUtil.delay(() -> {
					softLock[index] = false;
				}, 25);
			}
		}
	}
	
	// basic mapping from index to location
	public Location getPillarOriginFromIndex(int index) {
		if (index == GREEN_CRUSHER) return lords.greenCrusher;
		else if (index == YELLOW_CRUSHER) return lords.yellowCrusher;
		else if (index == PURPLE_CRUSHER) return lords.purpleCrusher;
		return null;
	}
	
	// see who's standing on the pad
	private void filterPadPlayers(Collection<Entity> pad) {
		pad.removeIf(p -> {
			if (!(p instanceof Player)) return true;
			if (!p.isOnGround()) return true; 
			User u = User.getUser((Player) p);
			if (u.getDungeon() == null || u.isAGhost()) return true;
			return false;
		});
	}
	
	// initialize the crusher utils
	public void initializeCrushersLength() {
		int targetLength = 22 - 5; // length = the entire length - 5 (base, cant be changed)
		
		// set each individually
		setLengthOfCrusher(lords.greenCrusher, targetLength);
		greenLength.set(targetLength);
		
		setLengthOfCrusher(lords.yellowCrusher, targetLength);
		yellowLength.set(targetLength);
		
		setLengthOfCrusher(lords.purpleCrusher, targetLength);
		purpleLength.set(targetLength);
	}
	
	@Override
	public void start() {
		this.lords.bossbar.setTitle("&c&lStorm");
		
		// spawn wither skeletons
		this.lords.spawnPhaseWitherSkeletons(1); // phase 1
		
		// spawn storm
		storm = (Wither)new SEntity(lords.stormSpawnPoint, SEntityType.STORM_TECH_DEMO,
			Sputnik.getNMSWorld(lords.stormSpawnPoint), this
		).getEntity();
		
		// the core task loop
		new STask().run(s -> {
			// if the current phase isnt storm, stop the global ticker
			if (!(lords.currentPhase instanceof StormPhase)) {
				s.kill();
				return;
			}
			
			if (storm.isDead()) {
				this.end();
				s.kill();
				return;
			}
			
			onPhaseTick(s.counter);
			s.counter++;
		}).startLoop(RunMode.SYNC, 0, 0);
	}
	
	// on phase end
	@Override
	public void end() {
		if (lords.getHandle().isEnded()) return;
		// clear
		// advance to goldor phase
		lords.currentPhase = new GoldorPhase(lords);
		lords.currentPhase.start();
		// drop
		this.goldorDrop.dropPillar();
	}
	
	// either move up or down respective crushers
	public void greenCrusherActive(boolean up) {
		int targetLength = Math.min(PILLAR_MAX_HEIGHT, Math.max(PILLAR_MIN_HEIGHT, greenLength.get() + (up ? -1 : 1)));
		setLengthOfCrusher(lords.greenCrusher, targetLength);
		greenLength.set(targetLength);
	}
	
	public void yellowCrusherActive(boolean up) {
		int targetLength = Math.min(PILLAR_MAX_HEIGHT, Math.max(PILLAR_MIN_HEIGHT, yellowLength.get() + (up ? -1 : 1)));
		setLengthOfCrusher(lords.yellowCrusher, targetLength);
		yellowLength.set(targetLength);
	}
	
	public void purpleCrusherActive(boolean up) {
		int targetLength = Math.min(PILLAR_MAX_HEIGHT, Math.max(PILLAR_MIN_HEIGHT, purpleLength.get() + (up ? -1 : 1)));
		setLengthOfCrusher(lords.purpleCrusher, targetLength);
		purpleLength.set(targetLength);
	}
	
	// set the crusher length
	private void setLengthOfCrusher(Location origin, int length) {
		int counter = 0;
		for (int y = origin.getBlockY(); y >= origin.getBlockY() - PILLAR_GAP; y--) {
			Block toCheck = world.getBlockAt(origin.getBlockX(), y, origin.getBlockZ());
			Location tcl = toCheck.getLocation();
			boolean hasBlock = toCheck.getType() != Material.AIR;
			
			// count
			if (hasBlock) {
				counter++;
			}
			
			// if the current crusher is shorter than expected, create
			// more layers
			if (counter < length && !hasBlock) {
				// push entities down
				world.getNearbyEntities(tcl, 3, 2, 3).forEach(e -> {
					if (!(e instanceof LivingEntity)) return;
					LivingEntity entity = (LivingEntity) e;
					Location relative = entity.getEyeLocation().getBlock().getLocation();
					// if entity has a block above, assume that it's currently inside
					if (relative.add(0, 1, 0).getBlock().getType() != Material.AIR) {
						Location newLoc = entity.getLocation();
						newLoc.add(0, -1, 0);
						// only shift entites down IF the new position doesn't clip
						// through the floor
						if (newLoc.getBlockY() >= origin.getBlockY() - PILLAR_GAP) {
							entity.teleport(newLoc);
						}
					}
				});
				
				// copy the block above it
				cuboid(getPos1Of(tcl), getPos2Of(tcl)).forEach(b -> {
					Block above = b.getRelative(BlockFace.UP);
					b.setType(above.getType());
					b.setData(above.getData());
				});
				
				counter++;
				continue;
			}
			
			// delete if more than needed
			if (counter > length && hasBlock) {
				cuboid(getPos1Of(tcl), getPos2Of(tcl)).forEach(b -> b.setType(Material.AIR));
			}
		}
	}
	
	// get the 1st bound of a crusher's slice
	private Location getPos1Of(Location origin) {
		return origin.clone().add(3, 0, 3);
	}
	
	// get the 2nd bound of a crusher's slice
	private Location getPos2Of(Location origin) {
		return origin.clone().add(-3, 0, -3);
	}
	
	// get the 1st bound of a crusher's slice
	private List<Block> cuboid(Location ab, Location bc) {
		return Sputnik.cuboid(ab, bc);
	}
	
	public double distanceFrom(int crusher, Location tloc) {
		Location loc = getPillarOriginFromIndex(crusher);
		return Sputnik.setYTo(loc, 0).distance(Sputnik.setYTo(tloc, 0));
	}
	
	/**
	 * Represents a simple block consists of Material and data
	 * @author GiaKhanhVN
	 */
	class BlockSimple {
		Material material;
		byte data;
		
		public BlockSimple(Material m, byte b) {
			this.material = m;
			this.data = b;
		}
	}
	
	/**
	 * The wither--Storm itself
	 * @author GiaKhanhVN
	 */
	public static class StormWither extends CustomWitherEntity {
		// the reference to outside systems
		private StormPhase core;
		
		// flags
		public double damageThreshold; // for damage/hit control
		public boolean dying; // if the boss is dying
		public boolean doneInitialFlight; // if the boss is flying (introduction phase)
		
		// misc
		public int stormFloorY;
		
		// construct
		public StormWither(WorldServer world, StormPhase storm) {
			super(world, 1);
			this.core = storm;
			this.damageThreshold = getEntityMaxHealth() / 2;
			this.setShield(true);
			this.entityOwner = core.lords.getHandle();
			this.hoverHeight = 1.65f;
			this.frozen = true;
			this.DIALOG_GAP = 50;
			// get the floor Y
			this.stormFloorY = core.lords.goldorDropper.getBlockY();
		}
		
		// later
		@Override
		public String getEntityName() {
			return this.witherName();
		}
		
		@Override
		public String witherName() {
			return "Storm";
		}
		
		// dialogs
		static String[] WELCOME_DIALOG = new String[]{
			"Pathetic Maxor, just like expected.",
			"Don't boast about beating this simple minded Wither.",
			"My abilities are unparalleled, in many ways I am the last bastion.",
			"The memory of your death will be your fondest, focus up!",
			"The power of lightning is quite phenomenal. A single strike can vapourise a person whole.",
			"I'd be happy to show you what that's like!"
		};
		
		static String[] HIT_BY_CRUSHER = new String[]{
			"Ouch, that hurt!",
			"Oof"
		};
		
		static String[] CRUSHER_EXPLODED = new String[]{
			"THAT WAS ONLY IN MY WAY!",
			"Slowing me down will be your greatest accomplishment!",
			"This factory is too small for me!",
			"BEGONE PILLAR!"
		};
		
		static String[] LAUGH_AT_PLAYER = new String[]{
			"Bahahaha! Not a single intact pillar remains!",
			"Rejoice, your last moments are with me and my lightning."
		};
		
		static String[] GIGA_LIGHTNING = new String[]{
			"ENERGY HEED MY CALL!",
			"THUNDER LET ME BE YOUR CATALYST!"
		};
		
		static String FAIL_TO_TAKE_COVER = "Fool, I'd hide under something next time if I were you!";
		
		static String[] DEATH = new String[]{
			"I should have known that I stood no chance.",
			"At least my son died by your hands."
		};
		
		static String[] RANDOM_MESSAGES = new String[]{
			"The Age of Men is over, we are creating tens, hundreds of withers!!",
			"Not just your land, but every kingdom will soon be ruled by our army of undead!",
			"No more adventurers, no more heroes, death and thunder!",
			"The days are numbered until I am finally unleashed again on the world!"
		};
		
		@Override
		public void onSpawn(LivingEntity entity, SEntity sEntity) {
			super.onSpawn(entity, sEntity);
			// welcome dialog
			TimedActions welcome = saySequencedDialog(WELCOME_DIALOG);
			welcome.add(() -> {
				say(null);
			});
			
			welcome.run();
			
			this.frozen = true;
			WitherLordsHandle handle = core.lords;
			
			// he will slowly move to the first point
			moveToLocation(handle.stormCirclePath[0], STORM_VECTOR_SPEED, () -> {
				// after moving to the second point
				moveToLocation(handle.stormCirclePath[1], STORM_VECTOR_SPEED, () -> {
					// move to the third point
					moveToLocation(handle.stormCirclePath[2], STORM_VECTOR_SPEED, () -> {
						// move to his spawnpoint and activate giga lightning
						moveToLocation(handle.stormSpawnPoint, STORM_VECTOR_SPEED, () -> {
							frozen = false;
							doneInitialFlight = true;
							// boom
							startGigaLightning();
						});
					});
				});
			});
		}
		
		/** This is the task that will "end the boss's stun" after a set period of time **/
		public BukkitTask endStunTask = null;
		// the stun flag
		public boolean stun;
		public boolean damageable;
		
		public void onStun(int pillarIndex) {
			this.stun = true;
			this.damageable = false; // make sure the boss isnt damageable while it stucks inside a crusher
			this.frozen = true; // he wont be able to move
			this.setShield(false);
			
			// cosmetic
			wither.damage(0.0001D);
			say(HIT_BY_CRUSHER[SUtil.random(0, HIT_BY_CRUSHER.length - 1)]);
			
			// spawn the enraged animation
			ArmorStand bubble = spawnEnragedBubble();
			
			new STask().run(st -> {
				if (wither.isDead() || !stun || dying) {
					st.kill();
					bubble.remove();
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
			
			// destroy the pillar
			new TimedActions().wait(40).add(() -> {
				// say the dialog
				say(CRUSHER_EXPLODED[SUtil.random(0, CRUSHER_EXPLODED.length - 1)]);
				// play the animation
				explodePillar(pillarIndex);
				
				// after 15s, resume attacking
				this.endStunTask = SUtil.delay(() -> {
					if (!dying && stun) {
						endStun();
					}
				}, 20 * 15);
				
				// allow players to damage the boss
				this.damageable = true;
			}).wait(60).add(() -> {
				say(null);
			}).run();
		}
		
		public void explodePillar(int pillarIndex) {
			List<Block> blocksToBeDestroyed = new ArrayList<>();
			List<Block> blocksToBeAnimated = new ArrayList<>();
			
			// upper cuboid, eliminate random polished diorite blocks
			Location upperCuboidUp = wither.getLocation().add(8, 8, 8);
			Location upperCuboidDown = wither.getLocation().add(-8, 6, -8);
			
			// explodes the thingy
			core.cuboid(
				upperCuboidUp,
				upperCuboidDown
			).forEach(b -> {
				Material material = b.getType();
				byte data = b.getData();
				
				// only break "polished diorite"
				if (material == Material.STONE && data == 4) {
					// create a rough texture
					if (SUtil.random(0, 1) != 0) {
						blocksToBeDestroyed.add(b);
					}
				}
			});
			
			// remove polished diorites in the middle part entirely
			// to stop the blocks from blocking players' attacks
			Location middleCuboidUp = wither.getLocation().add(8, 6, 8);
			Location middleCuboidDown = wither.getLocation().add(-8, 2, -8);
			
			// remove ALL 
			core.cuboid(
				middleCuboidUp,
				middleCuboidDown
			).forEach(b -> {
				Material material = b.getType();
				byte data = b.getData();
				
				// only "polished diorite"
				if (material == Material.STONE && data == 4) {
					blocksToBeDestroyed.add(b);
					blocksToBeAnimated.add(b);
				}
			});
			
			// last bound, destroy everything from the wither
			// to the ground to stop debris everywhere
			Location lowerBound = wither.getLocation().add(-8, 0, -8);
			// this is the floor Y of the arena
			lowerBound.setY(this.stormFloorY);
			
			core.cuboid(
				wither.getLocation().add(8, 3, 8),
				lowerBound
			).forEach(b -> {
				Material material = b.getType();
				byte data = b.getData();
				
				// every type of blocks can be destroyed
				// including polished diorite AND diorite
				if (material == Material.STONE && (data == 3 || data == 4)) {
					blocksToBeDestroyed.add(b);
					blocksToBeAnimated.add(b);
				}
			});
			
			// blocks spew everywhere
			blocksToBeAnimated.forEach(b -> {
				// the flying animation
				Location matchPos = Sputnik.getBlockMatchPos(b.getLocation());
				
				// the blocks flying around
				if (SUtil.random(0, 2) == 0 && core.distanceFrom(pillarIndex, matchPos) >= 2) {
					// the block will fly backwards
					BlockFallAPI.sendVelocityBlock(
						matchPos, b.getType(), b.getData(), matchPos.getWorld(), 20, 
						b.getLocation().toVector().subtract(wither.getLocation().toVector())
						.normalize().setY(0.5).multiply(0.76)
					);
				}
			});
			
			// destroy the blocks
			blocksToBeDestroyed.forEach(b -> {
				// the break animation, DONT break everything
				// it will be chaotic if we do
				if (SUtil.random(0, 2) == 0) {
					// oh boy, here we go again, java boilerplates
					b.getWorld().playEffect(
						Sputnik.getBlockMatchPos(b.getLocation()), 
						Effect.STEP_SOUND, b.getType(), 10
					);
				}
				// actually removing the block
				b.setType(Material.AIR);
			});
		}
		
		public void endStun() {
			this.stun = false;
			this.damageable = false; // same thing
			this.frozen = false;
			this.setShield(true);
			
			// cancel the previously issued endStun task
			if (endStunTask != null) {
				endStunTask.cancel();
				endStunTask = null;
			}
			
			// call the enrage ability
			this.enrage();
			
			// if there's no pillars left, laugh at the player, fight is softlocked
			int pillarsLeft = 0;
			for (boolean b : core.crusherDestroyed) {
				if (!b) pillarsLeft++;
			}
			
			if (pillarsLeft <= 0) {
				// the fight is entirely softlocked now
				saySequencedDialog(LAUGH_AT_PLAYER).add(() -> {
					say(null);
				}).run();
			}
		}
		
		
		// counter (ticks)
		private int counter;
		// flag for gigalightnng activation
		private boolean gigaLightning;
		
		// fireball attack
		static final int FIREBALL_ATTACK_COOLDOWN = 20 * 70;  // 70s duh
		private boolean fireballAttack = false; // state flag
		private int fireBallAttackCooldown = FIREBALL_ATTACK_COOLDOWN;
		
		@Override
		public void t_() {
			super.t_();
			
			// synchronization
			this.hologramEntity.teleport(wither.getLocation().add(0, WitherLordsHandle.NAME_OFFSET, 0));
			this.dialougeEntity.teleport(wither.getLocation().add(0, WitherLordsHandle.DIABOX_OFFSET, 0));
			
			// sync the bossbar
			core.lords.bossbar.setProgress(wither.getHealth() / wither.getMaxHealth());
			counter++;
			
			// dying animation
			if (dying && counter % 3 == 0) {
				wither.damage(0.0001D);
				// strike lightning everywhere (cosmetic)
				Location strikeLocation = wither.getLocation().add(SUtil.randInt(-6, 6), 0, SUtil.randInt(-6, 6));
				// make the wither spin
				this.teleportStrict(Sputnik.setYawOf(
					wither.getLocation(), wither.getLocation().getYaw() + 55
				));
				// strike thne ground around
				SUtil.lightningLater(Sputnik.setYTo(strikeLocation, stormFloorY - 1), true, 0);
			}
			
			// only execute abilities and dialog if the boss isnt stunned or in "dying animation"
			// state
			if (stun || dying) return;
			
			// attack cooldown
			if (fireBallAttackCooldown > 0) fireBallAttackCooldown--;
			
			// abilities -- fireball (rare)
			if (!gigaLightning && SUtil.random(0, 8) == 0) {
				fireballAttack();
			}
			
			// spawn wither skeletons (uncommon)
			if (doneInitialFlight && !gigaLightning && !fireballAttack && !dying && !stun && counter % 300 == 0) {
				arcSpawnMobs();
			}
			
			// gigalightning (very rare/event driven)
			if (!gigaLightning && !fireballAttack && counter % 1200 == 0) {
				startGigaLightning();
			}
			
			// static field (occasional)
			if (!fireballAttack && !gigaLightning && counter % 160 == 0) {
				staticField();
			}
			
			// this applies only for the beginning (introduction flight)
			if (!doneInitialFlight && counter % 30 == 0) {
				stormShootFireball();
			}
			
			// default attack (fire skull every 10-15 ticks)
			if (!fireballAttack && !gigaLightning && !frozen && wither.getTarget() != null) {
				// fire every 10-15 ticks
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
			
			// say random message
			if (counter % SUtil.random(110, 260) == 0 // every 5.5s to 13s
			 && this.currentDialog == null // only if there's no current dialog
			 && !gigaLightning /* Storm specific: only say random if not during giganigga */
			) {
				this.say(SUtil.randArr(RANDOM_MESSAGES), DIALOG_GAP);
			}
		}
		
		// fireball attack the players
		public void fireballAttack() {
			if (!fireballAttack && fireBallAttackCooldown <= 0) {
				// flags
				fireballAttack = true;
				fireBallAttackCooldown = FIREBALL_ATTACK_COOLDOWN;
				
				boolean stateBefore = this.frozen;
				this.frozen = true;
				
				// move to the center and then shoot fireballs for 8-16 times
				moveToLocation(core.centerArena, STORM_VECTOR_SPEED * 2.5, () -> {
					TimedActions ta = new TimedActions();
					for (int i = 0; i < SUtil.randInt(8, 16); i++) {
						ta.add(() -> {
							if (stun || dying) return;
							stormShootFireball();
						}).wait(15);
					}
					ta.add(() -> {
						// after complete, resume
						fireballAttack = false;
						this.frozen = stateBefore;
					});
					ta.run();
				});
			}
		}
		
		boolean firstGigaLightning = true;
		// GIGA LIGHTNING METHODS
		public void startGigaLightning() {
			boolean stateBefore = this.frozen;
			
			// set state flag
			this.frozen = true;
			this.gigaLightning = true;
			
			// according to chimmy, first gigalightning will happen in his spawn point,
			// not in the middle
			Location gigaLocation = firstGigaLightning ? core.lords.stormSpawnPoint : core.centerArena;
			// countdown (will be 5s if this gl is the FIRST or the current floor is B2+, otherwise 9s)
			int glCountdown = firstGigaLightning ? 5 : 9; // TODO add difficulty
			
			// not the first anymore
			if (firstGigaLightning) this.firstGigaLightning = false;
			
			// move to the desired location and then strike
			moveToLocation(gigaLocation, STORM_VECTOR_SPEED * 2, () -> {
				new STask().run(s -> {
					if (wither.isDead() || dying) {
						s.kill();
						this.frozen = stateBefore;
						this.gigaLightning = false;
						return;
					}
					// when times up
					if (glCountdown - (s.counter / 20) <= 0) {
						// dialog
						say(GIGA_LIGHTNING[SUtil.random(0, GIGA_LIGHTNING.length - 1)], DIALOG_GAP);
						
						// handle
						Dungeon dungeon = core.lords.getHandle();
						
						// reset the states
						s.kill();
						this.gigaLightning = false;
						
						this.frozen = stateBefore;
						// clear subtitles (for sure)
						dungeon.sendTitleWithSubtitle("", "", 0, 10, 0);
						
						// stats
						int deathsBefore = dungeon.deaths;
						
						// strikes
						strikeIntactPillars();
						
						// strike players
						new TimedActions().add(() -> strikeAndKillPlayers()).wait(20).add(() -> {
							// cosmetic effects
							strikeIntactPillars();
							// do the actual killing
							strikeAndKillPlayers();
						}).add(() -> {
							// if one of them has died after the strike
							if (dungeon.deaths > deathsBefore) {
								say(FAIL_TO_TAKE_COVER, DIALOG_GAP);
							}
						}).run();
						return;
					}
					// count down
					if (s.counter % 20 == 0) {
						core.lords.getHandle().sendTitleWithSubtitle(
							"&4" + (glCountdown - (s.counter / 20)), "", 
						0, 50, 0);
					}
					s.counter++;
				}).startLoop(RunMode.SYNC, 0, 0);
			});
		}
		
		public void strikeIntactPillars() {
			for (int i = GREEN_CRUSHER; i <= PURPLE_CRUSHER; i++) {
				if (core.crusherDestroyed[i]) continue;
				// strike the shit out of it by shifting the thing down
				Location origin = core.getPillarOriginFromIndex(i).clone();
				// shift down by 22 blocks
				origin.setY(origin.getY() - StormPhase.PILLAR_GAP);
				
				// strike the circumferences of the pillars
				circumCircle(origin, 5).forEach(b -> {
					Location strike = b.getLocation().add(0.5, 0, 0.5);
					// strike the lightning
					strikeLightning(strike, false);
				});
			}
		}
		public void strikeAndKillPlayers() {
			Dungeon dungeon = core.lords.getHandle();
			// loop through all players
			dungeon.getAlivePlayersList().forEach(du -> {
				boolean killed = true;
				// if the player is not hiding, explode
				// loop through all crushers and check for each of them
				// predicates below
				for (int i = GREEN_CRUSHER; i <= PURPLE_CRUSHER; i++) {
					// if the crusher is destroyed,ignore it. Destroyed pillars
					// wont protect you from Storm's giganigga
					if (core.crusherDestroyed[i]) continue;
					// if the distance from a pillar is smaller or equals 3 (it means that the 
					// player is standing underneath it)
					if (core.distanceFrom(i, du.getInternal().getLocation()) <= 3) {
						// the player wont be killed anymore, yey
						killed = false;
						break;
					}
				}
				// if the player is not hiding under any pillars and
				// supposed to be nuked, do it
				if (killed) {
					User user = du.getInternal();
					// strike lightning for dramatic effect, duh
					strikeLightning(du.getInternal().getLocation(), false);
					strikeLightningsConsecutively(user.getLocation(), 4);
					// damage for 85% of the players' hp
					double absorbedDmg = user.toBukkitPlayer().getMaxHealth() * 0.85;
					damagePlayer(
						user, "Giga Lightning", 
						absorbedDmg, true // true dmg
					);
				}
			});
		}
		// END OF GIGA LIGHTNING
		
		// AOE damage
		public void stormFrenzy() {
			new STask().run(st -> {
				if (st.counter >= 7 * 20 || wither.isDead() || stun) {
					st.kill();
					return;
				}
				if (st.counter % 6 == 0) {
					// big explosion particle
					playLargeExplosion(wither.getLocation());
					
					if (!dying) Sputnik.hittablePlayers(wither.getLocation(), 5, 5, 5).forEach(p -> {
						User user = User.getUser(p);
						if (user == null) return;
						damagePlayer(
							user, "Frenzy",
							getDamageDealt() * 2, false
						);
					});
				}
				st.counter++;
			}).startLoop(RunMode.SYNC, 1, 1);
		}
		
		@Override
		public void enrage() {
			super.enrage();
			// activate shadow wave
			this.stormFrenzy();
		}
		
		@Override
		public void onHitAWall() {
			if (stun || dying || gigaLightning || fireballAttack) return;
			// test for the green
			testForStuck(GREEN_CRUSHER);
			testForStuck(YELLOW_CRUSHER);
			testForStuck(PURPLE_CRUSHER);
		}
		
		// check if the boss is currently stuck in a given pillar
		public boolean testForStuck(int pillar) {
			// if the pillar state is "pushing down"
			// and the boss distance is smaller or equals 2.5 (it means the boss is inside it)			
			if (!core.crusherDestroyed[pillar] &&  // not destroyed yet
				core.crusherCrushable[pillar] && // pillar can crush storm
				core.distanceFrom(pillar, this.wither.getLocation()) <= 2.5 // in the pillar
			) {
				// ADDITION: The block on the boss's head (wither.getLocation's Y + 1.5) must be non-solid
				if (!wither.getLocation().add(0, 1.5, 0).getBlock().getType().isSolid()) {
					return false;
				}
				core.crusherDestroyed[pillar] = true; // destroy the pillar
				onStun(pillar); // boss stunned
				return true;
			}
			return false;
		}
		
		@Override
		public void onTickAsync() {}
		
		// create a circle (circumference only) around a location
		// this thing is used for the lightning thing
		public List<Block> circumCircle(Location loc, int r) {
			List<Block> bl = new ArrayList<Block>();
			int cx = loc.getBlockX();
			int cy = loc.getBlockY();
			int cz = loc.getBlockZ();
			org.bukkit.World w = loc.getWorld();
			
			int rSquared = r * r;
			for (int x = cx - r; x <= cx + r; x++) {
				for (int z = cz - r; z <= cz + r; z++) {
					if ((cx - x) * (cx - x) + (cz - z) * (cz - z) >= rSquared) {
						Location l = new Location(w, x, cy, z);
						bl.add(l.getBlock());
					}
				}
			}
			return bl;
		}
		
		@Override
		public void onDamage(SEntity sEntity, Entity damager, EntityDamageByEntityEvent e, AtomicDouble damage) {
			wither.setTarget((LivingEntity)damager);
			
			// this is the same as Maxor's
			// nullify the damage if either NOT stunned or DYING
			if(!damageable || dying) {
				damage.set(0);
				return;
			}
			
			double dmg = damage.get();
			boolean reachedThreshold = false;
			
			// damage threshold system, STORM requires at least 2 pillars exploded to die
			if (wither.getHealth() - damage.get() <= this.damageThreshold) {
				// the first cycle is calculated by getting the leftover after damage, if leftover
				// is smaller than the allowed threshold, set the damage accordingly so that
				// the leftover is == the threshold
				dmg = Math.max(0, wither.getHealth() - 1 - this.damageThreshold);
				// the -1 here is to ensure the boss stays at 1 hp even after being killed in order 
				// to play the death animation
				damage.set(dmg);
				
				// after first threshold is broken, dmg threshold is lowered to 0 to allow any sort of damage
				this.damageThreshold = 0;
				reachedThreshold = true;
			}
			
			// if the HP is below 5, trigger death sequence
			if (wither.getHealth() - dmg <= 5 && !dying) {
				// prevent player from damaging it and
				// storm will spin furiously when he's marked as dying
				this.dying = true;
				// froze it in place
				this.frozen = true;
				// turn off wither shield
				this.setShield(false);
				
				// dialog for storm
				saySequencedDialog(DEATH)
				
				// trigger phase 3 by killing Storm
				.add(() -> {
					say(null);
					setHealth(0); // this will call end() consequently
					// remove all wither skeletons
					core.lords.spawnedSkeletons.forEach(w -> {
						w.damage(1);
						w.setHealth(0);
					});
				})
				
				.run();
				return;
			}
			
			// if the player exceeded damage threshold, end stun right now
			if (reachedThreshold && stun) {
				endStun();
			}
		}
		
		@Override
		public void onDeath(SEntity sEntity, Entity killed, Entity damager) {
			super.onDeath(sEntity, killed, damager);
		}
		
		@Override
		public double getEntityMaxHealth() {
			return GameplaySystem.getStormHealth(core.lords.getHandle().getDifficulty());
		}
	
		@Override
		public double getDamageDealt() {
			return GameplaySystem.getStormDPS(core.lords.getHandle().getDifficulty());
		}
		
		@Override
		public double getBossDefensePercentage() {
			return GameplaySystem.getStormDefense(core.lords.getHandle().getDifficulty());
		}
		
		@Override
		public double getXPDropped() {
			return 0;
		}
	}
}
