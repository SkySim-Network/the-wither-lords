package vn.giakhanhvn.skysim.dungeons.bosses.witherlords;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftArmorStand;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import lombok.var;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.GoldorLever;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.GoldorTerminal;
import vn.giakhanhvn.skysim.user.User;
import vn.giakhanhvn.skysim.util.DynamicHologram;
import vn.giakhanhvn.skysim.util.STask;
import vn.giakhanhvn.skysim.util.SUtil;
import vn.giakhanhvn.skysim.util.Sputnik;
import vn.giakhanhvn.skysim.util.TimedActions;
import vn.giakhanhvn.skysim.util.STask.RunMode;

public abstract class GoldorStage {
	/* WORLD RELATED SHIT */
	// related to terminals
	List<Location> terminalBlocks = new ArrayList<>();
	List<Location> leverBlocks = new ArrayList<>();
	public boolean hasMelodyGenerated = false; // prevents 2+ melodies from generating at the same time
	
	// related to doors
	List<Location> doorHolder = new ArrayList<>(2);
	List<Location> doorLockOuter = new ArrayList<>(2);
	List<Location> doorLockInner = new ArrayList<>(2);
	List<Location> doorBlocksBound = new ArrayList<>(2);
	
	// related to devices
	List<Location> mainDeviceBound = new ArrayList<>(2);
	Location deviceTriggerBlock = null;
	
	// related to misc features
	List<Location> hangingTNT = new ArrayList<>();
	
	// state flags
	List<GoldorTerminal> terminals = new ArrayList<>();
	List<GoldorLever> levers = new ArrayList<>();
	
	// how much devices have been completed
	public int devicesAndTerminalsCompleted = 0;
	
	// the gate will "unlock" once all devices + terminals are completed
	boolean gateLocked = true;
	// the gate can be destroyed either by boom TNT or wait 5s after all completions
	boolean gateDestroyed = false;
	// if the gate has opened
	boolean gateOpened = false;
	
	public GoldorStage() { }
	
	// goldor instance
	public GoldorPhase goldorPhase;
	
	// the location that superboom can be deployed to break the door holder
	public Location doorCenterCollision;
	
	/**
	 * Will be called DURING Goldor initialization
	 * @param phaseCore
	 */
	public void preInit(GoldorPhase phaseCore) {
		// core
		this.goldorPhase = phaseCore;
		// fill in so the world wont fall apart
		leverBlocks.forEach(b -> {
			b.getBlock().setType(Material.LEVER);
			b.getBlock().setData((byte)6);
		});
		// door
		for (Location loc : doorBlocksBound) {
			loc.getBlock().setType(Material.COBBLESTONE);
		}
		terminalBlocks.forEach(b -> {
			// fill the command block that was removed
			b.getBlock().setType(Material.COMMAND);
		});
		// calculate the collision location
		// by using the lock's outer as reference points
		if (doorLockOuter.size() >= 2) {
			this.doorCenterCollision = Sputnik.setYTo(
				doorLockOuter.get(0).mid(doorLockOuter.get(1)),
			0);
		}
	}
	
	public void explosion(Location loc) {
		// if the TNT explodes within 5 blocks radius of the collison box
		if (Sputnik.setYTo(loc, 0).distanceSquared(this.doorCenterCollision) <= 25) {
			// destroy the upper part (the holder)
			// allowing the user to progress instantly after the lock is removed
			destroyGateHolder();
		}
	}
	
	/**
	 * WIll be called by .start()
	 */
	private void init() {
		// initialize the thingy
		terminalBlocks.forEach(b -> {
			// thing
			terminals.add(new GoldorTerminal(this, b.getBlock()));
		});
		
		leverBlocks.forEach(b -> {
			// init system
			levers.add(new GoldorLever(this, b.getBlock()));
		});
		
		// setup the TNTs
		Collections.shuffle(hangingTNT);
		hangingTNT.forEach(b -> {
			List<Block> basket = Sputnik.cuboid(
				b.clone().add(-1, 1, 1), // upper bound
				b.clone().add(1, -1, -1)  // lower bound
			);
			// set the TNT
			basket.forEach(block -> block.setType(Material.TNT));
		});
	}
	
