package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.animations;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import vn.giakhanhvn.skysim.util.TimedActions;

public class TrapdoorAnimation {
	public static int[][] OFFSET_SHIFT = { 
		{ 0, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0 }, 
		{ 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 0 },
		{ 0, 1, 1, 1, 2, 0, 2, 1, 1, 1, 0 }, 
	};

	Location pos1;
	Location pos2;
	World world;

	Block[][] tiles = new Block[11][18];

	public TrapdoorAnimation(Location p1, Location p2) {
		pos1 = p1;
		pos2 = p2;
		world = pos1.getWorld();
		scanBlocks();
	}

	public void scanBlocks() {
		int y = pos1.getBlockY() - 1;
		int tile = 0;
		for (int x = pos1.getBlockX(); x != pos2.getBlockX() - 1; x--) {
			int i = 0;
			for (int z = pos1.getBlockZ() - 1; z != pos2.getBlockZ(); z--) {
				tiles[tile][i] = world.getBlockAt(x, y, z);
				i++;
			}
			tile++;
		}
	}

	public TimedActions playAnimation() {
		// first frame remove mid
		for (int i = 0; i < tiles[0].length; i++) {
			tiles[5][i].setType(Material.AIR);
		}

		// next 3 frames
		TimedActions ta = new TimedActions();
		for (int i = 0; i < OFFSET_SHIFT.length; i++) {
			final int index = i;
			ta.add(() -> {
				for (int j = 0; j < tiles.length; j++) {
					if (j == 5)
						continue;
					for (int k = 0; k < tiles[0].length; k++) {
						Block b = tiles[j][k];
						Material mat = b.getType();
						byte data = b.getData();
						
						b.setType(Material.AIR);
						
						Block nb = world.getBlockAt(b.getLocation().add(0, -OFFSET_SHIFT[index][j], 0));
						nb.setType(mat);
						nb.setData(data);
						
						tiles[j][k] = nb;
					}
				}
				
				// frame 3
				if (index >= 1) {
					for (int j = 0; j < tiles.length; j++) {
						if (j == 4 || j == 5 || j == 6)
							continue;
						for (int k = 0; k < tiles[0].length; k++) {
							Block b = tiles[j][k];
							
							Block nb = world.getBlockAt(b.getLocation().add(0, -1, 0));
							nb.setType(Material.STONE);
							nb.setData((byte) 6);
							
							Block ob = world.getBlockAt(b.getLocation().add(0, 1, 0));
							ob.setType(Material.AIR);
						}
					}
				}
			}).wait(4);
		}
		
		ta.add(() -> {
			// frame 5 is constructed by shifting leitmost tiles to the left/right by 1/2
			pushTile(3, 1);
			pushTile(4, 2);
			// right
			pushTile(6, -2);
			pushTile(7, -1);
		}).wait(4).add(() -> {
			pushTile(4, 2);
			pushTile(3, 2);
			pushTile(2, 2);
			pushTile(1, 1);

			pushTile(6, -2);
			pushTile(7, -2);
			pushTile(8, -2);
			pushTile(9, -1);
		});
		
		return ta;
	}

	private void pushTile(int index, int offset) {
		for (int k = 0; k < tiles[0].length; k++) {
			Block b = tiles[index][k];
			
			Block nb = world.getBlockAt(b.getLocation().add(offset, 0, 0));
			nb.setType(b.getType() == Material.SMOOTH_STAIRS ? Material.SMOOTH_BRICK : b.getType());
			nb.setData(b.getType() == Material.SMOOTH_STAIRS ? 0 : b.getData());
			
			Block ib = world.getBlockAt(b.getLocation().add(0, -1, 0));
			ib.setType(Material.AIR);
			
			b.setType(Material.AIR);
			
			tiles[index][k] = nb;
		}
	}
}
