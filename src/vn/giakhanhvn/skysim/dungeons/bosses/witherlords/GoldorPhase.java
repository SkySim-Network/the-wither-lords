package vn.giakhanhvn.skysim.dungeons.bosses.witherlords;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftWither;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.google.common.util.concurrent.AtomicDouble;

import net.minecraft.server.v1_8_R3.WorldServer;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.WitherLordsHandle.WitherLordPhase;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.animations.NecronTrapdoorAnimation;
import vn.giakhanhvn.skysim.dungeons.systems.DungeonUser;
import vn.giakhanhvn.skysim.entity.SEntity;
import vn.giakhanhvn.skysim.entity.SEntityType;
import vn.giakhanhvn.skysim.user.User;
import vn.giakhanhvn.skysim.util.BlockFallAPI;
import vn.giakhanhvn.skysim.util.SUtil;
import vn.giakhanhvn.skysim.util.Sputnik;
import vn.giakhanhvn.skysim.util.TimedActions;
import vn.giakhanhvn.skysim.util.customentities.GiantItemDisplay;

public class GoldorPhase implements WitherLordPhase {
	static final int SIMON_SAYS_PHASE = 2;
	
	public WitherLordsHandle lords;
	
	public Wither wither;
	public GoldorWither goldorWither;
	
	private Location coreEntrance;
	/**
	 * True IF the BOSS, Goldor, is IN the core (begin from the middle of the gold pad)
	 */
	private boolean enteredCore = false;
	public boolean finishedLastTerminals = false;
	
	public double goldorSpawnY;
	
	// for the animation
	NecronTrapdoorAnimation necronDoor;
	
	public GoldorPhase(WitherLordsHandle handle) {
		this.lords = handle;
		this.necronDoor = new NecronTrapdoorAnimation(lords.necronTrapdoor.get(0), lords.necronTrapdoor.get(1));
	}
	
	@Override
	public void start() {
		// normal initialization
		lords.bossbar.setTitle("&c&lGoldor");
		this.goldorSpawnY = lords.goldorSpawnPoint.getY();
		
		// calculate core entrance by using mid() on stage 1 loc and stage 4 loc
		coreEntrance = lords.goldorPath[1].mid(lords.goldorPath[4]);
		// set Y
		coreEntrance.setY(lords.finalGoldorLocation.getY());
		
		// initialize all four stages
		for (int i = 1; i <= 4; i++) {
			lords.goldorStages[i].preInit(this);
		}
		
		// spawn
		wither = (Wither)new SEntity(lords.goldorSpawnPoint, SEntityType.GOLDOR_TECH_DEMO,
			Sputnik.getNMSWorld(lords.goldorSpawnPoint), this
		).getEntity();
		// assignments
		goldorWither = (GoldorWither)((CraftWither) wither).getHandle();
		
		// first off, move its ass to the p1 starting point
		goldorWither.queuedMoveTo(
			Sputnik.setYTo(
				lords.goldorPath[1],
				lords.goldorSpawnPoint.getY()
				// the boss must move slowly to the doorstep of the first
				// stage, during this process, do not accelerate
			), () -> currentWitherLocation = 1
		);
		
		// start first stage
		this.startStage(1);
	}
	
	public GoldorStage stage;
	
	/** The stage that players are currently working on at the moment. */
	public int currentStage = 0;
	/** The stage that Goldor is currently moving through/or idle at the moment. */
	public int currentWitherLocation = 0; // 0 = the spawn point
	
