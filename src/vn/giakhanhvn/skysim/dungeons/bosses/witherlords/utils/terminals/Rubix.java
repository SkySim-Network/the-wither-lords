package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.GoldorTerminal;
import vn.giakhanhvn.skysim.util.IStackBuilder;
import vn.giakhanhvn.skysim.util.SUtil;

public class Rubix extends TerminalSystemGUI {
	private static final Short[] GLASS_ORDER = { 
		1, // ORANGE
		4, // YELLOW
		13, // GREEN
		11, // BLUE
		14 // RED
	};
	
	private static final String[] GLASS_COLORS_NAME = { 
		"&6Orange", // ORANGE
		"&eYellow", // YELLOW
		"&aGreen", // GREEN
		"&9Blue", // BLUE
		"&cRed" // RED
	};
	
	// map the colors to the orders
	private static final Map<Short, Integer> COLOR_TO_INDEX = new HashMap<>();
	
	static {
		// map the glass color to the index
		for (int i = 0; i < GLASS_ORDER.length; i++) {
			COLOR_TO_INDEX.put(GLASS_ORDER[i], i);
		}
	}
	
	public Rubix(GoldorTerminal parent) {
		super(parent, "Change all to same color!", 5);
	}

	@Override
	public void onUITick(int tick) {}
	
	final IStackBuilder STAINED_PANE = istack().material(Material.STAINED_GLASS_PANE).amount(1);
	
	@Override
	public void init(Inventory inv) {
		for (int row = 1; row <= 3; row++) { // 2nd row to the 4th row
			for (int col = 3; col <= 5; col++) { // 4th column to the 6th column
				// calc slot
				int slot = row * 9 + col;
				int index = SUtil.randInt(0, GLASS_ORDER.length - 1);
				short color = GLASS_ORDER[index];
				
				set(make().slot(row * 9 + col)
					.item(STAINED_PANE.data((short) color).name(GLASS_COLORS_NAME[index]))
					// handle clicks
					.onclick(c -> {
						ItemStack clicked = inv.getItem(slot);
						
						// get the current index and increemnt/decrement depending on what you clicked
						int current = COLOR_TO_INDEX.get(clicked.getDurability());
						int next = (current + (c.isRightClick() ? -1 : 1) + GLASS_ORDER.length) % GLASS_ORDER.length;
						
						// set it and update
						inv.setItem(slot, istack()
							.material(Material.STAINED_GLASS_PANE)
							.data(GLASS_ORDER[next])
							// name?
							.name(GLASS_COLORS_NAME[next])
						.build());
						onClicked(inv);
					})
				);
			}
		}
	}
	
	// specific
	public void onClicked(Inventory i) {
		short lastMet = -1;
		for (int row = 1; row <= 3; row++) { // 2nd row to the 4th row
			for (int col = 3; col <= 5; col++) { // 4th column to the 6th column
				ItemStack item = i.getItem(row * 9 + col);
				if(lastMet == -1) lastMet = item.getDurability();
				
				// if one of them isnt the correct color, return
				if (item.getDurability() != lastMet) return;
			}
		}
		// finish terminal
		this.completeTerminal();
	}

	@Override
	public String internalName() {
		return "ORDER_CLICK";
	}
	
	@Override
	public int skipTerminalSlot() {
		return 25; // right side
	}
}
