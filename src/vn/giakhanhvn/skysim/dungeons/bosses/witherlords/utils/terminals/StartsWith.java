package vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.terminals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.Inventory;
import net.md_5.bungee.api.ChatColor;
import universal.SConsts;
import vn.giakhanhvn.skysim.dungeons.bosses.witherlords.utils.GoldorTerminal;
import vn.giakhanhvn.skysim.item.SMaterial;
import vn.giakhanhvn.skysim.util.SUtil;

public class StartsWith extends TerminalSystemGUI {
	// precache
	final static Set<SMaterial> ALLOWED_MATERIALS = EnumSet.allOf(SMaterial.class);
	static {
		// no spawn eggs, too dumb
		ALLOWED_MATERIALS.removeIf(m -> m.hasClass() || m.name().endsWith("SPAWN_EGG"));
		// remove blacklisted materials from the constants list
		ALLOWED_MATERIALS.removeAll(SConsts.BLACKLISTED_MATERIALS);
	}
	
	final static int TERM_SIZE = 28;
	
	// selected materials for this terminal window
	List<SMaterial> selectedItems = new ArrayList<>();
	
	// self-explanatory;
	String selectedLetter;
	int itemsWithLetterCount; // kept track of the thing
	
	public StartsWith(GoldorTerminal parent) {
		super(parent, "Generic Terminal", 6);
		
		// initialize the terminal
		List<SMaterial> allowedList = new ArrayList<>(ALLOWED_MATERIALS);
		Collections.shuffle(allowedList); // Shuffle the list randomly
		
		// Return up to TERM_SIZE elements
		Map<String, Integer> freqMap = new HashMap<>();
		
		// get 28 items and put them in a list
		for (int i = 0; i < TERM_SIZE; i++) {
			SMaterial material = allowedList.get(i);
			// get the actual display name of the item
			String matName = SUtil.getMaterialDisplayName(
				material.getCraftMaterial(),
				material.getData()
			);
			
			freqMap.merge(matName.substring(0, 1), 1, Integer::sum);
			this.selectedItems.add(material);
		}
		
		// we will select the letter that has the most frequent appearance
		int maxFrequency = 0;
		for (Map.Entry<String, Integer> entry : freqMap.entrySet()) {
			if (entry.getValue() > maxFrequency) {
				maxFrequency = entry.getValue();
				// set it here
				this.selectedLetter = entry.getKey();
			}
		}
		// this for loop also counted the frequency already
		this.itemsWithLetterCount = maxFrequency;
 		
		// set the title
		this.title = "What starts with " + (selectedLetter.toUpperCase()) + "?";
	}

	@Override
	public void onUITick(int tick) {}
	
	
	int clickedItems = 0;
	@Override
	public void init(Inventory inv) {
		int index = 0;
		for (int row = 1; row <= 4; row++) { // 4x7 GUI
			for (int col = 1; col <= 7; col++) {
				SMaterial primitive = selectedItems.get(index);
				String name = SUtil.getMaterialDisplayName(
					primitive.getCraftMaterial(),
					primitive.getData()
				);
				
				int slot = 9 * row + col;
				
				System.out.println(primitive + " " + primitive.getCraftMaterial() + " " + primitive.getData());
				
				set(make().slot(slot)
					.item(istack()
						// construct the stack
						.material(primitive.getCraftMaterial())
						.data(primitive.getData())
						// first letter is highlighted
						.name("&c" + name.charAt(0) + "&a" + name.substring(1, name.length()))
					).onclick(e -> {
						if (name.startsWith(selectedLetter)) {
							boolean clicked = inv.getItem(slot).getEnchantments().size() > 0;
							if (clicked) return;
							
							// if the clicked items exceeded or = the amount of
							// items started with a specific letter, complete the
							// terminal
							if(++clickedItems >= itemsWithLetterCount) {
								completeTerminal();
							}
							
							// indicate that the item has been selected
							inv.setItem(slot, istack()
								// construct the stack
								.material(primitive.getCraftMaterial())
								.data(primitive.getData())
								.name("&a✔ " + name)
								.glow()
								.build()
							);
							
							return;
						}
						
						// the terminal has been failed
						viewer().sendMessage(ChatColor.RED + "Wrong item. Try again!");
						viewer().closeInventory();
					})
				);
				++index;
			}
		}
	}

	@Override
	public String internalName() {
		return "STARTS_WITH";
	}
	
	@Override
	public int skipTerminalSlot() {
		return 4; // mid
	}
}