	public void onCompletion(User who, String message) {
		// increment
		devicesAndTerminalsCompleted++;
		
		// terminals + 2 levers + 1 device
		int total = terminalBlocks.size() + leverBlocks.size() + 1;
		
		// failsafe
		if (devicesAndTerminalsCompleted > total) return;
		
		goldorPhase.lords.getHandle().sendSubtitleWithChat(
			who.getDisplayName() + " &a" + message + " (&c" + devicesAndTerminalsCompleted + "&a/" + total + ")",
			0, 50, 0
		);
		
		// if players completed all terms + dev + levers, open the lock
		if (devicesAndTerminalsCompleted == total) {
			// destroy locks
			destroyLock();
		}
	}
	
	public void completeDevice(User u) {
		onCompletion(u, "completed a device");
	}
	
	public abstract void start();
	public abstract void onStageCompletion();
	
	public double gateTornDownSpeed() {
		return -0.45;
	}
	
	/**
	 * Open a stage's gate, successfully invoking this
	 * triggers {@link GoldorStage#onStageCompletion()}
	 * 
	 * @param core - true if the stage is connected to the Core
	 */
	public void openGate(boolean core) {
		if (gateOpened) return;
		
		// set the gate open status
		this.gateOpened = true;
		
		// the systems
		var gateSegments = Sputnik.cuboid(doorBlocksBound.get(0), doorBlocksBound.get(1));
		World world = doorBlocksBound.get(0).getWorld();
		
		// play the animation
		List<ArmorStand> armorStandEntities = new ArrayList<>();
		for (Block block : gateSegments) {
			Material material = block.getType();
			byte data = block.getData();
			
			// spawn a falling block that rides an armorstand
			ArmorStand as = (ArmorStand) world.spawnEntity(
				block.getLocation().add(0.5, -2, 0.5), EntityType.ARMOR_STAND
			);
			as.setGravity(false);
			as.setVisible(false);
			
			@SuppressWarnings("deprecation")
			FallingBlock fbl = world.spawnFallingBlock(
				block.getLocation(), material, data
			);
			fbl.setVelocity(new Vector(0,0,0));
			fbl.setHurtEntities(false);
			fbl.setDropItem(false);
			
			// rides
			as.setPassenger(fbl);
			
			armorStandEntities.add(as);
			block.setType(Material.AIR);
		}
		
		// translate down every 0.45 blocks per tick and destroying it after it submerges
		new STask().run(s -> {
			if (goldorPhase.lords.dungeonEnded() || s.counter >= 200) {
				s.kill();
				return;
			}
			
			for (ArmorStand block : armorStandEntities) {
				CraftArmorStand cas = (CraftArmorStand) block;
				
				// move the shit down at designed speed
				Location lc = block.getLocation().add(0, gateTornDownSpeed(), 0);
				cas.getHandle().setPositionRotation(lc.getX(), lc.getY(), lc.getZ(), 0, 0);
				
				Entity falling = block.getPassenger();
				if (falling == null) {
					block.remove();
					continue;
				}
				
				// get 1 block above
				Material blockAbove = falling
					.getLocation().add(0, 1, 0)
					.getBlock()
				.getType();
				
				// if the block above is neither air nor
				// barrier, despawn the thing
				if (blockAbove != Material.AIR &&
					blockAbove != Material.BARRIER
				) {
					// despawn
					block.remove();
					block.getPassenger().remove();
				}
			}
			s.counter++;
		}).startLoop(RunMode.SYNC, 0, 0);
		
		// on open CORE GATE ONLY!
		if (core) {
			goldorPhase.lords.getHandle().sendSubtitleWithChat(
				"&aThe Core Entrance is opening!",
				0, 50, 0
			);
		}
		
		// trigger the thing
		this.onStageCompletion();
	};
	