	/**
	 * Initiates the specified stage of the Goldor Phase, managing Goldor's movement with
	 * Goldor Stages
	 * <p>
	 * When a new stage starts, Goldor is instructed to move to the entrance of the next stage. 
	 * If he arrives before players complete the current stage, he will stay idle at the entrance 
	 * until further instructions are given.
	 * </p>
	 * <h3>Detailed Behavior Explanation:</h3>
	 * <p>
	 * The process follows a sequential progression where Goldor's movement is queued stage-by-stage. 
	 * Below is a step-by-step breakdown of the flow:
	 * </p>
	 * <ol>
	 *     <li><b>Stage 0 (Spawn Point):</b> 
	 *         <ul>
	 *             <li>Goldor spawns at the initial spawn point.</li>
	 *             <li>The first invocation of <code>startStage(1)</code> moves him to the doorstep 
	 *             of stage 1 at a slow pace.</li>
	 *         </ul>
	 *     </li>
	 *     <li><b>Transition to Stage 1:</b> 
	 *         <ul>
	 *             <li>Goldor reaches the doorstep of stage 1 and slowly moves to stage 2 doorstep while waiting for players to complete stage 1.</li>
	 *             <li>Once the players complete stage 1, <code>startStage(2)</code> is called.</li>
	 *         </ul>
	 *     </li>
	 *     <li><b>Transition to Stage 2:</b>
	 *         <ul>
	 *             <li>If Goldor has already reached the doorstep of stage 2, he will remain idle.</li>
	 *             <li>If he has not yet reached the doorstep (i.e., <code>currentWitherLocation != currentStage</code>), 
	 *             acceleration will be enabled (<code>accelerate = true</code>) to ensure he reaches it quickly.</li>
	 *             <li>Upon reaching the doorstep, he automatically proceeds towards stage 3.</li>
	 *         </ul>
	 *     </li>
	 *     <li><b>Further Stages (Repeating Process):</b>
	 *         <ul>
	 *             <li>This pattern continues until Goldor reaches the final stage.</li>
	 *             <li>At each stage, Goldor waits for players and accelerates if necessary to maintain progression.</li>
	 *         </ul>
	 *     </li>
	 * </ol>
	 * 
	 * <h3>Key Points:</h3>
	 * <ul>
	 *     <li><b>Movement Queue:</b> Goldor’s movements are queued in advance and not immediate.</li>
	 *     <li><b>Idle Waiting:</b> If he reaches the next stage too early, he will remain stationary.</li>
	 *     <li><b>Acceleration:</b> Goldor will speed up to catch up if he falls behind the intended progress.</li>
	 *     <li><b>Punishment:</b> If he arrives before the players complete the current stage, he will issue a punishment dialog.</li>
	 * </ul>
	 *
	 * @param index The stage index to start (ranging from 1 to 4).
	 * @implNote Goldor's movement is managed through a queue to ensure pacing that aligns with player progress.
	 */
	public void startStage(int index) {
		// setup the variables
		this.stage = lords.goldorStages[index];
		this.stage.start(); // start this stage
		this.currentStage = index;
		
		int next = (index % 4) + 1;
		// queue the move
		goldorWither.queuedMoveTo(
			// move to the door step of the next stage
			Sputnik.setYTo(
				lords.goldorPath[next],
				lords.goldorSpawnPoint.getY()
			),
			// when reached the doorstep of that stage
			() -> {
				// set it to the current location
				currentWitherLocation = next;
				// if the wither reached the location BEFORE the players, send
				// the punishment dialog
				if (currentWitherLocation > currentStage) {
					goldorWither.say(GoldorWither.PUNISHMENT_SERVED, goldorWither.DIALOG_GAP);
				}
			}
		);
		
		// the wither will need to accelerate (speed up)
		// to reach the next door quickly enough
		// Basically, if Goldor is not currently at the doorstep of a stage as it begins,
		// he needs to accelerate to reach that point
		// EXCLUSION: will not speed up if Goldor is moving from 0 to stage 1 begin
		if (currentWitherLocation != 0 && currentWitherLocation != index) {
			goldorWither.accelerate = true;
		}
		
		// say the dialog
		if (index == 1) return; 
		goldorWither.say(GoldorWither.NEXT_CHECKPOINT_REACH, goldorWither.DIALOG_GAP);
	}
	
