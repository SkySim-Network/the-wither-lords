package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils;

import vn.giakhanhvn.skysim.dungeons.systems.DungeonEntity;
import vn.giakhanhvn.skysim.entity.EntityFunction;
import vn.giakhanhvn.skysim.entity.SEntityEquipment;
import vn.giakhanhvn.skysim.entity.SkeletonStatistics;
import vn.giakhanhvn.skysim.item.SMaterial;

public class WitherMinions {
	public static class WitherMiner implements DungeonEntity, EntityFunction, SkeletonStatistics {
		@Override
		public String getEntityName() {
			return "Wither Miner";
		}

		@Override
		public double getEntityMaxHealth() {
			return 10_000_000;
		}

		@Override
		public double getDamageDealt() {
			return 14000.0;
		}

		@Override
		public SEntityEquipment getEntityEquipment() {
			return new SEntityEquipment(
				SMaterial.STONE_PICKAXE.toSItem().getStack(),
				null, null, null, null
			);
		}

		@Override
		public double getMovementSpeed() {
			return 0.35;
		}

		public double getXPDropped() {
			return 7.0;
		}
		
		@Override
		public int mobLevel() {
			return -1;
		}
		
		@Override
		public boolean isWither() {
			return true;
		}
	}
	
	public static class WitherGuard implements DungeonEntity, EntityFunction, SkeletonStatistics {
		@Override
		public String getEntityName() {
			return "Wither Guard";
		}

		@Override
		public double getEntityMaxHealth() {
			return 3_500_000;
		}

		@Override
		public double getDamageDealt() {
			return 20000.0;
		}

		@Override
		public SEntityEquipment getEntityEquipment() {
			return new SEntityEquipment(
				SMaterial.BOW.toSItem().getStack(),
				null, null, null, null
			);
		}

		@Override
		public double getMovementSpeed() {
			return 0.0;
		}

		public double getXPDropped() {
			return 7.0;
		}
		
		@Override
		public int mobLevel() {
			return -1;
		}
		
		@Override
		public boolean isWither() {
			return true;
		}
	}
}