	/**
	 * A Gate has 2 layers of lock, one being the "holder" and the other one is the
	 * "hinges", successfully invoking this method will remove the "holder"
	 * lock
	 * <br><br>
	 * If both the gate holder and hinges are removed, 
	 * {@link GoldorStage#openGate(boolean)} is invoked
	 * <br><br>
	 * To destroy the "hinges" {@link GoldorStage#destroyLock()}
	 *
	 * @apiNote This method can be called by the System (after 5s countdown) 
	 * <b>OR</b> Superboom TNTs implementation
	 */
	public void destroyGateHolder() {
		if (gateDestroyed) return;
		this.gateDestroyed = true;
		
		// break the shit out of it
		var holder = Sputnik.cuboid(
			doorHolder.get(0), doorHolder.get(1)
		);
		
		holder.forEach(b -> {
			if (b.getType() == Material.SMOOTH_BRICK || b.getType() == Material.SMOOTH_STAIRS) {
				b.setType(Material.BARRIER);
			}
		});
		
		// thingy
		goldorPhase.lords.getHandle().sendSubtitleWithChat(
			"&aThe gate has been destroyed!",
			0, 50, 0
		);
		
		// do state update
		if (!gateLocked && gateDestroyed) {
			openGate(false);
		}
	}
	
	/**
	 * A Gate has 2 layers of lock, one being the "holder" and the other one is the
	 * "hinges", successfully invoking this method will remove the "holder"
	 * lock
	 * <br><br>
	 * If both the gate holder and hinges are removed, 
	 * {@link GoldorStage#openGate(boolean)} is invoked
	 * <br><br>
	 * To destroy the "gate holder" {@link GoldorStage#destroyGateHolder()}
	 * 
	 * @apiNote This method is ONLY invoked AFTER the current stage's terminals and devices are completed
	 */
	public void destroyLock() {
		this.gateLocked = false; // unlock the "hinges"
		
		// get the lock
		var gateLock =  Sputnik.cuboid(
			doorLockInner.get(0), doorLockInner.get(1) // the inner blocks
		);
		
		gateLock.addAll(Sputnik.cuboid(
			doorLockOuter.get(0), doorLockOuter.get(1) // the outer blocks
		));
		
		// remove blocks
		gateLock.forEach(block -> {
			block.setType(Material.AIR);
		});
		
		// explosion animation
		doorLockInner.get(0).playEffect(Effect.EXPLOSION_HUGE, 0);
		doorLockInner.get(1).playEffect(Effect.EXPLOSION_HUGE, 0);
		
		if (!gateDestroyed) {
			// open after 5s
			goldorPhase.lords.getHandle().sendSubtitleWithChat(
				"&aThe gate will open in 5 seconds!",
				0, 50, 0
			);
			SUtil.delay(() -> {
				// force open by "exploding" it
				this.destroyGateHolder();
			}, 100);
		} else {
			// ONLY if the gate HOLDER is DESTROYED before this (lock open)
			// we send this subtitle
			goldorPhase.lords.getHandle().sendSubtitleWithChat(
				"&aThe gate has been opened!",
				0, 50, 0
			);
		}
		
		// do state update
		if (!gateLocked && gateDestroyed) {
			openGate(false);
		}
	};
	
	// first stage - simon says
	public static class SimonSaysStage extends GoldorStage {
		// for orders
		List<Integer> simonSaysIndexes = new ArrayList<>();
		// the core blocks
		List<Block> simonSaysPanels;
		
		// the start flags
		public boolean simonSaysStarted;
		
		// if the user can click or not
		boolean allowClicking = false;
		// current order of which the user clicked the button
		int currentIndex = 0;
		// the round index (max: 5)
		int roundIndex = 1;
		
		// dynamic hologram
		DynamicHologram hologram;
		
