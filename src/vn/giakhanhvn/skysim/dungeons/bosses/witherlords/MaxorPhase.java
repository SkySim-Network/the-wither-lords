package vn.giakhanhvn.skysim.dungeons.bosses.witherlords;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.google.common.util.concurrent.AtomicDouble;

import net.minecraft.server.v1_8_R3.WorldServer;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.WitherLordsHandle.WitherLordPhase;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.HologramAttachedEntity;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.animations.TrapdoorAnimation;
import vn.giakhanhvn.skysim.dungeons.systems.DungeonUser;
import vn.giakhanhvn.skysim.dungeons.systems.classes.DungeonClass.EnumClass;
import vn.giakhanhvn.skysim.entity.SEntity;
import vn.giakhanhvn.skysim.entity.SEntityType;
import vn.giakhanhvn.skysim.user.User;
import vn.giakhanhvn.skysim.util.DynamicHologram;
import vn.giakhanhvn.skysim.util.STask;
import vn.giakhanhvn.skysim.util.SUtil;
import vn.giakhanhvn.skysim.util.Sputnik;
import vn.giakhanhvn.skysim.util.TimedActions;
import vn.giakhanhvn.skysim.util.STask.RunMode;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;

public class MaxorPhase implements WitherLordPhase {
	public WitherLordsHandle lords;
	private World world;
	
	// the two crystals
	private HologramAttachedEntity[] energyCrystals = new HologramAttachedEntity[2];
	
	// the indicator for the 2 crystal pads
	private DynamicHologram[] padHolo = new DynamicHologram[2];
	private Entity[] padCrystal = new Entity[2];
	
	// trackers
	private int pickedUpCrystalsCount = 0;
	private boolean[] laserPowered = { false, false };
	
	// 0 = black, 1 = yellow, 2 = red (deadly)
	public int laserState = 0;

	// the entity of this phase, maxor
	public Wither maxor;
	
	public TrapdoorAnimation trapdoor;
	
	public MaxorPhase(WitherLordsHandle handle) {
		this.lords = handle;
		this.world = lords.getHandle().getWorld();
		this.trapdoor = new TrapdoorAnimation(
			lords.trapDoorPos1, 
			lords.trapDoorPos2
		);
	}
	
	
	@Override
	public void start() {
		this.lords.bossbar.setTitle("&c&lMaxor");
		// spawn wither skeletons
		this.lords.spawnPhaseWitherSkeletons(0); // phase 1
		
		// initialize boss mechanics
		initStatic();
		resetState(true);
		
		// spawn
		lords.maxorSpawnPoint.setYaw(180);
		maxor = (Wither)new SEntity(lords.maxorSpawnPoint, SEntityType.MAXOR_TECH_DEMO,
			Sputnik.getNMSWorld(lords.maxorSpawnPoint), this
		).getEntity();
		
		// the core task loop
		new STask().run(s -> {
			if (!(lords.currentPhase instanceof MaxorPhase)) {
				s.kill();
				return;
			}
			if (maxor.isDead()) {
				this.end();
				s.kill();
				return;
			}
			
			onPhaseTick(s.counter);
			s.counter++;
		}).startLoop(RunMode.SYNC, 0, 0);
	}

	@Override
	public void end() {
		if (lords.getHandle().isEnded()) return;
		// clear
		clearPhase();
		
		// advance to storm phase
		lords.currentPhase = new StormPhase(lords);
		
		new TimedActions()
		.wait(20)
		.add(() -> {
			// opens the trapdoor
			this.trapdoor.playAnimation().run();
		})
		.wait(10) // wait so the player falls down
		.add(() -> {
			// start storm phase
			lords.currentPhase.start();
		}).run();
	}
	
	// this will be called when the player touches a crystal
	public void onInteractionWithCrystal(User u, int index) {
		if (pickedUpCrystalsCount > 0) {
			u.send("&cYou can only carry one crystal at a time!");
			return;
		}
		
		// destroy and say a msg
		energyCrystals[index].kill();
		lords.getHandle().sayTo(u.getDisplayName() + " &apicked up an &bEnergy Crystal&a!");
		
		// increment
		pickedUpCrystalsCount++;
	}
	
