package miniventure.game.world.tilenew;

import miniventure.game.world.entity.Entity;


@FunctionalInterface
public interface TouchListener extends TilePropertyInstance {
	
	//TouchListener DO_NOTHING = entity -> {};
	
	void touchedBy(Entity entity, Tile tile, boolean initial);
	//void stillTouchedBy(Entity entity);
	//void steppedOff(Entity entity); // idk, this will be much later.
	
}