		@Override
		public void start() {
			// call the initialization process
			super.init();
			
			// cuboid
			simonSaysPanels = Sputnik.cuboid(
				mainDeviceBound.get(0), 
				mainDeviceBound.get(1)
			);
			
			// init
			clearBoard();
			
			// set the button
			Block button = deviceTriggerBlock.getBlock();
			button.setType(Material.STONE_BUTTON);
			button.setData((byte)1);
			
			hologram = new DynamicHologram(Sputnik.getBlockMatchPos(deviceTriggerBlock),
				2, 0.45, 50
			);
			
			// on click button
			button.onBlockClick(e -> {
				User user = User.getUser(e.getPlayer());
				if (user.isAGhost()) return;
				// activate device
				startSimonSays();
			});
			
			// initialize simon says
			this.reinitializeSimonSays();
			hologram.setLine(1, "&cInactive");
			hologram.setLine(0, "&cDevice");
			hologram.update();
		}
		
		public void reinitializeSimonSays() {
			// reset variables
			this.roundIndex = 1;
			this.currentIndex = 0;
			
			// clear and repopulate
			simonSaysIndexes.clear();
			for (int i = 0; i < simonSaysPanels.size(); i++) {
				simonSaysIndexes.add(i);
			}
			Collections.shuffle(simonSaysIndexes);
		}
		
		public void startSimonSays() {
			if (simonSaysStarted) return;
			// set the flag to indicate that the thing started
			simonSaysStarted = true;
			// replay round #1 instructions
			replayInstructions().add(() -> {
				spawnButtons();
			}).run();
		}
		
		public void failSimonSays() {
			// allow user to replay
			this.simonSaysStarted = false;
			// reinitialize the internal variables
			this.reinitializeSimonSays();
			// delete the thing
			this.removeButtons();
		}
		
		public void onClickButton(User user, int buttonIndex) {
			// if the state doesnt allow for clicking, return
			if (!allowClicking) return;
			
			// if the user clicked the wrong button, fail the entire puzzle
			if (simonSaysIndexes.get(currentIndex) != buttonIndex) {
				user.send("&cDevice failed. Try again!");
				failSimonSays();
				return;
			}
			
			// for each click, increment the currentIndex by 1
			currentIndex++;
			
			// if the user exceeded the round steps (round 1 = 1 click, 2 = 2 click, etc)
			if (currentIndex >= roundIndex) {
				// user wins round, reset index
				currentIndex = 0;
				// next round
				roundIndex++;
				
				// reset the board
				removeButtons();
				
				// if exceeded 5 rounds, complete the device
				if (roundIndex > GoldorPhase.SIMON_SAYS_PHASE) {
					completeDevice(user);
					// actthing
					hologram.setLine(1, "&aActive");
					hologram.setLine(0, "&aDevice");
					hologram.update();
					return;
				}
				
				// replay new round instruction
				replayInstructions().add(() -> {
					spawnButtons();
				}).run();
			}
		}
		
		public void spawnButtons() {
			for (int i = 0; i < simonSaysPanels.size(); i++) {
				// button will spawn/despawn on the east-facing block of
				// the main panel
				Block block = simonSaysPanels.get(i).getRelative(BlockFace.EAST);
				// to make it face the player, data is 1
				block.setType(Material.STONE_BUTTON);
				block.setData((byte) 1);
				// set the listener
				final int index = i;
				block.onBlockClick(e -> {
					e.setCancelled(true);
					// thiong
					User user = User.getUser(e.getPlayer());
					if(user.isAGhost()) return; 
					// trigger
					onClickButton(user, index);
				});
			}
			// allow clicking the buttons
			allowClicking = true;
		}
		
		// disallow clicking and unhook listeners
		public void removeButtons() {
			// disallow clicking buttons
			allowClicking = false;
			// remove each of them fixed
			for (int i = 0; i < simonSaysPanels.size(); i++) {
				// button will spawn/despawn on the east-facing block of
				// the main panel
				Block block = simonSaysPanels.get(i).getRelative(BlockFace.EAST);
				// destroy
				block.getLocation().getBlock().setType(Material.AIR);
				block.onBlockClick(null);
			}
		}
		
