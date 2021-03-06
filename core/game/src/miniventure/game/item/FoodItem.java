package miniventure.game.item;

import miniventure.game.world.entity.mob.Player;
import miniventure.game.world.entity.mob.Player.Stat;

import org.jetbrains.annotations.NotNull;

public enum FoodItem {
	
	Apple(2);
	
	private final int healthGained;
	
	FoodItem(int healthGained) {
		this.healthGained = healthGained;
	}
	
	@NotNull
	public Item get() {
		return new Item(ItemType.Food, name()) {
			@Override public Item copy() { return this; }
			
			@Override
			public String[] save() {
				return new String[] {getType().name(), FoodItem.this.name()};
			}
			
			@Override public void interact(Player player) {
				int gained = player.changeStat(Stat.Hunger, healthGained);
				if(gained > 0) {
					use();
				}
			}
		};
	}
}
