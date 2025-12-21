package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils;

import org.bukkit.block.Block;
import org.bukkit.event.block.Action;

import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.GoldorStage;
import vn.giakhanhvn.skysim.user.User;
import vn.giakhanhvn.skysim.util.DynamicHologram;
import vn.giakhanhvn.skysim.util.Sputnik;

// yes, we need this, for some absolute jackass reason
public class GoldorLever {
	public Block leverBlock;
	public DynamicHologram dynamicHologram;
	public GoldorStage core;
	
	public GoldorLever(GoldorStage stage, Block lever) {
		this.leverBlock = lever;
		this.core = stage;
		
		dynamicHologram = new DynamicHologram(
			Sputnik.getBlockMatchPos(leverBlock.getLocation().add(0, 1, 0)),
			1, 0, 50
		);
		
		// init
		dynamicHologram.setLine(0, "&cNot Activated");
		dynamicHologram.update();
		
		this.leverBlock.onBlockClick(e -> {
			if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
			// boilerplate
			User user = User.getUser(e.getPlayer());
			if (user.isAGhost()) return;
			// completes the lever
			core.onCompletion(user, "activated a lever");
			// set shit accordingly
			dynamicHologram.setLine(0, "&aActivated");
			dynamicHologram.update();
			// clear listener
			this.leverBlock.onBlockClick(null);
 		});
	}
}