		// tell the players where to click by displaying the
		// orders (by setting them to sea lantern one by one in 1s interval) 
		public TimedActions replayInstructions() {
			TimedActions replayActions = new TimedActions();
			
			// show the player where to click
			// the limit of this is the current ss round
			for (int ix = 0; ix < roundIndex; ix++) {
				int index = simonSaysIndexes.get(ix);
				// add to the delayed shit
				replayActions.add(() -> {
				for (int i = 0; i < simonSaysPanels.size(); i++) {
					Block block = simonSaysPanels.get(i);
					// if == index, set to sea lantern
					block.setType(i == index ? Material.SEA_LANTERN : Material.OBSIDIAN);
				}}).wait(15);
			}
			
			// clear the sea lantern after animation
			replayActions.add(() -> {
				// clear board;
				this.clearBoard();
			});
			
			return replayActions;
		}
		
		// clear the board (set all to obsidian)
		public void clearBoard() {
			for (int i = 0; i < simonSaysPanels.size(); i++) {
				Block block = simonSaysPanels.get(i);
				block.setType(Material.OBSIDIAN);
			}
		}

		@Override
		public void onStageCompletion() {
			goldorPhase.startStage(2);
		}
	}
	
	// second stage - redstone lights
	public static class LightBoardStage extends GoldorStage {
		// the core blocks
		List<Block> lightBulbs;
		boolean completed;
		
		// dynamic hologram
		DynamicHologram hologram;
		
		@Override
		public void start() {
			// call the initialization process
			super.init();
			
			// cuboid
			lightBulbs = Sputnik.cuboid(
				mainDeviceBound.get(0).add(0, 0, -2), 
				mainDeviceBound.get(1).add(0, 0, -2)
			);
			
			// init
			hologram = new DynamicHologram(Sputnik.getBlockMatchPos(deviceTriggerBlock),
				2, 0.45, 50
			);
			hologram.setLine(1, "&cInactive");
			hologram.setLine(0, "&cDevice");
			hologram.update();
			
			// check constantly every 1s
			new STask().run(s -> {
				if (goldorPhase.lords.dungeonEnded() || completed) {
					s.kill();
					return;
				}
				
				// check the lamps for ON status, if all lamps on, choose the nearest player
				int turnedOn = 0;
				for (Block block : lightBulbs) {
					Block b = block.getLocation().getBlock();
					if (b.getType() == Material.REDSTONE_LAMP_ON) {
						turnedOn++;
					}
				}
				
				// if on >= the size = active
				if (turnedOn >= lightBulbs.size()) {
					var players = Sputnik.hittablePlayers(deviceTriggerBlock, 5, 5, 5);
					Player finisher = null;
					// select a player that will be responsible
					if (players.size() == 0) {
						// if the thing cant be chosen
						// get a random person
						finisher = goldorPhase.getRandomPlayer().getInternal().toBukkitPlayer();
					} else {
						// get the shit
						finisher = players.get(0);
					}
					// complete
					complete(User.getUser(finisher));
				}
				
			}).startLoop(RunMode.SYNC, 1, 1);
		}
		
		// complete the device, crediting the nearest player
		public void complete(User user) {
			if (this.completed) return;
			this.completed = true;
			
			hologram.setLine(1, "&aActive");
			hologram.setLine(0, "&aDevice");
			hologram.update();
			
			completeDevice(user);
		}

		@Override
		public void onStageCompletion() {
			goldorPhase.startStage(3);
		}
		
	}
	
	// third stage - arrows
	public static class ArrowsStage extends GoldorStage {
		static final int NN = 0;
		static final int BG = 1;
		static final int ED = 2;
		
		static final int UP = 11;
		static final int RG = 5;
		static final int LF = 9;
		static final int DN = 7;
		
