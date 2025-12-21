package vn.giakhanhvn.skysim.dungeons.bosses.witherlords;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;

import vn.giakhanhvn.skysim.dungeons.features.watcher.GlobalBossBar;
import vn.giakhanhvn.skysim.dungeons.systems.Dungeon;
import vn.giakhanhvn.skysim.dungeons.systems.IDungeonBoss;
import vn.giakhanhvn.skysim.dungeons.systems.SSchematic.BasicBlock;
import vn.giakhanhvn.skysim.entity.SEntity;
import vn.giakhanhvn.skysim.entity.SEntityType;
import vn.giakhanhvn.skysim.user.User;
import vn.giakhanhvn.skysim.util.SUtil;
import vn.giakhanhvn.skysim.util.Sputnik;

public class WitherLordsHandle extends IDungeonBoss {
	static final int WITHER_CAP = 24;
	static final double NAME_OFFSET = 3.5D;
	static final double DIABOX_OFFSET = NAME_OFFSET + 0.3D;
	static final String ENRAGED_TEXTURE = "ecc58cb55b1a11e6d88c2d4d1a6366c23887dee26304bda412c4a51825f199";
	
	// necessary locations
	Location playersSpawnPoint;
	Location rewardsRoomLocation;
	Location chestsLocation;
	
	// phase 1
	Location maxorSpawnPoint; // self explanatory
	
	Location conveyorBeltBegin; // the conveyor belt starting position
	Location conveyorBeltEnd; // the conveyor belt ending position
	
	Location laserBeaconLocation; // the beacon (glass block) location
	List<Location> energyCrystals = new ArrayList<>(); // energy crystals to be collected
	
	// TODO i should've used an array, this is so bad
	Location rightSideBegin; // the energy wire (right side) begin
	Location rightSideJoint; // the energy wire (right side) middle joint
	Location rightSideEnd; // the energy wire (right side) end
	
	Location leftSideBegin; // the energy wire (left side) begin
	Location leftSideFirstJoint; // the first joint
	Location leftSideSecondJoint; // same thing
	Location leftSideEnd; // the energy wire (left side) end
	
	Location leftPad; // the position of the crystal altar -- left (to be placed)
	Location rightPad; // the position of the crystal altar -- right (to be placed)
	
	Location trapDoorPos1; // trapdoor to phase 2 bounding box
	Location trapDoorPos2;
	
	// phase 2
	Location stormSpawnPoint; // ofc
	Location[] stormCirclePath = new Location[3]; // the path that storm cicrles around phase 2
	
	Location greenPad; // the green activator pad
	Location greenCrusher; // the green crusher base (the uppermost)
	
	Location yellowPad; // same thing but yellow
	Location yellowCrusher; // wyd
	
	Location purplePad; // same thing
	Location purpleCrusher; // ...
	
	Location goldorDropper; // the red pillar that drops players down to goldor
	
	// phase 3
	Location goldorSpawnPoint; // self-explanatory
	// the internal instances of the stages
	GoldorStage[] goldorStages = {
		null, // empty
		new GoldorStage.SimonSaysStage(), // first stage
		new GoldorStage.LightBoardStage(), // second stage
		new GoldorStage.ArrowsStage(), // third stage
		new GoldorStage.TargetsStage() // final stage
	}; // they need to be initialized so that the locations can be assigned during
	// evaluation
	
	// the path that goldor moves around the factory
	Location[] goldorPath = new Location[5]; // index 0 is always null
	
	Location finalGoldorLocation; // his final location (above the core trapdoor)
	
	// phase 4 (final phase of Hypixel's f7)
	List<Location> necronTrapdoor = new ArrayList<>();
	Location necronSpawnPoint;
	// locations of the platforms in necron phase
	@SuppressWarnings("unchecked")
	List<Location>[] necronPlatforms = new ArrayList[6];
	// the 6 pillars that connected to the 6 platforms
	Location[] necronTowers = new Location[6]; // the first tower doesnt exist, so always null
	Location[] necronPositions = new Location[4]; // the path that necron may tp to
	// the places that the players will hover above before necron throws them into lava
	List<Location> necronHoverLocations = new ArrayList<>();
	Location necronEntrancePlatform;
	// end of necron
	
