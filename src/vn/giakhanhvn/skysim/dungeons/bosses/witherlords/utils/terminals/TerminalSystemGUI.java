package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import lombok.Getter;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.GoldorTerminal;
import vn.giakhanhvn.skysim.gui.GUI;
import vn.giakhanhvn.skysim.gui.GUIOpenEvent;
import vn.giakhanhvn.skysim.user.User;
import vn.giakhanhvn.skysim.util.STask;
import vn.giakhanhvn.skysim.util.STask.RunMode;

public abstract class TerminalSystemGUI extends GUI {
	public STask guiInternalTick;
	@Getter
	private GoldorTerminal parent;
	
	public TerminalSystemGUI(GoldorTerminal parent, String title, int rows) {
		super(title, rows * 9);
		this.parent = parent;
	}
	
	@Override
	public void onOpen(GUIOpenEvent e) {
		/* system methods */
		// fill the framework
		fill(BLACK_STAINED_GLASS_PANE);
		// fill mandatory systems
		if (viewer().isOp()) {
			// only allow skipping for OPs
			set(make().item(istack().name("&d➤ Skip Terminal")
				.material(Material.WATCH)
				.lore("&7Click this button to &eskip")
				.lore("&7this terminal without")
				.lore("&7penalty.")
				.lore("")
				.lore("&eClick to skip!"))
				// the slot can be configured
				.slot(skipTerminalSlot())
				// on click
				.onclick(click -> {
					// just skip it
					this.completeTerminal();
				})
			);
		}
		
		guiInternalTick = new STask().run(t -> {
			// before doing any UI tick, check if the terminal has been
			// completed/either by this player or another
			if(this.parent != null && this.parent.finished) {
				viewer().closeInventory();
				return;
			}
			// tick
			onUITick(t.counter);
			t.counter++;
		});
		
		// start the handler
		this.guiInternalTick.startLoop(RunMode.SYNC, 0, 0);
		
		// call user-defined shit
		this.init(e.getInventory());
	}
	
	@Override
	public void onClose(InventoryCloseEvent e) {
		// destroy the loop task
		if (this.guiInternalTick != null) {
			this.guiInternalTick.kill();
		}
	}
	
	public void completeTerminal() {
		this.viewer().closeInventory();
		
		if (this.parent != null) {
			this.parent.complete(User.getUser(viewer()));
			return;
		}
		
		// DEV TEST
		viewer().sendMessage("Terminal with ID: " + internalName() + " completed!");
	}
	
	public Player viewer() {
		return this.player;
	}
	
	// extended
	public int skipTerminalSlot() {
		return 0; // default is top-left
	}
	public abstract void onUITick(int tick);
	public abstract void init(Inventory inv);
	public abstract String internalName();
}