	// spawn crystals in destined places
	public void spawnCrystals() {
		for (int i = 0; i < 2; i++) {
			// spawn 2 left and right crystal
			energyCrystals[i] = spawnCrystal(
				Sputnik.getBlockMatchPos(lords.energyCrystals.get(i))
			);
			
			final int index = i;
			energyCrystals[i].entity.onClickByPlayer(e -> {
				User clicked = User.getUser(e.getPlayer());
				if (clicked.isAGhost()) return;
				// fire interact event
				onInteractionWithCrystal(clicked, index);
			});
		}
	}
	
	// spawn a crystal in loc
	private HologramAttachedEntity spawnCrystal(Location l) {
		Entity ec = world.spawnEntity(l, EntityType.ENDER_CRYSTAL);
		// with hologram entity
		HologramAttachedEntity e = new HologramAttachedEntity(
			ec, 
			new DynamicHologram(l, Arrays.asList("&bEnergy Crystal", "&e&lCLICK HERE"), 0.35, 35),
		1.75);
		return e;
	}
	
	// this will setup the initial basis of the bossfight, this includes:
	// left-right pads, left-right wires, and the conveyor belt
	public void initStatic() {
		Location lp = Sputnik.getBlockMatchPos(lords.leftPad);
		padHolo[0] = new DynamicHologram(lp.add(0, 1.25, 0), 2, 0.35, 35);
		
		Location rp = Sputnik.getBlockMatchPos(lords.rightPad);
		padHolo[1] = new DynamicHologram(rp.add(0, 1.25, 0), 2, 0.35, 35);
		
		this.precompute();
		this.initializeConveyorBelt();
	}
	
	// cached animation
	private List<Location> cachedRightSide = new ArrayList<>();
	private List<Location> cachedLeftSide = new ArrayList<>();
	
	private void precompute() {
		// trace the cable for aniamtion
		cachedRightSide.addAll(traceRoute(lords.rightSideBegin, lords.rightSideJoint));
		cachedRightSide.addAll(traceRoute(lords.rightSideJoint, lords.rightSideEnd));

		cachedLeftSide.addAll(traceRoute(lords.leftSideBegin, lords.leftSideFirstJoint));
		cachedLeftSide.addAll(traceRoute(lords.leftSideFirstJoint, lords.leftSideSecondJoint));
		cachedLeftSide.addAll(traceRoute(lords.leftSideSecondJoint, lords.leftSideEnd));
		
		// set them all to coal blocks
		for (Location l : cachedLeftSide) {
			l.getBlock().setType(Material.COAL_BLOCK);
		}
		for (Location l : cachedRightSide) {
			l.getBlock().setType(Material.COAL_BLOCK);
		}
	}
	
	public List<Location> traceRoute(Location joint1, Location joint2) {
		List<Location> locations = new ArrayList<>();
		Vector direction = joint2.toVector().subtract(joint1.toVector()).normalize();
		
		int i = 0;
		while (i < 100) {
			Location newLoc = joint1.clone().add(
				direction.clone().multiply(i)
			);
			locations.add(newLoc);
			if (newLoc.getBlockX() == joint2.getBlockX() 
				&& newLoc.getBlockY() == joint2.getBlockY() 
				&& newLoc.getBlockZ() == joint2.getBlockZ()
			) {
				break;
			}
			i++;
		}
		
		return locations;
	}
	
	public void synchronizeLaserState() {
		lords.laserBeaconLocation.getBlock().setType(Material.STAINED_GLASS);
		lords.laserBeaconLocation.getBlock().setData((byte) (laserState == 0 ? 15 : (laserState == 1 ? 4 : 14)));
	}
	
