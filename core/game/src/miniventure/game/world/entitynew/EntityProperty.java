package miniventure.game.world.entitynew;

import java.util.HashMap;

import miniventure.game.api.Property;
import miniventure.game.api.PropertyFetcher;

import com.badlogic.gdx.math.Vector2;

public interface EntityProperty extends Property<EntityProperty> {
	
	/*
		sprite properties:
			- directional mob animations
			- text sprites
			- single textures
			- textures based on something, like an item, aka animation property chooser... such as direction...
		
		update properties:
			- keyboard input for movement / other control
			- ai updates
			- other misc updates
		
		level properties:
			- position and level (however, no behavior, just coords, so this is part of the Entity class)
			- size
		
		... that's about it..?
		
		So, I'm going to have a number of different update properties... Actually, I can just make a static method in the UpdateProperty class to merge different update properties into a single property.
		Oh! I totally forgot about events. Need those too.
		
		Event properties:
			- on attacked
			- on touched
			- on continued touch?
			- on attempted interaction
	 */
	
	static PropertyFetcher<EntityProperty> getDefaultPropertyMap() {
		//noinspection Convert2MethodRef
		return () -> new EntityProperty[] {
				(SizeProperty) () -> new Vector2(),
				(LevelListener) (e, lvl) -> {},
				(UpdateProperty) (e, delta) -> {}
		};
	}
	
	@Override
	default String[] getInitialData() { return new String[0]; }
	
	// This interface is for when you want to define multiple properties of the same type, but want to select only one.
	interface PropertyChooser<T extends EntityProperty> {
		T chooseProperty();
	}
}