	// spawnpoints for wither miners (p1 + p2)
	@SuppressWarnings("unchecked")
	List<Location>[] witherMinerSpawnPoints = new List[2];
	
	// other systems-related variables
	public WitherLordPhase currentPhase;
	
	public GlobalBossBar bossbar;
	public int activeWitherSkeletons = 0;
	List<LivingEntity> spawnedSkeletons = new ArrayList<>();
	
	public WitherLordsHandle(Dungeon dungeon) {
		super(dungeon);
		// initialize the platform data holder
		for (int i = 0; i < necronPlatforms.length; i++) {
			necronPlatforms[i] = new ArrayList<>();
		}
	}

	@Override
	public Location getSpawnLocation() {
		return Sputnik.setYawOf(playersSpawnPoint, 180);
	}

	@Override
	public Location getRewardChestsOffsets() {
		return chestsLocation;
	}

	@Override
	public String bossName() {
		return "The Wither Lords";
	}

	@Override
	public int floor() {
		return 2;
	}

	@Override
	public String SDKVersionId() {
		return "NULL";
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void prepare(Object... argumentObjects) {
		int blockId;
		
		// loop through all blocks
		// CAUTION! They're re-usable objects, so DO NOT modify anything inside, make a copy first!!!
		for (Map.Entry<Location, BasicBlock> eval : this.getBossRoomData().entrySet()) {
			blockId = eval.getValue().id();
			
			// discarding non-marker blocks
			if (blockId != Material.SPONGE.getId() && blockId != Material.MONSTER_EGGS.getId() && blockId != Material.COMMAND.getId()) {
				continue;
			}
			
			Location loc = eval.getKey().clone(); // clone to keep the eval.getKey() clean
			BasicBlock base = eval.getValue();
			
			// move to the same world as this instance
			loc.setWorld(getHandle().getWorld());
			
			// scan for mob spawnpoints
			if (blockId == Material.SPONGE.getId()) {
				// DRY SPONGES (ID = 0), PHASE 1 (MAXOR)
				// WET SPONGES (ID = 1), PHASE 2 (STORM)
				int indexId = (int)eval.getValue().data();
				if (witherMinerSpawnPoints[indexId] == null) {
					witherMinerSpawnPoints[indexId] = new ArrayList<>();
				}
				witherMinerSpawnPoints[indexId].add(Sputnik.getBlockMatchPos(loc));
				// set to air without physics update
				loc.getBlock().setTypeIdAndData(0, (byte) 0, false);
				continue;
			}
			
			// scan the necron phase for MONSTER_EGG (infested stone bricks),
			// they're used instead of CMD BLOCKS for platform markers
			if (blockId == Material.MONSTER_EGGS.getId()) {
				// set to air without physics update
				loc.getBlock().setTypeIdAndData(0, (byte) 0, false);
				// add the lower block to the list
				necronPlatforms[(int)eval.getValue().data()].add(loc.add(0, -1, 0));
				continue;
			}
			
			if (blockId != Material.COMMAND.getId()) continue;
			
			// Read Data Blocks (MARKERS)
			String rawData = base.hasNBTData() ? base.getNbtData().getString("Command") : null;
			if (rawData == null || !rawData.contains("::")) continue;
			
			String metadata = rawData.replace("system::", "").replace("sys::", "");
			String[] dataSplit = metadata.split(":");
			
			String enumData = dataSplit[0];
			String additionData = dataSplit.length > 1 ? dataSplit[1] : null;
			
			this.processData(loc, enumData, additionData);
			loc.getBlock().setType(Material.AIR);
		}
		
		leftPad.getBlock().setType(Material.STONE_PLATE);
		rightPad.getBlock().setType(Material.STONE_PLATE);
		
		this.setReady(true);
	}
	
	private void processData(Location loc, String enumData, String additional) {
		// PLAYER SPAWN
		if (enumData.equals("spawn")) {
			playersSpawnPoint = Sputnik.getBlockMatchPos(loc);
			return;
		}
		
		// REWARDS ROOM
		if (enumData.equals("end_player_teleport")) {
			rewardsRoomLocation = Sputnik.setYawOf(Sputnik.getBlockMatchPos(loc), 180);
			return;
		}
		
		// CHESTS LOCATION
		if (enumData.equals("reward_chests")) {
			chestsLocation = Sputnik.getBlockMatchPos(loc).add(0, -1, 0);
			return;
		}
		
		// BOSS SPAWNS
		if (enumData.equals("boss_spawn")) {
			if (additional.equals("maxor")) {
				maxorSpawnPoint = Sputnik.getBlockMatchPos(loc);
				return;
			}
			if (additional.equals("storm")) {
				stormSpawnPoint = Sputnik.getBlockMatchPos(loc);
				return;
			}
			if (additional.equals("goldor")) {
				goldorSpawnPoint = Sputnik.getBlockMatchPos(loc);
				return;
			}
			if (additional.equals("necron")) {
				necronSpawnPoint = Sputnik.getBlockMatchPos(loc);
				return;
			}
			return;
		}
		
		// MAXOR
		if (enumData.equals("conveyor_belt_begin")) {
			conveyorBeltBegin = loc;
			return;
		}
		
		if (enumData.equals("conveyor_belt_end")) {
			conveyorBeltEnd = loc;
			return;
		}
		
		if (enumData.equals("laser_color_block")) {
			laserBeaconLocation = loc;
			return;
		}
		
		if (enumData.equals("crystal_maxor")) {
			energyCrystals.add(loc);
			return;
		}
		
		if (enumData.equals("right_crystal_wire_begin")) {
			rightSideBegin = loc;
			return;
		}
		
		if (enumData.equals("right_crystal_wire_joint")) {
			rightSideJoint = loc;
			return;
		}
		
		if (enumData.equals("right_crystal_wire_end")) {
			rightSideEnd = loc;
			return;
		}
		
		if (enumData.equals("left_crystal_wire_begin")) {
			leftSideBegin = loc;
			return;
		}
		
		if (enumData.equals("left_crystal_wire_joint")) {
			if (additional.equals("1")) {
				leftSideFirstJoint = loc;
			} else {
				leftSideSecondJoint = loc;
			}
			return;
		}
		
		if (enumData.equals("left_crystal_wire_end")) {
			leftSideEnd = loc;
			return;
		}
		
		if (enumData.equals("left_crystal_pad")) {
			leftPad = loc;
			return;
		}
		
		if (enumData.equals("right_crystal_pad")) {
			rightPad = loc;
			return;
		}
		
		if (enumData.equals("p1_trapdoor_1")) {
			trapDoorPos1 = loc;
			return;
		}
		
		if (enumData.equals("p1_trapdoor_2")) {
			trapDoorPos2 = loc;
			return;
		}
		
		// STORM
		if (enumData.equals("storm_green_pillar")) {
			greenCrusher = loc;
			return;
		}
		
		if (enumData.equals("storm_green_pad")) {
			greenPad = loc;
			return;
		}
		
		if (enumData.equals("storm_yellow_pillar")) {
			yellowCrusher = loc;
			return;
		}
		
		if (enumData.equals("storm_yellow_pad")) {
			yellowPad = loc;
			return;
		}
		
		if (enumData.equals("storm_purple_pillar")) {
			purpleCrusher = loc;
			return;
		}
		
		if (enumData.equals("storm_purple_pad")) {
			purplePad = loc;
			return;
		}
		
		if (enumData.equals("storm_waypoint")) {
			int path = Integer.parseInt(additional);
			stormCirclePath[path] = loc;
			return;
		}
		
		if (enumData.equals("storm_goldor_drop")) {
			goldorDropper = loc;
			return;
		}
		
		// NECRON
		if (enumData.equals("necron_tower")) {
			necronTowers[Integer.parseInt(additional)] = loc;
			return;
		}
		if (enumData.equals("necron_point")) {
			int path = Integer.parseInt(additional);
			necronPositions[path] = Sputnik.getBlockMatchPos(loc);
			return;
		}
		if (enumData.equals("necron_player_hover")) {
			necronHoverLocations.add(loc);
			if ("mid".equals(additional)) {
				this.necronEntrancePlatform = loc;
			}
			return;
		}
		if (enumData.equals("necron_trapdoor")) {
			necronTrapdoor.add(loc);
			return;
		}
		
		// GOLDOR
		if (enumData.startsWith("goldor")) {
			try {
				int index = Integer.parseInt(additional);
				handleGoldorStageNecessities(loc, enumData, index);
			} catch (Exception e) {
				System.err.println("Cannot parse: " + enumData + ":" + additional);
			}
		}
		
		if (enumData.equals("final_goldor_destination")) {
			finalGoldorLocation = Sputnik.addYTo(Sputnik.getBlockMatchPos(loc), -1);
			return;
		}
	}
	
	public void handleGoldorStageNecessities(Location loc, String enumData, int stage) {
		GoldorStage goldor = goldorStages[stage];
		if (enumData.equals("goldor_terminal")) {
			goldor.terminalBlocks.add(loc);
			return;
		}
		
		if (enumData.equals("goldor_lever")) {
			goldor.leverBlocks.add(loc);
			return;
		}
		
		if (enumData.equals("goldor_door_body")) {
			goldor.doorBlocksBound.add(loc);
			return;
		}
		
		if (enumData.equals("goldor_device_main")) {
			goldor.mainDeviceBound.add(loc);
			return;
		}
		
		if (enumData.equals("goldor_device_trigger")) {
			goldor.deviceTriggerBlock = loc;
			return;
		}
		
		if (enumData.equals("goldor_door_lock")) {
			goldor.doorLockOuter.add(loc);
			return;
		}
		
		if (enumData.equals("goldor_door_lock_inner")) {
			goldor.doorLockInner.add(loc);
			return;
		}
		
		if (enumData.equals("goldor_door_holder")) {
			goldor.doorHolder.add(loc);
			return;
		}
		
		if (enumData.equals("goldor_path")) {
			goldorPath[stage] = Sputnik.getBlockMatchPos(loc);
			return;
		}
		
		if (enumData.contains("goldor_hanging_tnt")) {
			goldor.hangingTNT.add(loc);
			return;
		}
	}
	
	@Override
	public void test(User executor, String[] args) {
		this.currentPhase.test(executor, args);
	}
	
	private static final Set<Material> LAVA_TYPES = EnumSet.of(
		Material.LAVA,
		Material.STATIONARY_LAVA
	);

	public void lavaJump(User user) {
		if (user.isAGhost()) return;
		if (!LAVA_TYPES.contains(user.getCurrentBlock().getType())) return; 
		user.toBukkitPlayer().setVelocity(new Vector(0, 3.5, 0));
		//user.damage(user.toBukkitPlayer().getMaxHealth() * 0.25, DamageCause.FIRE, null);
	}
	
	public boolean dungeonEnded() {
		return getHandle().isEnded();
	}
	
	@Override
	public void onSuperboomExplodes(Location origin) {
		if (currentPhase instanceof GoldorPhase) {
			// explodes the door
			((GoldorPhase) currentPhase).stage.explosion(origin);
		}
	}
	
	@Override
	public void start() {
		this.bossbar = new GlobalBossBar("&c", this.getHandle().getWorld());
		this.currentPhase = new MaxorPhase(this);
		this.currentPhase.start();
	}
	
	public void spawnPhaseWitherSkeletons(int phase) {
		this.witherMinerSpawnPoints[phase].forEach(loc -> {
			this.spawnWitherMinerOrGuard(loc);
		});
	}
	
	/**
	 * Spawn either a wither miner or a wither guard at a location
	 * @param spawnLoc
	 * @return the spawned entity
	 */
	public LivingEntity spawnWitherMinerOrGuard(Location spawnLoc) {
		LivingEntity e = (LivingEntity) new SEntity(spawnLoc, 
			SUtil.random(0, 1) == 1 ? SEntityType.WITHER_GUARD : SEntityType.WITHER_MINER,
			getHandle()
		).getEntity();
		spawnedSkeletons.add(e);
		return e;
	}
	
	
	/**
	 * The interface for each phases (4 phases + 1 additional)
	 * @author GiaKhanhVN
	 */
	static interface WitherLordPhase {
		void start();
		void end();
		default void test(User executor, String[] args) {
			executor.send("&cNot available!");
		};
	}
}
