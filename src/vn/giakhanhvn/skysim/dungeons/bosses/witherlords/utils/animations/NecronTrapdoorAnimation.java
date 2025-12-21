package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.animations;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import vn.giakhanhvn.skysim.util.BlockFallAPI;
import vn.giakhanhvn.skysim.util.Sputnik;

public class NecronTrapdoorAnimation {
	List<Block> toDrop;
	
	public NecronTrapdoorAnimation(Location p1, Location p2) {
		this.toDrop = Sputnik.cuboid(p1.add(0, -1, 0), p2.add(0, -1, 0));
	}
	
	// simple animation
	public void playAnimation() {
		toDrop.forEach(b -> {
			byte data = b.getData();
			Material mat = b.getType();
			
			// break before sending blocks to avoid stupid
			b.setType(Material.AIR);
			
			BlockFallAPI.sendBlock(
				Sputnik.getBlockMatchPos(b.getLocation()), 
				mat, data, 
				b.getLocation().getWorld(), 60
			);
		});
	}
}
