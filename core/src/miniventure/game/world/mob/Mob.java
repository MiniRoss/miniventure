package miniventure.game.world.mob;

import miniventure.game.world.Direction;
import miniventure.game.world.Entity;
import miniventure.game.world.mob.MobAnimationController.AnimationState;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import org.jetbrains.annotations.NotNull;

/**
 * This class is necessary because it ought to nicely package up the functionality of a mob, that moves around, and has up/down/left/right walking animations. Though, I may move the "directional + state driven animation to its own class...
 */
public class Mob extends Entity {
	
	@NotNull private Direction dir;
	@NotNull private MobAnimationController animator;
	@NotNull private String spriteName;
	
	public Mob(@NotNull String spriteName) {
		dir = Direction.DOWN;
		this.spriteName = spriteName;
		
		animator = new MobAnimationController(this);
	}
	
	public Direction getDirection() { return dir; }
	
	@NotNull
	public String getSpriteName() {
		return spriteName;
	}
	
	public void dispose() {
		animator.dispose();
	}
	
	public void render(SpriteBatch batch, float delta) {
		animator.update(delta);
		
		batch.draw(animator.getFrame(), x, y);
	}
	
	@Override
	public void move(int xd, int yd) {
		super.move(xd, yd);
		
		if(xd != 0 || yd != 0) animator.requestState(AnimationState.WALK);
		
		Direction dir = Direction.getDirection(xd, yd);
		if(dir != null) {
			// change sprite direction
			this.dir = dir;
		}
	}
}
