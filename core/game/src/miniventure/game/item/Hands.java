package miniventure.game.item;

import org.jetbrains.annotations.NotNull;

public class Hands extends Inventory {
	
	// holds the items in the player's hotbar.
	
	private int selection;
	private Inventory inventory;
	
	Hands(@NotNull Inventory inventory) {
		super(3);
		this.inventory = inventory;
	}
	
	@Override
	public boolean addItem(Item item) {
		if(super.getCount(item) == 0) {
			if(!super.addItem(item))
				return inventory.addItem(item);
		} else {
			if(!inventory.addItem(item))
				return super.addItem(item);
		}
		
		return true; // first of the two choices succeeded
	}
	
	@Override
	public boolean removeItem(Item item) {
		if(inventory.removeItem(item))
			return true;
		return super.removeItem(item);
	}
	
	@Override
	public int getCount(Item item) { return super.getCount(item) + inventory.getCount(item); }
	
	public void setSelection(int idx) { selection = idx; }
	public int getSelection() { return selection; }
	
	void swapItem(Inventory inv, int invIdx, int hotbarIdx) {
		Item item = inv.replaceItemAt(invIdx, getItemAt(hotbarIdx));
		replaceItemAt(hotbarIdx, item);
	}
	
	public void resetItemUsage() {}
	
	public boolean hasUsableItem() { return !(getSelectedItem().isUsed()); }
	
	@NotNull
	public Item getSelectedItem() { return getItemAt(selection); }
}