	// reset the crystal states
	public void resetState(boolean initial) {
		spawnCrystals();
		
		// reset the power lines
		laserPowered = new boolean[] { false, false };
		
		// reset count
		pickedUpCrystalsCount = 0;
		
		// reset the state of laser
		laserState = 0;
		synchronizeLaserState();
		
		// remove 2 crystal pads
		for (Entity entity : padCrystal) {
			if(entity != null) entity.remove(); 
		}
		
		// reset holname
		for (DynamicHologram dyn : padHolo) {
			dyn.setLine(1, "&cEnergy Crystal Missing");
			dyn.setLine(0, "&e&lSTAND HERE");
			dyn.update();
		}
		
		// power off both left and right
		if (!initial) {
			powerSwitchLeftSide(false, null);
			powerSwitchRightSide(false, null);
		}
	}
	
	// on phase end, clear
	public void clearPhase() {
		// clean up, as usual
		laserPowered = new boolean[] { false, false };
		pickedUpCrystalsCount = 0;
		
		// destroy
		laserState = 0;
		synchronizeLaserState();
		
		// clear everything
		for (Entity entity : padCrystal) {
			if(entity != null) entity.remove(); 
		}
		
		for (DynamicHologram dyn : padHolo) {
			dyn.destroy();
		}
		
		powerSwitchLeftSide(false, null);
		powerSwitchRightSide(false, null);
		
		// TODO destroy animation
		
	}
	
	// call on crystal place
	public void updateCrystalState() {
		laserState = (laserPowered[0] || laserPowered[1]) ? 1 : 0;
		if (laserPowered[0] && laserPowered[1]) {
			TimedActions ta = new TimedActions();
			
			ta.wait(40).add(() -> {
				lords.getHandle().sendSubtitleWithChat( 
					"&aThe Energy Laser is charging up!",
				0, 50, 0);
			}).wait(60).add(() -> {
				this.laserState = 2;
				synchronizeLaserState();
			});
			
			ta.run();
		}
		synchronizeLaserState();
		
		// shit
		int count = 0;
		for (boolean b : laserPowered) {
			if(b) count++;
		}
		if (count > 0) {
			lords.getHandle().sendSubtitleWithChat( 
				"%d%s&a/2 Energy Crystals are now active!".replace("%d", count == 1 ? "&c" : "&a")
				.replace("%s", "" + count),
			0, 70, 0);
		}
	}
	
	// tick on player step pressure plates
	public void playerOnPressurePlateTick() {
		boolean changed = false;
		
		// if left side has player stepped on it and has enough crystal
		if (!laserPowered[0] && hasPlayerLeftPad && pickedUpCrystalsCount > 0) {
			// power on the left wire (animation)
			powerSwitchLeftSide(true, null);
			
			// switch the flags to true
			laserPowered[0] = true;
			pickedUpCrystalsCount--; // remove crystal
			changed = true;
			
			// change hologram
			padHolo[0].setLine(1, "&aCrystal Active");
			padHolo[0].setLine(0, null);
			padHolo[0].update();
			
			// summon the crystal entity
			padCrystal[0] = world.spawnEntity(
				Sputnik.getBlockMatchPos(lords.leftPad), EntityType.ENDER_CRYSTAL
			);
		}
		
		// if right side has player stepped on it and has enough crystal
		if (!laserPowered[1] && hasPlayerRightPad && pickedUpCrystalsCount > 0) {
			// power on the right wire (animation)
			powerSwitchRightSide(true, null);
			
			// switch flags
			laserPowered[1] = true;
			pickedUpCrystalsCount--;
			changed = true;
			
			// change hologram
			padHolo[1].setLine(1, "&aCrystal Active");
			padHolo[1].setLine(0, null);
			padHolo[1].update();
			
			// summon crystal
			padCrystal[1] = world.spawnEntity(
				Sputnik.getBlockMatchPos(lords.rightPad), EntityType.ENDER_CRYSTAL
			);
		}
		
		// if changed, update state
		if (changed) this.updateCrystalState();
	}
	
