package miniventure.game.world;

import java.util.TreeMap;

import miniventure.game.item.Item;
import miniventure.game.world.entity.Entity;
import miniventure.game.world.entity.mob.Mob;
import miniventure.game.world.entity.mob.Player;
import miniventure.game.world.tile.Tile;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface WorldObject {
	
	Level getLevel();
	
	Rectangle getBounds();
	default Vector2 getCenter() { return getBounds().getCenter(new Vector2()); }
	default Vector2 getPosition() { return getBounds().getPosition(new Vector2()); }
	default Vector2 getSize() { return getBounds().getSize(new Vector2()); }
	
	void update(float delta);
	
	void render(SpriteBatch batch, float delta, Vector2 posOffset);
	
	default float getLightRadius() { return 0; }
	
	// returns whether the given entity can share the same space as this object.
	boolean isPermeableBy(Entity entity);
	
	// returns whether interaction had any effect (should we look for other objects to interact with?)
	boolean interactWith(Player player, Item heldItem);
	
	// returns whether attack had any effect (should we look for other objects to attack?)
	boolean attackedBy(Mob mob, Item attackItem);
	
	// mostly used for the player, but also could be mobs hurting other mobs, or tiles hurting mobs
	boolean hurtBy(WorldObject obj, int dmg);
	
	boolean touchedBy(Entity entity);
	
	void touching(Entity entity);
	
	// returns the closest tile to the center of this object, given an array of tiles.
	@Nullable
	default Tile getClosestTile(@NotNull Array<Tile> tiles) {
		if(tiles.size == 0) return null;
		
		final Vector2 center = getBounds().getCenter(new Vector2());
		TreeMap<Vector2, Tile> tileMap = new TreeMap<>((v1, v2) -> (int) (center.dst2(v1) - center.dst2(v2)));
		for(Tile t: tiles)
			tileMap.put(t.getCenter(), t);
		
		return tileMap.firstEntry().getValue();
	}
}