	/**
	 * Move to the core
	 * @param middlePoint the golden gate
	 */
	public void moveToCore(Location middlePoint) {
		goldorWither.stopMovingToLocation();
		
		// set factor to 1
		goldorWither.speedAcceleratorFactor = 1.0f;
		
		// clear all previous moves
		goldorWither.moves.clear();
		
		// moves quickly to the core entrance (the gold pad)
		goldorWither.moveToLocation(coreEntrance, GoldorWither.GOLDOR_VECTOR_SPEED * 1.5,
		() -> {
			goldorWither.setShield(false);
			// set this flag to true so that he can be damaged,
			// his insta-kill ability is disabled and his frenzy 
			// now deals normal damage instead of true damage. Also
			// he's able to shoot shit out of his ass now
			this.enteredCore = true;
			// Accelerate during the arrival of the golden door
			goldorWither.accelerate = true;
			// resets the hit counter
			goldorWither.hitCounter = 0;
			
			// move to the door of the core (golden door) with the height of his final destionation
			goldorWither.moveToLocation(Sputnik.getBlockMatchPos(middlePoint), GoldorWither.GOLDOR_VECTOR_SPEED * 1.5, 
			() -> {
				// say the messages
				goldorWither.saySequencedDialog(GoldorWither.ON_CORE_ENTERS).add(() -> {
					goldorWither.say(null);
				}).run();
				// he should not accelerate during this period
				// as he is approaching the core dropdown (at the skull)
				goldorWither.accelerate = false;
				// he will slowly move to his final destination once nea
				goldorWither.moveToLocation(lords.finalGoldorLocation, GoldorWither.GOLDOR_VECTOR_SPEED * 2, () -> {
					// we dont do anything when he reaches this point
				});
			});
		});
		
		// accelerate
		goldorWither.accelerate = true;
	}
	
	public DungeonUser getRandomPlayer() {
		return SUtil.getRandom(
			lords.getHandle().getAlivePlayersList()
		);
	}

	@Override
	public void end() {
		// remove the flying swords
		for (GiantItemDisplay sword : goldorWither.flyingSwords) {
			if (sword == null) continue;
			sword.remove();
		}
		// force the wither into dummy mode
		goldorWither.stopTicking = true;
		// the wither will be removed from the world after 30s
		SUtil.delay(() -> {
			goldorWither.getBukkitEntity().remove();
		}, 30 * 20);
		
		if (lords.getHandle().isEnded()) return;
		
		// open the trapdoor and advance to the next phase
		lords.currentPhase = new NecronPhase(lords);
		lords.currentPhase.start();
		// opens the door
		this.necronDoor.playAnimation();
	}
	
	/**
	 * The wither--Goldor itself
	 * @author GiaKhanhVN
	 */
	public static class GoldorWither extends CustomWitherEntity {
		public int DIALOG_GAP = 50;
		
		// the reference to outside systems
		private GoldorPhase core;
		static final float GOLDOR_VECTOR_SPEED = 0.05f;
		
		// flags
		// dying and stopTicking is basically the same
		// DUMMIFIED means, he cannot update the bossbar anymore (as Necron took the control from
		// this point on)
		public boolean dying = false; // if the boss is dying (for saying his last words), he cannot
		// use any of his abilities but still can update necessities like bossbar and nametags
		public boolean stopTicking = false; // if true, the boss will minimize the tick function
		// as much as possible
		
		// construct
		public GoldorWither(WorldServer world, GoldorPhase goldor) {
			super(world, 0);
			this.core = goldor;
			this.setShield(true);
			this.entityOwner = core.lords.getHandle();
			this.hoverHeight = 1.55f;
			this.frozen = true;
			this.DIALOG_GAP = 50;
		}
		
		@Override
		public String getEntityName() {
			return this.witherName();
		}
		