	// play animation on the right side (righjt pad)
	private void powerSwitchRightSide(boolean on, Runnable onComplete) {
		TimedActions ta = new TimedActions();
		for (Location l : cachedRightSide) {
			ta.add(() -> {
				l.getBlock().setType(on ? Material.SEA_LANTERN : Material.COAL_BLOCK);
			}).wait(0);
		}
		ta.add(onComplete);
		ta.run();
	}
	
	// play animation on the left side (righjt pad)
	private void powerSwitchLeftSide(boolean on, Runnable onComplete) {
		TimedActions ta = new TimedActions();
		for (Location l : cachedLeftSide) {
			ta.add(() -> {
				l.getBlock().setType(on ? Material.SEA_LANTERN : Material.COAL_BLOCK);
			}).wait(0);
		}
		ta.add(onComplete);
		ta.run();
	}
	
	// conveyor belt stuff
	ArrayDeque<List<BlockSimple>> conveyorBeltBasis = new ArrayDeque<>();
	List<List<Location>> conveyorBelt = new ArrayList<>();
	
	public void initializeConveyorBelt() {
		int sliceUpperY = lords.conveyorBeltEnd.getBlockY() + 3;
		int sliceLowerY = lords.conveyorBeltEnd.getBlockY() - 1;
		
		int sliceLeastZ = lords.conveyorBeltBegin.getBlockZ();
		int sliceUpmostZ = lords.conveyorBeltEnd.getBlockZ();
		
		World w = lords.conveyorBeltBegin.getWorld();
		// create slices of the conveyor belt so we can use queue properties to minimize
		// the amount of calculations
		
		for (int x = lords.conveyorBeltEnd.getBlockX(); x != lords.conveyorBeltBegin.getBlockX() + 1; x++) {
			// slice the conveyor belt along the X-axis and put each of them in a queue
			List<BlockSimple> slice = new ArrayList<>();
			List<Location> sliceBasis = new ArrayList<>();
			for (int sy = sliceUpperY; sy != sliceLowerY - 1; sy--) {
				int z = sliceLeastZ;
				for (int sz = z; sz != sliceUpmostZ - 1; sz--) {
					Block b = w.getBlockAt(x, sy, sz);
					// add to basis
					slice.add(new BlockSimple(b.getType(), b.getData()));
					sliceBasis.add(b.getLocation());
				}
			}
			
			// insert to the double phase queue
			conveyorBeltBasis.add(slice);
			conveyorBelt.add(sliceBasis);
		}
	}
	
	// for conveyor belt animation
	public void maxorConveyorBeltTick() {
		int index = 0;
		
		// paste the updated state to the world
		for (List<BlockSimple> bss : conveyorBeltBasis) {
			int j = 0;
			for (BlockSimple bs : bss) {
				Block b = conveyorBelt.get(index).get(j).getBlock();
				if (!b.getType().toString().toLowerCase().contains("cobble")) {
					b.setType(bs.material);
					b.setData(bs.data);
					
					// for setting up the skull
					if (bs.material == Material.SKULL) {
						Skull skull = (Skull) b.getState();
						// set to wither skull to immitate coal
						skull.setSkullType(SkullType.WITHER);
						skull.update(true);
					}
				}
				++j;
			}
			++index;
		}
		
		// post-process to fix the blockage of the "laser"
		for (int y = 2; y < 6; y++) {
			Location relativeLoc = lords.laserBeaconLocation.clone().add(0, y, 0);
			Block relBlock = relativeLoc.getBlock();
			
			// set the transparency blocks above
			if (relBlock.getType() != Material.AIR) {
				relBlock.setType(Material.GLASS);
				continue;
			}
			
			// carpet?
			relBlock.setType(Material.CARPET);
			relBlock.setData((byte)7);
			break;
		}
		
		// queue properties, make the conveyor belt moves
		conveyorBeltBasis.add(conveyorBeltBasis.pop());
	}
	
	/**
	 * A simple block structure
	 * @author GiaKhanhVN
	 */
	class BlockSimple {
		Material material;
		byte data;
		