		// 0 = NONE
		// 1 = BEGIN
		// 2 = END
		// 3+ onwards (state of rotation = num - 4)
		static final int[][] EXPECTED_STAGES = {
			{ // stripes
				1, 5, 5, 5, 2,
				0, 0, 0, 0, 0,
				1, 5, 5, 5, 2,
				0, 0, 0, 0, 0,
				1, 5, 5, 5, 2
			},
			{ // snake 1S
				DN, LF, LF, 00, ED,
				DN, 00, UP, 00, UP,
				DN, 00, BG, 00, UP,
				DN, 00, DN, 00, UP,
				ED, 00, RG, RG, UP,
			},
			{ // swirl
				RG, RG, RG, RG, DN,
				UP, 00, 00, 00, DN,
				UP, 00, ED, 00, DN,
				UP, 00, UP, 00, DN,
				BG, 00, UP, LF, LF
			},
			{ // snake 2
				BG, RG, RG, RG, DN,
				00, 00, 00, 00, DN,
				DN, LF, LF, LF, LF,
				DN, 00, 00, 00, 00,
				RG, RG, RG, RG, ED,
			},
			{ // curly
				00, 00, RG, RG, DN,
				00, 00, UP, 00, DN,
				BG, 00, UP, 00, ED,
				DN, 00, UP, 00, 00,
				RG, RG, UP, 00, 00,
			},
			{ // willy
				00, 00, 00, ED, 00,
				00, RG, DN, UP, 00,
				00, UP, DN, UP, 00,
				RG, UP, DN, UP, 00,
				BG, 00, RG, UP, 00,
			}
		};
		
		boolean stageStarted = false;
		boolean deviceCompleted = false;
		// all "nodes"
		List<Block> deviceBounds = new ArrayList<>();
		// keep track of the last player touched the board
		public Player lastInteracted = null;
		// expected lit nodes
		private int expectedLitNodes = 0;
		
		// spawn the board
		public void spawnBoard() {
			// select the stage
			int[] selectedStage = SUtil.randArr(EXPECTED_STAGES);
			// calculate some shit
			for (int i : selectedStage) {
				if (i >= 3) expectedLitNodes++;
			}
			
			// the world objects of the terminal (the board)
			var rawBounds = Sputnik.cuboid(
				mainDeviceBound.get(0), mainDeviceBound.get(1)
			);
			Collections.reverse(rawBounds);
			
			// black magic
			// not actually, the Sputnik.cuboid is upside down-inside out
			// so that we need to reverse iterating (to get the correct order from
			// EXPECTED_STAGE(s))
			for (int i = 0; i < 5; i++) {
				for (int j = i * 5 + 5 - 1; j >= i * 5; j--) {
					deviceBounds.add(rawBounds.get(j));
				}
			}
			
			deviceBounds.forEach(b -> {
				b.setType(Material.STAINED_CLAY);
				b.setData((byte)11);
			});
			
			for (int i = 0; i < selectedStage.length; i++) {
				if (selectedStage[i] == 0) continue;
				int state = selectedStage[i];
				Block origin = deviceBounds.get(i);
				Block toSpawn = origin.getLocation().add(-1,0,0).getBlock();
				// spawn iframe
				ItemFrame itemFrame = origin.getWorld().spawn(toSpawn.getLocation(), ItemFrame.class);
				// internal shit
				if (state < 3) { // begin (green wool), end (red wool)
					itemFrame.setItem(
						SUtil.getSingleLoreStack(
							state == ED ? ChatColor.RED + "End" : ChatColor.GREEN + "Start" ,
							Material.WOOL, 
							(short) (state == ED ? 14 : 5), // 14 = red; 5 = lime
							1, ""
						)
					);
					// decorations
					origin.setType(Material.EMERALD_BLOCK);
				} else {
					itemFrame.setItem(new ItemStack(Material.ARROW));
					itemFrame.setRotation(SUtil.randArr(Rotation.values()));
					
					// initial check (there's a chance, though impossibly low, that the
					// puzzle spawns solved, no need intervention.) And yes, that
					// edge case is covered by this line below
					checkStateOfArrow(origin, itemFrame, state);
					
					// this is a proprietary API
					itemFrame.onClickByPlayer(e -> {
						if (deviceCompleted) return; // if device is completed, stop players from rot
						if (User.getUser(e.getPlayer()).isAGhost()) return;
						
						// rotate and credit the rotator
						itemFrame.setRotation(itemFrame.getRotation().rotateClockwise());
						this.lastInteracted = e.getPlayer();
						
						// check for state
						checkStateOfArrow(origin, itemFrame, state);
					});
				}
			}
		}
		
