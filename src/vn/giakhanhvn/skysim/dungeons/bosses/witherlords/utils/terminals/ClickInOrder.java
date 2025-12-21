package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.GoldorTerminal;
import vn.giakhanhvn.skysim.util.IStackBuilder;

public class ClickInOrder extends TerminalSystemGUI {
	
	private List<Integer> order = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14));
	// keep track of the last clicked numbor
	private int lastClickedNumber;
	
	public ClickInOrder(GoldorTerminal parent) {
		super(parent, "Click in order!", 4);
	}

	@Override
	public void onUITick(int tick) {}
	
	final IStackBuilder RED_PANE = istack().material(Material.STAINED_GLASS_PANE).name("&c").data((short)14);
	final IStackBuilder LIME_PANE = istack().material(Material.STAINED_GLASS_PANE).name("&c").data((short)5);
	
	@Override
	public void init(Inventory inv) {
		// shuffle
		Collections.shuffle(order);
		
		int index = 0;
		for (int row = 1; row <= 2; row++) { // 2nd row to the 3rd row
			for (int col = 1; col <= 7; col++) { // 2nd column to the 8th column
				// calc slot
				int slot = row * 9 + col;
				int number = order.get(index);
				
				set(make().slot(row * 9 + col)
					.item(RED_PANE.amount(number))
					// handle clicks
					.onclick(c -> {
						// click in order, so if the number the player clicked
						// is EQUAL the last one + 1, they clicked
						// the right one
						if (number != lastClickedNumber + 1) return;
						
						inv.setItem(slot, LIME_PANE.amount(number).build());
						// update the "last"
						this.lastClickedNumber = number;
						
						onClicked(inv);
					})
				);
				++index;
			}
		}
	}
	
	// specific
	public void onClicked(Inventory i) {
		for (int row = 1; row <= 2; row++) { // 2nd row to the 3rd row
			for (int col = 1; col <= 7; col++) { // 2nd column to the 8th column
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
		return "ORDER_CLICK";
	}
	
	@Override
	public int skipTerminalSlot() {
		return 4; // mid
	}

}