		public BlockSimple(Material m, byte b) {
			this.material = !m.name().contains("COBBLE") ? m : Material.AIR;
			this.data = b;
		}
	}
	
	// flags
	boolean hasPlayerLeftPad;
	boolean hasPlayerRightPad;
	
	// basically check if there's a player on a pad
	public void onPhaseTick(long ticks) {
		if (ticks % 15 == 0) maxorConveyorBeltTick();
		if (ticks % 2 == 0) {
			Collection<Player> leftPad = Sputnik.hittablePlayers(Sputnik.getBlockMatchPos(lords.leftPad), .2, 1, .2);
			hasPlayerLeftPad = leftPad.size() > 0;
			
			Collection<Player> rightPad = Sputnik.hittablePlayers(Sputnik.getBlockMatchPos(lords.rightPad), .2, 1, .2);
			hasPlayerRightPad = rightPad.size() > 0;
			
			// tick?
			playerOnPressurePlateTick();
		}
	}
	
	/**
	 * The wither representing this phase
	 * @author GiaKhanhVN
	 */
	public static class MaxorWither extends CustomWitherEntity {
		private MaxorPhase core;
		public double damageThreshold; // for damage control
		public boolean dying; // if the entity is dying
		
		public MaxorWither(WorldServer world, MaxorPhase maxor) {
			super(world, 200);
			this.core = maxor;
			this.damageThreshold = getEntityMaxHealth() / 2;
			this.setShield(true);
			this.entityOwner = core.lords.getHandle();
			this.hoverHeight = 0.5f;
			this.frozen = true;
			this.DIALOG_GAP = 50;
		}
		
		// later
		@Override
		public String witherName() {
			return "Maxor";
		}
		
		@Override
		public String getEntityName() {
			return this.witherName();
		}
		
		// dialogs
		static String[] WELCOME_DIALOG = new String[]{
			"WELL WELL WELL LOOK WHO'S HERE!",
			"I'VE BEEN TOLD I COULD HAVE A BIT OF FUN WITH YOU.",
			"DON'T DISAPPOINT ME, I HAVEN’T HAD A GOOD FIGHT IN A WHILE."
		};
		
		static String[] DEATH = new String[]{
			"I'M TOO YOUNG TO DIE AGAIN!",
			"I'LL MAKE YOU REMEMBER MY DEATH!!"
		};
		
		static String[] HIT_BY_BEAM = new String[]{
			"YOU TRICKED ME!",
			"THAT BEAM! IT HURTS! IT HURTS!!",
		};
		
		static String[] RAPID_FIRE_DIALOG = new String[]{
			"Eat Wither Skulls, scum!",
			"How about you taste some rapid fire Wither Skulls!",
			"Time for me to blast you away for good!"
		};
		
		static String[] RANDOM_MESSAGES = new String[]{
			"YOUR WEAPONS CAN’T PIERCE THROUGH MY SHIELD!",
			"I HOPE YOU LIKE EXPLOSIONS TOO!",
			"MY MINIONS WILL HAVE TO WIPE THE FLOOR AFTER I'M DONE WITH YOU ALL!",
			"YOUR MOBILITY TRICKS DON'T WORK IN MY DOMAIN!"
		};
		
		public boolean welcomingFinish = false;
		@Override
		public void onSpawn(LivingEntity entity, SEntity sEntity) {
			super.onSpawn(entity, sEntity);
			// welcome dialog
			TimedActions welcome = saySequencedDialog(WELCOME_DIALOG);
			welcome.add(() -> {
				say(null);
				
				// set flags
				welcomingFinish = true;
				frozen = false;
				
				this.selectTarget();
			});
			
			welcome.run();
		}
		
		/** This is the task that will "end the boss's stun" after a set period of time **/
		public BukkitTask endStunTask = null;
		// the stun flag
		public boolean stun;
		