		void checkStateOfArrow(Block origin, ItemFrame itemFrame, int expected) {
			// check for state
			Block backBlock = origin.getLocation().getBlock();
			// if matches (the NONE is actually arrow titled 45 deg)
			// so instead of -3, we -4 to compensate
			if (itemFrame.getRotation().ordinal() == expected - 4) {
				// if aligned, set to sea lantern and then add 1 to lit Nodes
				backBlock.setType(Material.SEA_LANTERN);
				// only if one aligned, do an update
				int litNodes = 0;
				for (Block block : deviceBounds) {
					if (block.getLocation().getBlock().getType() == Material.SEA_LANTERN) {
						litNodes++;
					}
				}
				if (litNodes >= expectedLitNodes) {
					this.complete(lastInteracted);
					return;
				}
			} else {
				// otherwise, set to clay
				backBlock.setType(Material.STAINED_CLAY);
				backBlock.setData((byte)11);
			}
		}
		
		public void complete(Player player) {
			if (!stageStarted) return;
			
			// VERY VERY RARE
			if (player == null) {
				// select a random alive player
				// because this puzzle spawns solved already
				// , that's crazy!
				player = goldorPhase.getRandomPlayer()
				.getInternal().toBukkitPlayer();
			}
			
			// mark as completed
			this.deviceCompleted = true;
			// calls
			completeDevice(User.getUser(player));
		}
 		
		@Override
		public void preInit(GoldorPhase phaseCore) {
			super.preInit(phaseCore);
			// allow for pre-devices
			this.spawnBoard();
		}
		
		@Override
		public void start() {
			super.init();
			this.stageStarted = true;
		}

		@Override
		public void onStageCompletion() {
			goldorPhase.startStage(4);
		}
		
	}
	
	// last (4th) stage - targets / core
	public static class TargetsStage extends GoldorStage {
		@SuppressWarnings("deprecation")
		@Override
		public void preInit(GoldorPhase phaseCore) {
			super.preInit(phaseCore);
			// s4 is core so the door will be golden
			for (Location loc : doorBlocksBound) {
				loc.getBlock().setType(Material.GOLD_BLOCK);
			}
			
			mainDeviceBound.get(0).getBlock().setTypeIdAndData(Material.STAINED_CLAY.getId(), (byte) 11, false);
			mainDeviceBound.get(1).getBlock().setTypeIdAndData(Material.STAINED_CLAY.getId(), (byte) 11, false);
		}
		
		// the bounds of the thing
		List<Location> deviceBounds = new ArrayList<>();
		
		// flags
		boolean canHit = false;
		boolean deviceStarted = false;
		boolean deviceCompleted = false;
		
		// orders in which the target must be shot
		List<Integer> targetOrders = new ArrayList<>();
		int currentTargetIndex = -1; // begins at -1 because first thing adds 1 to it
		
		// others
		DynamicHologram hologram;
		
		@Override
		public void start() {
			super.init();
			
			// begin
			var rawBounds = Sputnik.cuboid(
				mainDeviceBound.get(0), mainDeviceBound.get(1)
			);
			
			rawBounds.forEach(block -> {
				if (block.getType() == Material.STAINED_CLAY) {
					deviceBounds.add(block.getLocation());
				}
			});
			
			// init
			hologram = new DynamicHologram(Sputnik.getBlockMatchPos(deviceTriggerBlock),
				2, 0.35, 50
			);
			hologram.setLine(1, "&cInactive");
			hologram.setLine(0, "&cDevice");
			hologram.update();
			
			// constantly check for players on top of the plate
			deviceTriggerBlock.getBlock().setType(Material.GOLD_PLATE);
			new STask().run(s -> {
				// if device completed, stop checking
				if (goldorPhase.lords.dungeonEnded() || deviceCompleted) {
					// no hit anymore
					canHit = false;
					// stop
					s.kill();
					return;
				}
				
				Collection<Player> pressurePlate = Sputnik.hittablePlayers(Sputnik.getBlockMatchPos(deviceTriggerBlock), .2, 1, .2);
				// only allow players to hit if theres a player on the plate
				boolean canHit = pressurePlate.size() > 0;
				
				// if player steps out
				if (!canHit && this.canHit) {
					this.stopDevice();
				}
				
				// when the user steps on pressureplate and device is not triggered yet,
				// start the device handler
				if (canHit && !this.canHit) {
					this.deviceStart();
				}
				
				// sync
				this.canHit = canHit;
			}).startLoop(RunMode.SYNC, 10, 10);
		}
		
