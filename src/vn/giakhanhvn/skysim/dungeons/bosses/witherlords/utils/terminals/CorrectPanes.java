package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.GoldorTerminal;
import vn.giakhanhvn.skysim.util.SUtil;
import vn.giakhanhvn.skysim.util.Sputnik;

public class CorrectPanes extends TerminalSystemGUI {

	public CorrectPanes(GoldorTerminal parent) {
		super(parent, "Correct all the panes!", 5);
	}

	@Override
	public void onUITick(int tick) {}

	@Override
	public void init(Inventory inv) {
		for (int row = 1; row <= 3; row++) { // 2nd row to the 4th row
			for (int col = 2; col <= 6; col++) { // 3rd column to the 7th column
				// calc slot
				int slot = row * 9 + col;
				// color index
				// according to chummy: 75% red, 25% green
				short glassType = (short) (SUtil.random(0, 100) <= 75 ? 14 : 5);
				
				set(make().slot(row * 9 + col)
					// according to chum: 75% red, 25% green
					.item(istack().material(Material.STAINED_GLASS_PANE)
						.data(glassType).name(glassType == 14 ? "&cOff" : "&aOn")
					)
					// handle clicks
					.onclick(c -> {
						ItemStack clicked = inv.getItem(slot);
						ItemMeta meta = clicked.getItemMeta();
						// change the name
						meta.setDisplayName(Sputnik.trans(clicked.getDurability() == 5 ? "&cOff" : "&aOn"));
						// change the slot to green to red and vice-versa
						clicked.setDurability((short)(clicked.getDurability() == 5 ? 14 : 5));
						clicked.setItemMeta(meta);
						// set
						inv.setItem(slot, clicked);
						onClicked(inv);
					})
				);
			}
		}
	}
	
	// specific
	public void onClicked(Inventory i) {
		for (int row = 1; row <= 3; row++) { // 2nd row to the 4th row
			for (int col = 2; col <= 6; col++) { // 3rd column to the 7th column
				ItemStack clicked = i.getItem(row * 9 + col);
				if (clicked == null || clicked.getDurability() != 5) // not lime
				return;
			}
		}
		// finish terminal
		this.completeTerminal();
	}

	@Override
	public String internalName() {
		return "PANE_CORRECT";
	}
	
	@Override
	public int skipTerminalSlot() {
		return 4; // mid
	}

}
