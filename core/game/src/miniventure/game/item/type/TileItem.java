package miniventure.game.item.type;

import miniventure.game.GameCore;
import miniventure.game.util.MyUtils;
import miniventure.game.world.WorldObject;
import miniventure.game.world.entity.mob.Player;
import miniventure.game.world.tile.Tile;
import miniventure.game.world.tile.TileType;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

import org.jetbrains.annotations.NotNull;

public class TileItem extends Item {
	
	@FunctionalInterface
	interface PlacementFunction {
		boolean canPlace(TileType type);
		
		PlacementFunction ANY = (type) -> true;
		PlacementFunction LAND = oneOf(TileType.GRASS, TileType.DIRT, TileType.SAND);
		
		static PlacementFunction oneOf(TileType... types) {
			return (type) -> {
				for(TileType listType: types)
					if(listType == type)
						return true;
				
				return false;
			};
		}
	}
	
	@NotNull private TileType result;
	@NotNull private PlacementFunction placer;
	
	public TileItem(@NotNull TileType type, @NotNull TileType... canPlaceOn) {
		this(type, PlacementFunction.oneOf(canPlaceOn));
	}
	public TileItem(@NotNull TileType type, @NotNull PlacementFunction placer) {
		this(MyUtils.toTitleCase(type.name()), GameCore.tileAtlas.findRegion(type.name().toLowerCase()+"/00"), type, placer); // so, if the placeOn is null, then...
	}
	
	private TileItem(String name, TextureRegion texture, @NotNull TileType result, @NotNull PlacementFunction placer) {
		super(name, texture);
		this.placer = placer;
		this.result = result;
	}
	
	@Override
	public boolean interact(WorldObject obj, Player player) {
		if(!isUsed() && obj instanceof Tile) {
			Tile tile = (Tile) obj;
			
			if(placer.canPlace(tile.getType()) && tile.addTile(result)) {
				use();
				return true;
			}
		}
		
		return obj.interactWith(player, this);
	}
	
	@Override
	public boolean equals(Object other) {
		if(!super.equals(other)) return false;
		TileItem ti = (TileItem) other;
		return ti.result == result;
	}
	
	@Override
	public int hashCode() { return super.hashCode() - 17 * result.hashCode(); }
	
	@Override
	public TileItem copy() {
		return new TileItem(getName(), getTexture(), result, placer);
	}
}
