package vn.giakhanhvn.skysim.dungeons.bosses.witherlords;

import vn.giakhanhvn.skysim.dungeons.SDungeons.Difficulty;

public class GameplaySystem {
	// For contributors:
	// - HP represents the Health Points of the Boss. Data type: Number
	// - DPS denotes the Damage Per Second dealt by the Boss. Data type: Number
	// - Armor indicates the Boss's defense as a percentage. Data type: Number (max 100)
	//   - A value of 100% makes the Boss completely immune to all attacks
	//   - A value of 0% allows full damage to pass through without reduction
	//   - Intermediate values reduce incoming damage proportionally 
	//	     (e.g., 50% Armor reduces incoming damage by half)
	
	// FOR MAXOR
	public static double[] MAXOR_HP = { 
		1_500_000_000d, // MERCY
		15_000_000_000d, // BALANCED
		75_000_000_000d, // CHALLENGE
		150_000_000_000d // GIVE ME GOD OF ROOMS
	};
	
	public static double[] STORM_HP = {
		2_000_000_000d, // MERCY
		25_000_000_000d, // BALANCED
		100_000_000_000d, // CHALLENGE
		230_000_000_000d // GIVE ME GOD OF ROOMS
	};
	
	public static double[] GOLDOR_HP = { 
		3_000_000_000d, // MERCY
		35_000_000_000d, // BALANCED
		150_000_000_000d, // CHALLENGE
		300_000_000_000d // GIVE ME GOD OF ROOMS
	};
	
	public static double[] NECRON_HP = { 
		4_000_000_000d, // MERCY
		45_000_000_000d, // BALANCED
		200_000_000_000d, // CHALLENGE
		360_000_000_000d // GIVE ME GOD OF ROOMS
	};
	
	public static double[] MAXOR_DPS = { 
		650_000d, // MERCY
		15_000_000d, // BALANCED
		90_000_000d, // CHALLENGE
		150_000_000d // GIVE ME GOD OF ROOMS
	};

	public static double[] STORM_DPS = { 
		1_100_000d, // MERCY
		25_000_000d, // BALANCED
		130_000_000d, // CHALLENGE
		250_000_000d // GIVE ME GOD OF ROOMS
	};

	public static double[] GOLDOR_DPS = { 
		2_100_000d, // MERCY
		35_000_000d, // BALANCED
		160_000_000d, // CHALLENGE
		350_000_000d // GIVE ME GOD OF ROOMS
	};

	public static double[] NECRON_DPS = { 
		3_200_000d, // MERCY
		40_000_000d, // BALANCED
		200_000_000d, // CHALLENGE
		400_000_000d // GIVE ME GOD OF ROOMS
	};
	
	public static double[] MAXOR_DEFENSE = {
		5, // M
		20, // B
		50, // C
		80, // G
	};
	
	public static double[] STORM_DEFENSE = {
		5, // M
		25, // B
		55, // C
		85, // G
	};
	
	public static double[] GOLDOR_DEFENSE = {
		0, // M
		10, // B
		40, // C
		70, // G
	};
	
	public static double[] NECRON_DEFENSE = {
		5, // M
		35, // B
		75, // C
		95, // G
	};
	
	// for WITHER SKELETONS
	public static double[] WITHER_SKELETONS_HP = {
		30_000_000d,
		350_000_000d,
		600_000_000d,
		1_200_000_000d,
	};
	
	public static double[] WITHER_SKELETONS_DPS = {
		40_000d,
		120_000d,
		450_000d,
		750_000d,
	};
	
	public static double getMaxorHealth(Difficulty difficulty) {
		return MAXOR_HP[difficulty.ordinal()];
	}
	
	public static double getMaxorDPS(Difficulty difficulty) {
		return MAXOR_DPS[difficulty.ordinal()];
	}
	
	public static double getMaxorDefense(Difficulty difficulty) {
		return MAXOR_DEFENSE[difficulty.ordinal()];
	}
	
	public static double getGoldorHealth(Difficulty difficulty) {
		return GOLDOR_HP[difficulty.ordinal()];
	}
	
	public static double getGoldorDPS(Difficulty difficulty) {
		return GOLDOR_DPS[difficulty.ordinal()];
	}
	
	public static double getGoldorDefense(Difficulty difficulty) {
		return GOLDOR_DEFENSE[difficulty.ordinal()];
	}
	
	public static double getStormHealth(Difficulty difficulty) {
		return STORM_HP[difficulty.ordinal()];
	}

	public static double getStormDPS(Difficulty difficulty) {
		return STORM_DPS[difficulty.ordinal()];
	}
	
	public static double getStormDefense(Difficulty difficulty) {
		return STORM_DEFENSE[difficulty.ordinal()];
	}

	public static double getNecronHealth(Difficulty difficulty) {
		return NECRON_HP[difficulty.ordinal()];
	}

	public static double getNecronDPS(Difficulty difficulty) {
		return NECRON_DPS[difficulty.ordinal()];
	}
	
	public static double getNecronDefense(Difficulty difficulty) {
		return NECRON_DEFENSE[difficulty.ordinal()];
	}
	
	public static double getWitherSkeletonHealth(Difficulty difficulty) {
		return WITHER_SKELETONS_HP[difficulty.ordinal()];
	}

	public static double getWitherSkeletonDPS(Difficulty difficulty) {
		return WITHER_SKELETONS_DPS[difficulty.ordinal()];
	}
}