		public void deviceStart() {
			if (deviceStarted) return;
			
			deviceStarted = true;
			targetOrders.clear();
			for (int i = 0; i < deviceBounds.size(); i++) {
				targetOrders.add(i);
			}
			Collections.shuffle(targetOrders);
			next(null);
		}
		
		@SuppressWarnings("deprecation")
		public void stopDevice() {
			if (!deviceStarted) return;
			
			this.deviceStarted = false;
			deviceBounds.forEach(loc -> {
				loc.getBlock().setTypeIdAndData(Material.STAINED_CLAY.getId(), (byte) 11, false);
				// unhook listeners
				loc.getBlock().onBlockHitByArrow(null);
			});
			
			// reset everything
			currentTargetIndex = -1;
		}
		
		@SuppressWarnings("deprecation")
		public void next(User user) {
			// only allow hitting targets if there's a player in pressureplate and
			// device NOT completed
			if (deviceCompleted || (!canHit && user != null)) return;
			
			currentTargetIndex++;
			if (currentTargetIndex >= deviceBounds.size()) {
				// FINiSHED
				complete(user);
				return;
			}
			
			for (int i = 0; i < deviceBounds.size(); i++) {
				Block target = deviceBounds.get(i).getBlock();
				// if the target, set to emerald and hook a listener
				if (i == targetOrders.get(currentTargetIndex)) {
					target.setType(Material.EMERALD_BLOCK);
					// if the target block was hit by an arrow, next
					target.onBlockHitByArrow(e -> {
						ProjectileSource source = e.getArrow().getShooter();
						if (!(source instanceof Player)) return;
						User hitter = User.getUser((Player)source);
						// ignore ghost projekctiles
						if (hitter.isAGhost()) return;
						// trigger
						next(hitter);
					});
					continue;
				}
				// if not, unhook listener
				target.setTypeIdAndData(Material.STAINED_CLAY.getId(), (byte) 11, false);
				target.onBlockHitByArrow(null);
			}
		}
		
		public void complete(User user) {
			if (this.deviceCompleted) return;
			this.deviceCompleted = true;
			
			// stop the device
			stopDevice();
			
			hologram.setLine(1, "&aActive");
			hologram.setLine(0, "&aDevice");
			hologram.update();
			
			// call outer layers
			completeDevice(user);
		}
		
		// phase 4 doesn't have lock & holder, only lock
		@Override
		public void destroyLock() {
			openGate(true);
		}
		
		// ignore this
		@Override
		public void explosion(Location loc) { }
		
		@Override
		public double gateTornDownSpeed() {
			return -0.2;
		}
		
		@Override
		public void onStageCompletion() {
			// the Y pos that he will fly to
			double alignY = goldorPhase.lords.finalGoldorLocation.getBlockY();
			// calculate the 
			Location boundaryPoint = Sputnik.setYTo(
				doorBlocksBound.get(0),
				alignY
			).mid(Sputnik.setYTo(
				doorBlocksBound.get(1),
				alignY
			));
			
			// set the flag so that he wont do the instakill anymore
			goldorPhase.finishedLastTerminals = true;
			
			new STask().run(s -> {
				int playersInCore = Sputnik.hittablePlayers(boundaryPoint, 2, 10, 2).size();
				// if a player passed the door gate OR a minute has passed
				// goldor will run straight into the core after 3s
				if (playersInCore > 0 || s.counter >= 60 * 20) {
					s.kill();
					// 0.5s before it runs
					SUtil.delay(() -> goldorPhase.moveToCore(boundaryPoint), 10);
					return;
				}
				s.counter++;
			}).startLoop(RunMode.SYNC, 0, 0);
		}
	}
}
