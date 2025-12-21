package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.GoldorTerminal;
import vn.giakhanhvn.skysim.util.IStackBuilder;
import vn.giakhanhvn.skysim.util.SUtil;

public class Melody extends TerminalSystemGUI {
	public Melody(GoldorTerminal parent) {
		super(parent, "Click the button on time!", 6);
	}
	
	// glass panes
	final IStackBuilder PURPLE_GLASS = istack().material(Material.STAINED_GLASS_PANE).data((short)10).name("&c").amount(1);
	final IStackBuilder RED_GLASS = istack().material(Material.STAINED_GLASS_PANE).data((short)14).name("&c").amount(1);
	final IStackBuilder LIME_GLASS = istack().material(Material.STAINED_GLASS_PANE).data((short)5).name("&c").amount(1);
	final IStackBuilder WHITE_GLASS = istack().material(Material.STAINED_GLASS_PANE).data((short)0).name("&c").amount(1);
	
	// green clay
	final ItemStack LOCK_IN_ABLE = istack()
		.material(Material.STAINED_CLAY)
		.data((short)5)
		.name("&aLock In Slot")
		.lore("&7Click this button when the Red")
		.lore("&7slot lines up with the Purple")
		.lore("&7line!")
		.amount(1)
	.build();
	
	// the red terracotta
	final ItemStack NOT_LOCK_IN_ABLE = istack()
		.material(Material.STAINED_CLAY)
		.data((short)14)
		.name("&cLock In Slot")
		.amount(1)
	.build();

	
	// the purple (target)
	int currentSelectedColumn = 0;
	
	// the green cursor
	int currentCursorColumn = 0;
	// the row that the thing is supposed to operate
	int currentRow = 0;
	
	// other flags
	// penalty for clicking wrongly
	private boolean penalty = false;
	
	// draw the main panel with indicators
	public void redrawMelodyGUI(Inventory i) {
		for (int row = 0; row <= 5; row++) {
			for (int col = 1; col <= 5; col++) {
				int index = row * 9 + col;
				// draw the active (purple) indicator
				// the rows are first and last
				if (row == 0 || row == 5) {
					i.setItem(index, 
						// offset it by 1 so that it matches
						// the reason for offseting is: the internal values
						// start at 0, but the actual UI component index is 1
						(col - 1) != currentSelectedColumn ? 
						BLACK_STAINED_GLASS_PANE
						: PURPLE_GLASS.build()
					);
					continue;
				}
				// draw other rows, if the current row is not selected
				// set to white
				if ((row - 1) != currentRow) {
					i.setItem(index, WHITE_GLASS.build());
					continue;
				}
				// if it does, draw the row red with current selected column set to green
				i.setItem(index, 
					// offset it by 1 so that it matches
					(col - 1) == currentCursorColumn ? 
					LIME_GLASS.build() 
					: RED_GLASS.build()
				);
			}
		}
	}
	
	public void redrawButtons() {
		// force immediate redraw on the main component
		this.redrawMelodyGUI(inv);
		
		// redraw the buttons
		final int col = 7;
		for (int row = 1; row <= 4; row++) {
			final int actualRow = row - 1;
			// if "row" is == the current row, change to
			// the green clay
			inv.setItem(9 * row + col,
				actualRow == currentRow ? 
				LOCK_IN_ABLE : 
				NOT_LOCK_IN_ABLE
			);
		}
	}
	
	public void onClick(int row) {
		if (row != currentRow) return;
		
		// if the user clicked wrongly
		if (currentCursorColumn != currentSelectedColumn) {
			// freeze the UI for a sec
			this.penalty = true;
			return;
		}
		
		// select a new column
		currentSelectedColumn = SUtil.random(0, 3);
		
		// user completed that row, next
		if (++currentRow >= 4) {
			completeTerminal();
			return;
		}
		
		// update buttons
		redrawButtons();
	}
	
	public boolean cursorForward = true;
	@Override
	public void onUITick(int tick) {
		if (tick % 10 != 0) return; // tick this UI every 10 ticks
		
		// move the cursor
		if (tick > 0 && !penalty) {
			currentCursorColumn = (currentCursorColumn + (cursorForward ? 1 : -1)) % 5;
			
			// if reaches the end or the beginning, flip the direction
			if (currentCursorColumn == 0 || currentCursorColumn == 4) {
				// flip the cursor
				cursorForward = !cursorForward;
			}
		}
		
		// redraw the UI
		this.redrawMelodyGUI(inv);
		
		// remove the penalty
		this.penalty = false;
	}
	
	private Inventory inv;
	@Override
	public void init(Inventory inv) {
		this.inv = inv;
		// select a random one
		currentSelectedColumn = SUtil.random(0, 4);
		
		// initialize the button listeners
		final int col = 7;
		for (int row = 1; row <= 4; row++) {
			final int actualRow = row - 1;
			// the same thing as redrawButton
			// but instead of vanilla setItem
			// we use GUI#set so that listen to UI clicks is possible
			set(make().slot(9 * row + col).item(
				actualRow == currentRow ? 
				LOCK_IN_ABLE : 
				NOT_LOCK_IN_ABLE
			).onclick(click -> {
				// when click a row, fire this event
				onClick(actualRow);
			}));
		}
	}
	
	@Override
	public String internalName() {
		return "AIDS_MELODY";
	}
	
	@Override
	public int skipTerminalSlot() {
		return 52; // bottom right side
	}
}
