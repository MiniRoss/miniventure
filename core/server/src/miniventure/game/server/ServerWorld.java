package miniventure.game.server;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;

import miniventure.game.GameCore;
import miniventure.game.GameProtocol.EntityAddition;
import miniventure.game.GameProtocol.EntityRemoval;
import miniventure.game.GameProtocol.WorldData;
import miniventure.game.ProgressPrinter;
import miniventure.game.item.ToolItem;
import miniventure.game.item.ToolItem.Material;
import miniventure.game.item.ToolType;
import miniventure.game.world.*;
import miniventure.game.world.entity.Entity;
import miniventure.game.world.entity.ServerEntity;
import miniventure.game.world.entity.mob.ServerPlayer;
import miniventure.game.world.levelgen.LevelGenerator;
import miniventure.game.world.tile.ServerTileType;
import miniventure.game.world.tile.TileEnumMapper;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import org.jetbrains.annotations.NotNull;

public class ServerWorld extends WorldManager {
	
	/*
		This is what contains the world. GameCore will check if the world is loaded here, and if not it won't render the game screen. Though... perhaps this class should hold a reference to the game screen instead...? Because, if you don't have a world, you don't need a game screen...
		
		The world will be created with this.
		It holds references to the current game level.
		You use this class to start processes on the whole world, like saving, loading, creating.
			And accessing the main player... and respawning. Also changing the player level?
		
		Perhaps this instance can be fetched from GameCore.
		
		GameScreen... game screen won't do much, just do the rendering. 
	 */
	
	private LevelGenerator levelGenerator;
	private GameServer server;
	
	private boolean worldLoaded = false;
	
	private final HashSet<WorldObject> keepAlives = new HashSet<>(); // always keep chunks around these objects loaded.
	
	public ServerWorld(boolean standalone) {
		super(new TileEnumMapper<>(ServerTileType::new));
		
		server = new GameServer(standalone);
		server.startServer();
	}
	
	// Update method
	
	@Override
	public void update(float delta) {
		
		HashSet<ServerLevel> loadedLevels = new HashSet<>();
		for(WorldObject obj: keepAlives) {
			ServerLevel level = (ServerLevel) obj.getLevel();
			if(level != null)
				loadedLevels.add(level);
		}
		
		for(ServerLevel level: loadedLevels)
			level.update(getEntities(level), delta);
		
		super.update(delta);
	}
	
	
	/*  --- WORLD MANAGEMENT --- */
	
	
	public WorldData getWorldUpdate() { return new WorldData(gameTime, this.daylightOffset, Config.DaylightCycle.get()); }
	public void broadcastWorldUpdate() { server.broadcast(getWorldUpdate()); }
	
	public void setTimeOfDay(float daylightOffset) {
		this.daylightOffset = daylightOffset % TimeOfDay.SECONDS_IN_DAY;
		broadcastWorldUpdate();
	}
	public float changeTimeOfDay(float deltaOffset) {
		float newOff = this.daylightOffset;
		if(deltaOffset < 0)
			newOff += TimeOfDay.SECONDS_IN_DAY;
		newOff = (newOff + deltaOffset) % TimeOfDay.SECONDS_IN_DAY;
		setTimeOfDay(newOff);
		return getDaylightOffset();
	}
	
	@Override protected boolean doDaylightCycle() { return Config.DaylightCycle.get(); }
	
	@Override
	public boolean worldLoaded() { return worldLoaded; }
	
	@Override
	public void createWorld(int width, int height) {
		worldLoaded = false;
		levelGenerator = new LevelGenerator(MathUtils.random.nextLong(), width, height);
		
		ProgressPrinter logger = new ProgressPrinter();
		
		logger.pushMessage("");
		clearLevels();
		int numLevels = 1;
		int minDepth = 0;
		for(int i = 0; i < numLevels; i++) {
			logger.editMessage("Loading level "+(i+1)+"/"+numLevels+"...");
			addLevel(new ServerLevel(this, i + minDepth, levelGenerator));
		}
		logger.popMessage();
		
		worldLoaded = true;
	}
	
	// load world method here, param worldname
	
	// save world method here, param worldname
	
	@Override
	public void exitWorld(boolean save) {
		if(!worldLoaded) return;
		// dispose of level/world resources
		keepAlives.clear();
		clearLevels();
		levelGenerator = null;
		server.stop();
		worldLoaded = false;
		//spawnTile = null;
	}
	
	
	/*  --- LEVEL MANAGEMENT --- */
	
	
	
	
	
	/*  --- ENTITY MANAGEMENT --- */
	
	
	@Override
	public void setEntityLevel(@NotNull Entity e, @NotNull Level level) {
		ServerLevel previous = getEntityLevel(e);
		super.setEntityLevel(e, level);
		ServerLevel newLevel = getEntityLevel(e);
		
		if(!Objects.equals(previous, newLevel)) {
			if(previous != null)
				server.broadcast(new EntityRemoval(e), previous, (ServerEntity) e);
			if(newLevel != null)
				server.broadcast(new EntityAddition(e), newLevel, (ServerEntity) e);
		}
	}
	
	@Override
	public boolean isKeepAlive(WorldObject obj) {
		return keepAlives.contains(obj);
	}
	
	
	/*  --- PLAYER MANAGEMENT --- */
	
	
	@NotNull
	public ServerPlayer addPlayer(String playerName) {
		ServerPlayer player = new ServerPlayer(playerName);
		
		keepAlives.add(player);
		
		respawnPlayer(player);
		
		if(GameCore.debug)
			player.getInventory().addItem(new ToolItem(ToolType.Shovel, Material.Gem));
		return player;
	}
	
	// called on player death
	public void despawnPlayer(ServerPlayer player) {
		server.sendToPlayer(player, new EntityRemoval(player));
		removeFromLevels(player);
	}
	
	public void respawnPlayer(ServerPlayer player) {
		player.reset();
		
		ServerLevel level = getLevel(0);
		
		if(level == null)
			throw new NullPointerException("Surface level found to be null while attempting to spawn player.");
		
		// find a good spawn location near the middle of the map
		Rectangle spawnBounds = levelGenerator.getSpawnArea(new Rectangle());
		
		level.spawnMob(player, spawnBounds);
	}
	
	public void removePlayer(ServerPlayer player) {
		keepAlives.remove(player);
		
	}
	
	
	/*  --- GET METHODS --- */
	
	
	public Array<WorldObject> getKeepAlives(@NotNull Level level) {
		Array<WorldObject> keepAlives = new Array<>();
		for(WorldObject obj: this.keepAlives)
			if(level.equals(obj.getLevel()))
				keepAlives.add(obj);
		
		return keepAlives;
	}
	
	public GameServer getServer() { return server; }
	
	@Override
	public ServerLevel getLevel(int depth) { return (ServerLevel) super.getLevel(depth); }
	
	@Override
	public ServerLevel getEntityLevel(Entity e) { return (ServerLevel) super.getEntityLevel(e); }
	
	@Override
	public String toString() { return "ServerWorld"; }
}