		@Override
		public String witherName() {
			return "Goldor";
		}
		
		// dialogs
		static String[] WELCOME_DIALOG = new String[]{
			"Who dares trespass into my domain?",
			"Little ants, plotting and scheming, thinking they are invincible...",
			"I won’t let you break the factory core, I gave my life to my Master.",
			"No one matches me in close quarters."
		};
				
		static String[] DEATH = new String[]{
			"...",
			"Necron, forgive me."
		};
		
		static String[] RANDOM_MESSAGES = new String[]{
			"Come closer!",
			"CLOSER!", "Closer to me!",
			"You are breaking precious materials, unforgivable.",
			"Do you really think we won’t repair everything? Your impact will be minuscule!",
			"I am the death zone, you are smart to flee."
		};
		
		static String[] BOSS_SLOWED_DOWN = new String[]{
			"Slowing me down only prolongs your pain!",
			"You can't damage me, you can barely slow me down!",
			"There is no stopping me down there!",
			"Stop touching those terminals!",
		};
		
		static String[] ON_CORE_ENTERS = new String[] {
			"You have done it, you destroyed the factory...",
			"But you have nowhere to hide anymore!",
			"YOU ARE FACE TO FACE WITH GOLDOR!"
		};
		
		static String NEXT_CHECKPOINT_REACH = "The little ants have a brain it seems.";
		static String PUNISHMENT_SERVED = "Punishment served, nothing survives my reach.";
		
		@Override
		public void onSpawn(LivingEntity entity, SEntity sEntity) {
			super.onSpawn(entity, sEntity);
			// initial spawn
			this.replenishFlyingSword(4);
			// dialog
			this.saySequencedDialog(WELCOME_DIALOG).add(() -> {
				say(null);
			}).run();
		}
		
		// if the boss is accelerating, the vector speed factor will be SET to 6
		public boolean accelerate = false;
		// the queued moves
		public Queue<MovementJob> moves = new ArrayDeque<>();
		
		public void queuedMoveTo(Location loc, Runnable runnable) {
			MovementJob movementJob = new MovementJob();
			movementJob.target = loc;
			movementJob.whenCompleted = runnable;
			
			moves.add(movementJob);
			
			if (!isMoving) {
				processNextMove();
			}
		}
		
		private void processNextMove() {
			// stop the acceleration on new move
			accelerate = false;
			
			if (!moves.isEmpty()) {
				// Poll the next movement job
				MovementJob nextMove = moves.poll();
				
				// Start moving to the location
				moveToLocation(nextMove.target, GOLDOR_VECTOR_SPEED, () -> {
					// After the move is completed, run the associated Runnable
					if (nextMove.whenCompleted != null) nextMove.whenCompleted.run();
					
					// Once the current move is done, process the next move in the queue
					processNextMove();
				});
			}
		}
		
		public static class MovementJob {
			Location target;
			Runnable whenCompleted;
		}
		
		// the greatswords array (swords that will fly around goldor)
		public GiantItemDisplay[] flyingSwords = new GiantItemDisplay[4];
		private long counter = 0;
		// count the hits to slow the wither down
		private int hitCounter = 0;
		
		/** for the greatswords */
		public static final double RADIANS_PER_TICK = (2f * Math.PI) / 100f;
		
