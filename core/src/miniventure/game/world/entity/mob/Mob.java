package miniventure.game.world.entity.mob;

import miniventure.game.MyUtils;
import miniventure.game.world.ItemDrop;
import miniventure.game.world.Level;
import miniventure.game.world.WorldObject;
import miniventure.game.world.entity.Direction;
import miniventure.game.world.entity.Entity;
import miniventure.game.world.entity.TextParticle;
import miniventure.game.world.entity.mob.MobAnimationController.AnimationState;
import miniventure.game.world.tile.Tile;
import miniventure.game.world.tile.TileType;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import org.jetbrains.annotations.NotNull;

/**
 * This class is necessary because it ought to nicely package up the functionality of a mob, that moves around, and has up/down/left/right walking animations. Though, I may move the directional + state driven animation to its own class...
 */
public abstract class Mob extends Entity {
	
	// for knockback, the whole process should take about 0.5s. The first half at a constant speed, and the second half can be spend slowing down at a linear pace.
	
	private static final float KNOCKBACK_SPEED = 10 * Tile.SIZE; // in units / second
	private static final float MIN_KNOCKBACK_TIME = 0.05f;
	private static final float MAX_KNOCKBACK_TIME = 0.25f;
	private static final float DAMAGE_PERCENT_FOR_MAX_PUSH = 0.2f;
	
	@NotNull private Direction dir;
	@NotNull private MobAnimationController animator;
	
	private final int maxHealth;
	private int health;
	@NotNull private ItemDrop[] itemDrops;
	
	private float knockbackTimeLeft = 0;
	private Vector2 knockbackVelocity = new Vector2(); // knockback is applied once, at the start, as a velocity. The mob is moved with this velocity constantly, slowing down at a fixed rate, until the knockback is gone.
	
	public Mob(@NotNull String spriteName, int health, @NotNull ItemDrop... deathDrops) {
		super(new Sprite());
		dir = Direction.DOWN;
		this.maxHealth = health;
		this.health = health;
		this.itemDrops = deathDrops;
		
		animator = new MobAnimationController(this, spriteName);
	}
	
	public Direction getDirection() { return dir; }
	
	public void render(SpriteBatch batch, float delta) {
		setSprite(animator.pollAnimation(delta));
		super.render(batch, delta);
	}
	
	@Override
	public void update(float delta) {
		super.update(delta);
		
		if(knockbackTimeLeft > 0) {
			super.move(new Vector2(knockbackVelocity).scl(delta));
			knockbackTimeLeft -= delta;
			if(knockbackTimeLeft <= 0) {
				knockbackTimeLeft = 0;
				knockbackVelocity.setZero();
			}
		}
	}
	
	@Override
	public Rectangle getBounds() {
		Rectangle bounds = super.getBounds();
		bounds.setHeight(bounds.getHeight()*4/5);
		return bounds;
	}
	
	@Override
	public void move(float xd, float yd) {
		super.move(xd, yd);
		
		if(xd != 0 || yd != 0) animator.requestState(AnimationState.WALK);
		
		Direction dir = Direction.getDirection(xd, yd);
		if(dir != null) {
			// change sprite direction
			this.dir = dir;
		}
	}
	
	@Override
	public boolean hurtBy(WorldObject obj, int damage) {
		health -= Math.min(damage, health);
		
		if(health > 0) {
			// do knockback
			
			knockbackVelocity.set(getBounds().getCenter(new Vector2()).sub(obj.getBounds().getCenter(new Vector2())).nor().scl(KNOCKBACK_SPEED));
			
			/*
				I want the player to be pushed back somewhere between min and max time.
				Min time should approach no health lost
				Max time should be at the constant
				so, steps:
					- get percent lost, capped at max
					- map percent to times
			 */
			
			float healthPercent = MyUtils.map(damage, 0, maxHealth, 0, 1);
			knockbackTimeLeft = MyUtils.map(Math.min(healthPercent, DAMAGE_PERCENT_FOR_MAX_PUSH), 0, DAMAGE_PERCENT_FOR_MAX_PUSH, MIN_KNOCKBACK_TIME, MAX_KNOCKBACK_TIME);
		}
		
		Level level = getLevel();
		if(level != null) {
			level.addEntity(new TextParticle(damage+"", this instanceof Player ? Color.PINK : Color.RED), getBounds().getCenter(new Vector2()));
			
			if (health == 0) {
				for (ItemDrop drop : itemDrops)
					drop.dropItems(level, this, obj);
				
				remove();
			}
		} else
			System.out.println("level is null for mob " + this);
		
		return true;
	}
	
	
	public boolean maySpawn(Tile tile) {
		TileType type = tile.getType();
		return type == TileType.GRASS || type == TileType.DIRT || type == TileType.SAND;
	}
}