		public void onStun() {
			this.stun = true;
			this.frozen = true;
			this.setShield(false);
			
			// cosmetic
			wither.damage(0.0001D);
			say(HIT_BY_BEAM[SUtil.random(0, HIT_BY_BEAM.length - 1)], DIALOG_GAP);
			
			// spawn enraged skull
			ArmorStand armorStand = wither.getWorld()
				.spawn(wither.getLocation().add(0, WitherLordsHandle.NAME_OFFSET - 0.85, 0),
			ArmorStand.class);
			armorStand.setBasePlate(false);
			armorStand.setVisible(false);
			armorStand.setGravity(false);
			armorStand.setHelmet(SUtil.getSkullURL(WitherLordsHandle.ENRAGED_TEXTURE));
			
			// rotate!
			new STask().run(st -> {
				if (wither.isDead() || !stun || dying) {
					st.kill();
					armorStand.remove();
					return;
				}
				
				if (st.counter % 5 == 0) {
					armorStand.teleport(Sputnik.setYawOf(
						wither.getLocation().add(0, WitherLordsHandle.NAME_OFFSET - 0.85, 0),
						armorStand.getLocation().getYaw() + 55)
					);
				}
				st.counter++;
			}).startLoop(RunMode.SYNC, 0, 1);
			
			// end the stun after 12s of laser hit
			// after that, resume attacking
			this.endStunTask = SUtil.delay(() -> {
				if (!dying && stun) {
					endStun();
				}
			}, 20 * 12);
		}
		
		@Override
		public void enrage() {
			super.enrage();
			// activate shadow wave
			this.shadowWave();
		}
		
		public void endStun() {
			this.stun = false;
			this.frozen = false;
			this.setShield(true);
			
			// reset the crystals and pads
			this.core.resetState(false);
			
			// cancel the previously issued endStun task
			if (endStunTask != null) {
				endStunTask.cancel();
				endStunTask = null;
			}
			
			// enrage the shit
			this.enrage();
		}
		
		// rapid fire
		static final int RAPID_ATTACK_COOLDOWN = 30 * 20; 
		private boolean rapidFire = false;
		private int rapidFireCooldown = RAPID_ATTACK_COOLDOWN;
		
		private long counter = 0; 
		
