package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import vn.giakhanhvn.skysim.util.DynamicHologram;

public class HologramAttachedEntity {
	public Entity entity;
	public DynamicHologram hologram;
	public double offset = 0.0;
	
	public HologramAttachedEntity(Entity e, DynamicHologram hologram, double offset) {
		this.entity = e;
		this.offset = offset;
		this.hologram = hologram;
		this.hologram.moveTo(e.getLocation().add(0, offset, 0));
	}
	
	public void teleport(Location l) {
		this.entity.teleport(l);
		this.hologram.moveTo(l.clone().add(0, offset, 0));
		this.hologram.update();
	}
	
	public void kill() {
		this.entity.remove();
		this.hologram.destroy();
	}
}
