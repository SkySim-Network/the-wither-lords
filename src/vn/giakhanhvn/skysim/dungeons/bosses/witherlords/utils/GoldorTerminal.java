package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;

import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.GoldorStage;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals.ClickInOrder;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals.CorrectPanes;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals.Melody;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals.Rubix;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals.StartsWith;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals.TerminalSystemGUI;
import vn.giakhanhvn.skysim.user.User;
import vn.giakhanhvn.skysim.util.DynamicHologram;
import vn.giakhanhvn.skysim.util.SUtil;
import vn.giakhanhvn.skysim.util.Sputnik;

public class GoldorTerminal {
	// registered terminals
	static final Class<?>[] REGISTERED_TERMINALS = {
		ClickInOrder.class,
		CorrectPanes.class,
		Melody.class,
		Rubix.class,
		StartsWith.class
	};
	
	// the indicator
	public DynamicHologram hologram;
	// the block in front of the terminal command block
	private Block blockFront;
	
	//flags
	public boolean finished = false;
	// current stage
	public GoldorStage stage;
	// the assigned terminal type
	public Class<TerminalSystemGUI> selectedType;
	
	@SuppressWarnings("unchecked")
	public GoldorTerminal(GoldorStage stage, Block origin) {
		this.blockFront = findFacingOutBlock(origin);
		if (blockFront == null) throw new IllegalStateException("block stuck!");
		
		// assign the current stage
		this.stage = stage;
		
		// assign a terminal type
		this.selectedType = (Class<TerminalSystemGUI>) SUtil.randArr(REGISTERED_TERMINALS);
		
		Location head = Sputnik.getBlockMatchPos(blockFront.getLocation());
		// initialize the hologram
		hologram = new DynamicHologram(
			head.clone().add(0, 0.5, 0), 2, 0.45, 50
		);
		
		// set incomplete
		hologram.setLine(1, "&cInactive Terminal");
		hologram.setLine(0, "&e&lCLICK HERE");
		hologram.update();
		
		// spawn interaction entity
		ArmorStand core = head.getWorld().spawn(head, ArmorStand.class);
		core.setVisible(false);
		core.setGravity(false);
		
		core.onClickByPlayer(event -> {
			this.openTerminal(User.getUser(event.getPlayer()));
		});
	}
	
	public void openTerminal(User user) {
		// no ghost or finished
		if (user.isAGhost() || finished) return;
		try {
			// spawn the terminal for this user
			selectedType.getDeclaredConstructor(GoldorTerminal.class)
				.newInstance(this)
			.open(user.toBukkitPlayer());
		} catch (Exception e) {
			// prevent an error from throwing a run
			user.send("&cError! Cannot spawn terminal with type: " + selectedType.getCanonicalName() + ". Please report this error, terminal skipped!");
			this.complete(user);
		}
	}
	
	public void complete(User u) {
		// handle race condition
		if (this.finished) return;
		
		// set complete
		this.finished = true;
		
		// set the hologram (indicator)
		hologram.setLine(1, null);
		hologram.setLine(0, "&aActive Terminal");
		hologram.update();
		
		// reach the outer systems
		stage.onCompletion(u, "activated a terminal");
	}
	
	private Block findFacingOutBlock(Block bl) {
		BlockFace[] toCheck = { 
			BlockFace.EAST, BlockFace.NORTH, 
			BlockFace.WEST, BlockFace.SOUTH 
		};
		// shit
		for (BlockFace bf : toCheck) {
			if (bl.getRelative(bf).getType() == Material.AIR) {
				return bl.getRelative(bf);
			}
		}
		return null;
	}
}