		@Override
		public void t_() {
			super.t_();
			counter++;
			
			if (dying && counter % 2 == 0) {
				wither.damage(0.0001D);
			}
			
			this.hologramEntity.teleport(wither.getLocation().add(0, WitherLordsHandle.NAME_OFFSET, 0));
			this.dialougeEntity.teleport(wither.getLocation().add(0, WitherLordsHandle.DIABOX_OFFSET, 0));
			
			// sync the bossbar
			core.lords.bossbar.setProgress(wither.getHealth() / wither.getMaxHealth());
			
			if (!dying && core.laserState >= 2 && !stun && touchDistance() <= 2) {
				onStun();
			}
			
			if (rapidFireCooldown > 0) rapidFireCooldown--;
			
			// only execute abilities and dialog if the boss isnt stunned or in "dying animation"
			// state
			if (!welcomingFinish || dying || stun) return; 
			
			// abilities
			if (SUtil.random(0, 6) == 0) {
				rapidFire();
			}
			
			// spawn withers
			if (!rapidFire && !stun && counter % 280 == 0) {
				arcSpawnMobs();
			}
			
			// if not rapid-firing or stun/death, spawns tnt eveyr 5s
			if (!rapidFire && !stun && counter % 100 == 0) {
				witherTNT();
			}
			
			// default attack (fire skull every 5-10 ticks)
			if (!rapidFire && wither.getTarget() != null) {
				if (counter % SUtil.random(10, 15) == 0) {
					playHiss();
					// rapidly fire
					new TimedActions()
					.add(() -> shoot(wither.getTarget(), 1)) // shoot the middle-head first
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
			) {
				this.say(SUtil.randArr(RANDOM_MESSAGES), DIALOG_GAP);
			}
		}
		
		// AOE damage
		public void shadowWave() {
			new STask().run(st -> {
				if (st.counter >= 7 * 20 || wither.isDead() || stun) {
					st.kill();
					return;
				}
				if (st.counter % 6 == 0) {
					// big explosion particle
					playLargeExplosion(wither.getLocation());
					
					// this is kinda funny, because shadow wave can be activated
					// when the boss is dying. The explosion is just cosmetic at that point, though.
					if (!dying) Sputnik.hittablePlayers(wither.getLocation(), 5, 3, 5).forEach(p -> {
						User user = User.getUser(p);
						damagePlayer(user, "Shadow Wave", getDamageDealt(), false);
					});
				}
				st.counter++;
			}).startLoop(RunMode.SYNC, 1, 1);
		}
		
		public void rapidFire() {
			if (!rapidFire && rapidFireCooldown <= 0) {
				say(RAPID_FIRE_DIALOG[SUtil.random(0, RAPID_FIRE_DIALOG.length - 1)], DIALOG_GAP);
				
				rapidFire = true;
				rapidFireCooldown = RAPID_ATTACK_COOLDOWN;
				
				boolean stateBefore = this.frozen;
				this.frozen = true;
				
				new STask().run(st -> {
					if (wither.isDead() || st.counter >= 200 || stun || dying) {
						st.kill();
						this.frozen = stateBefore;
						this.rapidFire = false;
						return;
					}
					if (st.counter % 2  == 0) {
						this.playHiss();
						shoot(wither.getTarget(), (int) (Math.random() * 3d));
					}
					st.counter++;
				}).startLoop(RunMode.SYNC, 1, 1);
			}
		}
		
		@Override
		public void onTickAsync() {}
		
		@Override
		public void onDamage(SEntity sEntity, Entity damager, EntityDamageByEntityEvent e, AtomicDouble damage) {
			wither.setTarget((LivingEntity)damager);
			
			// nullify the damage if either NOT stunned or DYING
			if(!stun || dying) {
				damage.set(0);
				return;
			}
			
			double dmg = damage.get();
			boolean reachedThreshold = false;
			
			// damage threshold system, maxor requires at least 2 cycles to die
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
				// prevent player from damaging it
				this.dying = true;
				// froze it in place
				this.frozen = true;
				// turn off wither shield
				this.setShield(false);
				// play the animation
				this.stun = false; // stop the stun thingy
				this.shadowWave();
				
				// dialog for maxor
				saySequencedDialog(DEATH)
				
				// trigger phase 2 by killing Maxor
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
			if (reachedThreshold) {
				endStun();
			}
		}
		
		@Override
		public void onDeath(SEntity sEntity, Entity killed, Entity damager) {
			super.onDeath(sEntity, killed, damager);
		}
		
		// check if the entity touches the laser (distance)
		public double touchDistance() {
			Location enloc = Sputnik.setYTo(wither.getLocation(), 0);
			Location lasrLoc = Sputnik.setYTo(Sputnik.getBlockMatchPos(core.lords.laserBeaconLocation), 0);
			
			int lowerYBound = core.lords.laserBeaconLocation.getBlockY() + 2;
			int upperYBound = lowerYBound + 6; // 6 blocks gap between the conveyor belt and the laser gun
			
			// if maxor is out of bounds, return an arbitrary number (1000) to not
			// allow maxor to be damaged by the laser beam
			if (wither.getLocation().getY() < lowerYBound || wither.getLocation().getY() > upperYBound) {
				return 1000;
			}
			
			// calc the distance
			return enloc.distance(lasrLoc);
		}
		
		@Override
		public double getEntityMaxHealth() {
			return GameplaySystem.getMaxorHealth(core.lords.getHandle().getDifficulty());
		}
	
		@Override
		public double getDamageDealt() {
			return GameplaySystem.getMaxorDPS(core.lords.getHandle().getDifficulty());
		}
		
		@Override
		public double getBossDefensePercentage() {
			return GameplaySystem.getMaxorDefense(core.lords.getHandle().getDifficulty());
		}
		
		@Override
		public double getXPDropped() {
			return 0;
		}
	}
}