		@Override
		public void t_() {
			super.t_();
			// with this flag (formerly "dummified", he won't run his methods anymore)
			if (stopTicking) return;
			
			// internal clock counter
			counter++;
			
			this.hologramEntity.teleport(wither.getLocation().add(0, WitherLordsHandle.NAME_OFFSET, 0));
			this.dialougeEntity.teleport(wither.getLocation().add(0, WitherLordsHandle.DIABOX_OFFSET, 0));
			
			// sync the bossbar
			core.lords.bossbar.setProgress(wither.getHealth() / wither.getMaxHealth());
			
			// TODO | NOTE: WITH DYING SET TO TRUE, GOLDOR CAN STILL TICK HIS
			// BOSSBAR AND NAMETAGS TELEPORTATION
			if (dying) return;
			
			// if the boss is in core already, ignore hit counter
			if (core.enteredCore) {
				this.hitCounter = 0;
			}
			
			// synchronize speed with hit counter
			// the slowest speed factor is 1 - 0.095 * hitCounter (which is 0.05x the speed of normal Goldor)
			// the fastest factor is 8x the speed
			this.speedAcceleratorFactor = accelerate ? 8f : (1f - (Math.min(10, hitCounter) * 0.095f));
			
			// clear the hit counter every 1.5s
			if (counter % 30 == 0) {
				// say the dialog ONLY if the thing has been hit at least TWICE
				// in the last 30 ticks
				if (this.hitCounter > 2 && this.currentDialog == null) {
					say(SUtil.randArr(BOSS_SLOWED_DOWN), DIALOG_GAP);
				}
				this.hitCounter = 0;
			}
			
			// shadow wave (goldor's frenzy), but WAY more powerful
			// grace period: when goldor is accelerating, he wont damage
			if (counter % 20 == 0 && !accelerate) {
				// deal immense damage
				// SKYSIM TWIST: Goldor will deal 33.3% of players' HP (true dmg)
				// WHILE NOT in the core. Inside the core, Goldor will deal
				// 12x its normal damage (not true damage)
				Sputnik.hittablePlayers(wither.getLocation(), 8, 20, 8).forEach(player -> {
					User user = User.getUser(player);
					damagePlayer(
						user, "Frenzy", 
						core.enteredCore ? (getDamageDealt() * 12) : player.getMaxHealth() * 0.33, 
						!core.enteredCore // if entered core, use normal dmg instead
					);
				});
			}
			
			// play the effects regardless
			if (counter % 20 == 0) {
				playLargeExplosion(wither.getLocation());
			}
			
			// explode surroundings every half a second
			if (counter % 10 == 0) {
				explodeSurroundings();
			}
			
			// replenish greatswords every 5s
			if (counter % 100 == 0) {
				replenishFlyingSword(1);
			}
			
			// explode the hanging TNTs every 10s AND
			// the wither is aligned straight
			if (counter % 200 == 0 && this.yaw % 90 == 0) {
				this.activeGreatsword();
			}
			
			// greatsword tick (rotate)
			for (int i = 0; i < flyingSwords.length; i++) {
				if (flyingSwords[i] == null) continue;
				Location loc = this.circleLoc(
					wither.getLocation(), 3,
					// for every tick, moves RAD_PER_TICK
					// and keep distance i * (2pi / 4) with
					// the other swords
					(RADIANS_PER_TICK * counter) + (i * (2 * Math.PI / this.flyingSwords.length))
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
			
			// default attack (fire skull every 5-10 ticks)
			if (core.enteredCore && counter % SUtil.random(10, 15) == 0) {
				playHiss();
				// rapidly fire
				new TimedActions()
					.add(() -> shoot(core.getRandomPlayer().getPlayer(), 1)) // shoot the middle-head first
					.wait(3).add(() -> {
						// shoot the other 2 heads
						shoot(core.getRandomPlayer().getPlayer(), 0);
						shoot(core.getRandomPlayer().getPlayer(), 2);
				}).run();
			}
			
			// kill players behind, every 5s
			// only if they havent entered the core yet
			if (counter % 100 == 0 && !(core.finishedLastTerminals || core.enteredCore)) {
				// loop through all players alive at that point, calculate if they're behind or front
				for (DungeonUser p : core.lords.getHandle().getAlivePlayersList()) {
					Vector pDir = p.getInternal().getLocation().toVector().subtract(
						wither.getLocation().toVector()
					);
					Vector eDir = wither.getLocation().getDirection();
					
					// the angle (atan), stolen from spigotorg
					double xv = pDir.getX() * eDir.getZ() - pDir.getZ() * eDir.getX();
					double zv = pDir.getX() * eDir.getX() + pDir.getZ() * eDir.getZ();
					double angle = Math.atan2(xv, zv); // Value between -π and +π
					double angleInDegrees = (angle * 180) / Math.PI;
					
					// actions IF the player is behind AND distance > 10
					boolean front = angleInDegrees >= -90 && angleInDegrees <= 90;
					boolean farEnough = wither.getLocation().distanceSquared(p.getInternal().getLocation()) >= 100;
					
					// if back and far enough AND
					// the boss has hit the first stage
					if (core.currentWitherLocation > 0 && !front && farEnough) {
						// damage by 6x the boss DPS
						damagePlayer(
							p.getInternal(), "&8Instant Death", 
							p.getPlayer().getMaxHealth() * 10, true
						);
					}
				}
			}
			
			// say random message
			if (counter % SUtil.random(110, 260) == 0 // every 5.5s to 13s
			 && this.currentDialog == null // only if there's no current dialog
			 && !core.finishedLastTerminals /* Goldor specific: only say random if the player hasnt finished all terminals */
			) {
				this.say(SUtil.randArr(RANDOM_MESSAGES), DIALOG_GAP);
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
					new ItemStack(Material.GOLD_SWORD, 1)
				);
				
				amount--;
			}
		}
		
		// activate the greatsword ability
		public void activeGreatsword() {
			Location selectedBasket = null; 
			
			// TNT basket attacks should ONLY be done if the boss is inside the stage
			if (isMovingInsideStage() && core.stage.hangingTNT.size() > 0) {
				selectedBasket = core.stage.hangingTNT.remove(0);
			}
			
			// if there isnt a TNT basket left, aim for a random player
			if (selectedBasket == null) {
				this.launchGreatswordToPlayer();
				return;
			}
			
			// if there is a TNT basket left, aim for it, not the players
			this.launchGreatswordToTNTBasket(selectedBasket);
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
						// damage by 6x the boss DPS
						damagePlayer(
							user, "Greatsword", 
							getDamageDealt() * 6, false
						);
					});
				});
				
				return;
			}
		}
		
		public void launchGreatswordToTNTBasket(Location tntBasket) {
			// find an appropriate sword
			for (int i = 0; i < flyingSwords.length; i++) {
				GiantItemDisplay sword = flyingSwords[i];
				if (sword == null) continue;
				
				// detach the sword from the main wither
				flyingSwords[i] = null;
				
				// launch towards the TNT basket
				launchGreatswordToLoc(sword, tntBasket, () -> {
					// remove the sword
					sword.remove();
					
					// get the entire basket by doing a cuboid
					List<Block> basket = Sputnik.cuboid(
						tntBasket.clone().add(-1, 1, 1), // upper bound
						tntBasket.clone().add(1, -1, -1)  // lower bound
					);
					
					// drop the tnt down
					basket.forEach(b -> {
						// clear the tnt block
						b.setType(Material.AIR);
						// drop the tnt
						TNTPrimed tnt = wither.getWorld().spawn(
							Sputnik.getBlockMatchPos(b.getLocation())
						, TNTPrimed.class);
						// explode
						tnt.setFuseTicks(21);
						// actually damaging surrounding players
						SUtil.delay(() -> {
							Location tntLoc = tnt.getLocation();
							
							Sputnik.hittablePlayers(tntLoc, 5, 3, 5).forEach(p -> {
								Vector pushVector = p.getLocation().toVector().subtract(tntLoc.toVector()).normalize();
								pushVector.multiply(2);
								// knockback
								p.setVelocity(pushVector);
								
								// damage 5x the normal dmg
								damagePlayer(User.getUser(p), null, getDamageDealt() * 5, false);
							});
							
							// remove the TNT
							tnt.remove();
							
							// play effects
							tnt.getLocation().playEffect(Effect.EXPLOSION_HUGE, 0);
							tnt.getLocation().playSound(Sound.EXPLODE, 1, 1);
						}, 20);
					});
				});
				
				return;
			}
		}
		
		public void explodeSurroundings() {
			// if the wither is currently idle OR entered core, dont do anything
			if (!isMoving || core.enteredCore) return;
			
			// only explode if the wither is aligned straightly
			if (this.yaw % 90 != 0) return;
			
			// vectors
			Vector direction = wither.getLocation().getDirection();
			Vector toTheRight = Sputnik.rotateAroundAxisY(direction, 90);
			
			// select the uppermost and lowermost of the bounding box
			Location pos1 = wither.getLocation().add(
				toTheRight.clone()
				.normalize().multiply(6)
			).add(0, 8, 0);
			
			Location pos2 = wither.getLocation().add(
				toTheRight.clone()
				.normalize().multiply(-6)
			).add(0, -7, 0);
			
			List<Block> inRange = Sputnik.cuboid(pos1, pos2);
			inRange.forEach(b -> {
				if (b.getType() == Material.AIR) return; // dont explode air
				// if cant explode, return
				if (!canExplode(b.getType())) return;
				
				// predicates
				if (b.getLocation().distanceSquared(wither.getLocation()) <= 16
				|| SUtil.random(0, 2) == 0) { // 3 blocks or randomly
					// play animation
					Material type = b.getType();
					byte data = b.getData();
					
					BlockFallAPI.sendBlock(
						Sputnik.getBlockMatchPos(b.getLocation()), 
						type, data, b.getWorld(), 40
					);
					
					// destroy the block
					b.setType(Material.AIR);
				}
			});
		}
		
		/**
		 * Returns true if Goldor is moving INSIDE the
		 * current stage
		 * @return true if inside current stage
		 */
		boolean isMovingInsideStage() {
			return core.currentStage == core.currentWitherLocation;
		}
		
		boolean canExplode(Material material) {
			return material != Material.COMMAND &&
				   material != Material.GOLD_BLOCK &&
				   material != Material.STAINED_CLAY &&
				   material != Material.EMERALD_BLOCK &&
				   material != Material.LEVER &&
				   material != Material.STONE_BUTTON &&
				   material != Material.TNT &&
				   material != Material.BARRIER &&
				   material != Material.NETHER_BRICK_STAIRS &&
				   material != Material.NETHER_BRICK &&
				   material != Material.GOLD_PLATE;
		}
		
		@Override
		public void onDamage(SEntity sEntity, Entity damager, EntityDamageByEntityEvent e, AtomicDouble damage) {
			// after goldor is "dummified", he shall receive no damage
			if (stopTicking) {
				damage.set(0);
				return;
			}
			
			// actual logic
			if (!core.enteredCore || dying) {
				// add the hit counter only if he's NOT in the core
				hitCounter++;
				// nullify damage
				damage.set(0);
				return;
			}
			
			double dmg = damage.get();
			// if the HP AFTER DAMAGED is below 5, trigger death sequence
			if (wither.getHealth() - dmg <= 5 && !dying) {
				// prevent player from damaging it
				damage.set(wither.getHealth() - 5);
				this.dying = true;
				
				// turn off wither shield
				this.setShield(false);
				// stop the wither immediately
				this.stopMovingToLocation();
				
				// double the dialog length
				this.DIALOG_GAP *= 1.5;
				
				// dialog for goldor
				saySequencedDialog(DEATH)
				
				// trigger phase 4
				.add(() -> {
					say(null);
					// call the end event, this will open the trapdoor
					// and trigger phase 4, handling control over to Necron
					core.end();
				})
				.run();
			}
		}
		
		@Override
		public double getEntityMaxHealth() {
			return 750_000_000;
		}

		@Override
		public double getDamageDealt() {
			return 10_000;
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
