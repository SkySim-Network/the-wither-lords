package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.animations;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;

import vn.giakhanhvn.skysim.util.BlockFallAPI;
import vn.giakhanhvn.skysim.util.SUtil;
import vn.giakhanhvn.skysim.util.Sputnik;

public class Phase3DropAnimation {
	public Location dropperLocation;
	static final int FLOOR_TO_CRUSHER_Y = 18;
	
	// the red floor
	List<Location> redFloor = new ArrayList<>();
	
	public Phase3DropAnimation(Location origin) {
		// get the layer of red floor
		this.dropperLocation = origin;
		this.dropperLocation.add(0, -1, 0);
		// scan the red floor to select what blocks to break
		Sputnik.cuboid(
			// scan the 7x7 area
			dropperLocation.clone().add(3, 0, 3), 
			dropperLocation.clone().add(-3, 0, -3)
		).forEach(block -> {
			// if the block is within 2 blocks, destroy immediately
			// otherwise, choose randomly
			// additionally: the barrier blocks below are removed
			// without animation
			if (block.getLocation().distance(dropperLocation) <= 3 || SUtil.random(0, 2) == 0) {
				redFloor.add(block.getLocation());
			}
			// destroy all blocks below it (the barrier)
			redFloor.add(block.getLocation().add(0, -1, 0));
		});
	}
	
	@SuppressWarnings("deprecation")
	public void dropPillar() {
		dropperLocation.add(0, FLOOR_TO_CRUSHER_Y, 0);
		// get the to drop
		for (int y = 1; y <= 8; y++) {
			// final
			final int fy = y;
			// get blocks
			Sputnik.cuboid(
				// scan the 7x7 area
				dropperLocation.clone().add(3, -y, 3), 
				dropperLocation.clone().add(-3, -y, -3)
			).forEach(b -> {
				// only drop if the thing is lower than 2 up (OR radomized)
				if (!(fy > 2 || SUtil.random(0, 2) == 0)) return; 
				
				Material mat = b.getType();
				byte data = b.getData();
				// break the blocks
				b.setType(Material.AIR);
				// play effects
				b.getLocation().getWorld().playEffect(
					Sputnik.getBlockMatchPos(b.getLocation()), 
					Effect.STEP_SOUND, mat, 20
				);
				
				// drop the blocks down to the thing
				if (SUtil.random(0, 1) == 0) BlockFallAPI.sendBlock(
					Sputnik.getBlockMatchPos(b.getLocation()), 
					mat, data, 
					dropperLocation.getWorld(),
					70
				);
			});
		}
		SUtil.delay(() -> { // 28 ticks is the period that the blocks fall to the ground
			// remove the blocks instantly
			redFloor.forEach(l -> {
				l.getWorld().playEffect(
					Sputnik.getBlockMatchPos(l), 
					Effect.STEP_SOUND, Material.STAINED_CLAY.getId(), 20
				);
				l.getBlock().setType(Material.AIR);
			});
		}, 28);
	}
}
