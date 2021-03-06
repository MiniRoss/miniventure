package miniventure.game.server;

import javax.swing.Timer;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import miniventure.game.GameCore;
import miniventure.game.GameProtocol;
import miniventure.game.chat.InfoMessage;
import miniventure.game.chat.InfoMessageBuilder;
import miniventure.game.chat.InfoMessageLine;
import miniventure.game.chat.MessageBuilder;
import miniventure.game.chat.command.Command;
import miniventure.game.chat.command.CommandInputParser;
import miniventure.game.item.Hands;
import miniventure.game.item.Inventory;
import miniventure.game.item.Item;
import miniventure.game.item.ItemStack;
import miniventure.game.item.Recipe;
import miniventure.game.item.Recipes;
import miniventure.game.util.ArrayUtils;
import miniventure.game.world.Chunk;
import miniventure.game.world.Chunk.ChunkData;
import miniventure.game.world.Level;
import miniventure.game.world.ServerLevel;
import miniventure.game.world.entity.Entity;
import miniventure.game.world.entity.ServerEntity;
import miniventure.game.world.entity.mob.Player;
import miniventure.game.world.entity.mob.ServerPlayer;
import miniventure.game.world.tile.Tile;
import miniventure.game.world.tile.TileType.TileTypeEnum;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GameServer implements GameProtocol {
	
	private class PlayerData {
		final Connection connection;
		@NotNull final ServerPlayer player;
		final InfoMessageBuilder toClientOut, toClientErr;
		boolean op;
		final Timer validationTimer;
		
		PlayerData(Connection connection, @NotNull ServerPlayer player) {
			this.connection = connection;
			this.player = player;
			
			toClientOut = new InfoMessageBuilder(text -> new InfoMessageLine(Color.WHITE, text));
			toClientErr = new InfoMessageBuilder(toClientOut, text -> new InfoMessageLine(Color.RED, text));
			
			op = connection.getRemoteAddressTCP().getAddress().isLoopbackAddress();
			
			validationTimer = new Timer(5000, e -> sendEntityValidation(this));
			validationTimer.setRepeats(true);
		}
	}
	
	private final HashMap<Connection, PlayerData> connectionToPlayerDataMap = new HashMap<>();
	private final HashMap<ServerPlayer, Connection> playerToConnectionMap = new HashMap<>();
	
	private Server server;
	
	public GameServer(boolean standalone) {
		server = new Server(writeBufferSize*5, objectBufferSize) {
			@Override
			public void start() {
				Thread thread = new Thread(this, "Server");
				thread.setUncaughtExceptionHandler((t, ex) -> {
					t.getThreadGroup().uncaughtException(t, ex);
					close();
				});
				thread.start();
			}
		};
		GameProtocol.registerClasses(server.getKryo());
		
		addListener(/*new LagListener(lagMin, lagMax, */new Listener() {
			@Override
			public void received (Connection connection, Object object) {
				ServerWorld world = ServerCore.getWorld();
				
				if(object instanceof Login) {
					System.out.println("server received login");
					Login login = (Login) object;
					
					if(login.version.compareTo(GameCore.VERSION) != 0) {
						connection.sendTCP(new LoginFailure("Required version: "+GameCore.VERSION));
						return;
					}
					
					String name = login.username;
					
					for(ServerPlayer player: playerToConnectionMap.keySet()) {
						if(player.getName().equals(name)) {
							connection.sendTCP(new LoginFailure("Username already exists."));
							return;
						}
					}
					
					
					connection.sendTCP(world.getWorldUpdate());
					
					// prepare level
					ServerLevel level = world.getLevel(0);
					if(level != null) {
						ServerPlayer player = world.addPlayer(name);
						PlayerData playerData = new PlayerData(connection, player);
						connectionToPlayerDataMap.put(connection, playerData);
						playerToConnectionMap.put(player, connection);
						Array<Chunk> playerChunks = level.getAreaChunks(player.getCenter(), Level.X_LOAD_RADIUS, Level.Y_LOAD_RADIUS, true, true);
						connection.sendTCP(new LevelData(level));
						Rectangle entityRect = null;
						for(Chunk chunk: playerChunks) {
							connection.sendTCP(new ChunkData(chunk, level));
							entityRect = entityRect == null ? chunk.getBounds() : entityRect.merge(chunk.getBounds());
						}
						connection.sendTCP(new SpawnData(new EntityAddition(player), player));
						for(Entity e: level.getOverlappingEntities(entityRect, player))
							connection.sendTCP(new EntityAddition(e));
						
						System.out.println("Server: new player successfully connected: "+player.getName());
						
						playerData.validationTimer.start();
						
						broadcast(new Message(player.getName()+" joined the server.", Color.ORANGE), false, player);
					}
					else connection.sendTCP(new LoginFailure("Server world is not initialized."));
					
					return;
				}
				
				PlayerData clientData = connectionToPlayerDataMap.get(connection);
				if(clientData == null) {
					System.err.println("server received packet from unknown client "+connection.getRemoteAddressTCP().getHostString()+"; ignoring packet "+object);
					return;
				}
				
				ServerPlayer client = clientData.player;
				
				if(object instanceof ChunkRequest) {
					//System.out.println("server received chunk request");
					ChunkRequest request = (ChunkRequest) object;
					ServerLevel level = client.getLevel(); // assumes player wants for current level
					if(level != null) {
						Chunk chunk = level.getChunk(request.x, request.y);
						//System.out.println("server sending back chunk "+chunk);
						connection.sendTCP(new ChunkData(chunk, level));
						Array<Entity> newEntities = level.getOverlappingEntities(chunk.getBounds());
						for(Entity e: newEntities)
							connection.sendTCP(new EntityAddition(e));
					} else
						System.err.println("Server could not satisfy chunk request, player level is null");
				}
				
				forPacket(object, EntityRequest.class, req -> {
					Entity e = world.getEntity(req.eid);
					if(e != null)
						connection.sendTCP(new EntityAddition(e));
				});
				
				forPacket(object, StatUpdate.class, client::loadStat);
				
				forPacket(object, MovementRequest.class, move -> {
					Vector3 loc = client.getLocation();
					if(move.getMoveDist().len() < 1 && !move.startPos.variesFrom(client)) {
						// move to start pos
						Vector3 start = move.startPos.getPos();
						Vector3 diff = loc.cpy().sub(start);
						client.move(diff);
					}
					// move given dist
					Vector3 moveDist = move.getMoveDist();
					client.move(moveDist);
					// compare against given end pos
					if(move.endPos.variesFrom(client)) {
						connection.sendTCP(new PositionUpdate(client));
					}
					// note that the server will always have the say when it comes to which level the player should be on.
				});
				
				if(object instanceof InteractRequest) {
					InteractRequest r = (InteractRequest) object;
					if(r.playerPosition.variesFrom(client))
						connection.sendTCP(new PositionUpdate(client)); // fix the player's position
					
					client.setDirection(r.dir);
					client.getHands().setSelection(r.hotbarIndex);
					if(r.attack) client.attack();
					else client.interact();
				}
				
				forPacket(object, ItemDropRequest.class, drop -> {
					ItemStack stack = ItemStack.load(drop.stackData);
					int removed = 0;
					while(removed < stack.count && client.getHands().removeItem(stack.item))
						removed++;
					ServerLevel level = client.getLevel();
					if(level == null) return;
					// get target pos, which is one tile in front of player.
					Vector2 targetPos = client.getCenter();
					targetPos.add(client.getDirection().getVector().scl(2)); // adds 2 in the direction of the player.
					for(int i = 0; i < removed; i++)
						level.dropItem(stack.item.copy(), true, client.getCenter(), targetPos);
				});
				
				forPacket(object, InventoryUpdate.class, update -> {
					// so, we need to check the sent over inventory and hotbar data, and make sure that all the items in the sent over match all the items of the server player. 
					Inventory inv = client.getInventory();
					Hands hotbar = client.getHands();
					Array<Item> items = new Array<>(Item.class);
					items.addAll(inv.getItems());
					items.addAll(hotbar.getItems());
					
					// clear inventory and hand of items, load given items, and add them one by one
					inv.loadItems(update.inventory);
					hotbar.loadItems(update.hotbar);
					
					for(Item item: inv.getItems()) {
						if(!items.contains(item, false)) {
							// client sent over item that the server doesn't have
							inv.removeItem(item);
						}
						else items.removeValue(item, false);
					}
					for(Item item: hotbar.getItems()) {
						if(!items.contains(item, false)) {
							// client sent over item that the server doesn't have
							hotbar.removeItem(item);
						}
						else items.removeValue(item, false);
					}
					
					// now check for any remaining items that the server had, but not the client, and add them back
					for(Item item: items.shrink()) {
						hotbar.addItem(item);
					}
					
					connection.sendTCP(new InventoryUpdate(client));
				});
				
				forPacket(object, CraftRequest.class, req -> {
					Recipe recipe = Recipes.recipes[req.recipeIndex];
					Item[] left = recipe.tryCraft(client.getHands());
					if(left != null) {
						ServerLevel level = client.getLevel();
						if(level != null)
							for(Item item : left)
								level.dropItem(item, client.getPosition(), null);
					}
					connection.sendTCP(new InventoryUpdate(client));
				});
				
				if(object.equals(DatalessRequest.Respawn)) {
					//ServerPlayer client = connectionToPlayerDataMap.get(connection).player;
					world.respawnPlayer(client);
					connection.sendTCP(new SpawnData(new EntityAddition(client), client));
				}
				
				if(object.equals(DatalessRequest.Tile)) {
					Level level = client.getLevel();
					if(level != null) {
						Tile t = level.getClosestTile(client.getInteractionRect());
						connection.sendTCP(new Message(client+" looking at "+(t==null?null:t.toLocString()), Color.WHITE));
					}
				}
				
				forPacket(object, Message.class, msg -> {
					//System.out.println("server: executing command "+msg.msg);
					CommandInputParser.executeCommand(msg.msg, client, clientData.toClientOut, clientData.toClientErr);
					InfoMessage output = clientData.toClientOut.flushMessage();
					if(output != null)
						connection.sendTCP(output);
				});
				
				forPacket(object, TabRequest.class, request -> {
					String text = request.manualText;
					if(text.split(" ").length == 1) {
						// hasn't finished entering command name, autocomplete that
						Array<String> matches = new Array<>(String.class);
						for(Command c: Command.values()) {
							String name = c.name();
							if(text.length() == 0 || name.toLowerCase().contains(text.toLowerCase()))
								matches.add(name);
						}
						
						if(matches.size == 0) return;
						
						if(matches.size == 1) {
							connection.sendTCP(new TabResponse(request.manualText, matches.get(0)));
							return;
						}
						
						matches.sort(String::compareToIgnoreCase);
						
						if(request.tabIndex < 0)
							connection.sendTCP(new Message(ArrayUtils.arrayToString(matches.shrink(), "", "", ", "), Color.WHITE));
						else {
							// actually autocomplete
							connection.sendTCP(new TabResponse(request.manualText, matches.get(request.tabIndex % matches.size)));
						}
					}
				});
				
				forPacket(object, SelfHurt.class, hurt -> {
					// assumed that the client has hurt itself in some way
					client.attackedBy(client, null, hurt.dmg);
				});
			}
			
			/*@Override
			public void connected(Connection connection) {
				System.out.println("new connection: " + connection.getRemoteAddressTCP().getHostString());
			}*/
			
			@Override
			public void disconnected(Connection connection) {
				PlayerData data = connectionToPlayerDataMap.get(connection);
				if(data == null) return;
				data.validationTimer.stop();
				ServerPlayer player = data.player;
				player.remove();
				player.getWorld().removePlayer(player);
				connectionToPlayerDataMap.remove(connection);
				playerToConnectionMap.remove(player);
				broadcast(new Message(player.getName()+" left the server.", Color.ORANGE));
				//System.out.println("server disconnected from client: " + connection.getRemoteAddressTCP().getHostString());
				
				if(connectionToPlayerDataMap.size() == 0 && !standalone)
					ServerCore.quit();
			}
		});//);
		
		server.start();
	}
	
	public void addListener(Listener listener) { server.addListener(listener); }
	
	public boolean startServer() { return startServer(GameProtocol.PORT); }
	public boolean startServer(int port) {
		try {
			server.bind(port);
		} catch(IOException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public void sendToPlayer(@NotNull ServerPlayer player, Object obj) {
		Connection c = playerToConnectionMap.get(player);
		if(c != null) {
			c.sendTCP(obj);
		}
	}
	
	// levelMask is the level a player must be on to receive this data.
	public void broadcast(Object obj, Level levelMask, @NotNull ServerEntity... exclude) {
		if(levelMask == null) return; // no level, no packet.
		
		HashSet<ServerPlayer> players = new HashSet<>(playerToConnectionMap.keySet());
		HashSet<ServerEntity> excluded = new HashSet<>(Arrays.asList(exclude));
		//noinspection SuspiciousMethodCalls
		players.removeAll(excluded);
		players.removeIf(player -> !levelMask.equals(player.getLevel()));
		
		broadcast(obj, true, players);
	}
	public void broadcast(Object obj, @NotNull ServerEntity excludeEntity) {
		if(excludeEntity instanceof ServerPlayer)
			broadcast(obj, false, (ServerPlayer)excludeEntity);
		else
			broadcast(obj);
	}
	public void broadcast(Object obj, @NotNull ServerPlayer... excludedPlayers) { broadcast(obj, false, excludedPlayers); }
	public void broadcast(Object obj, boolean includeGiven, @NotNull ServerPlayer... players) { broadcast(obj, includeGiven, new HashSet<>(Arrays.asList(players))); }
	private void broadcast(Object obj, boolean includeGiven, @NotNull HashSet<ServerPlayer> players) {
		if(!includeGiven) {
			HashSet<ServerPlayer> playerSet = new HashSet<>(playerToConnectionMap.keySet());
			playerSet.removeAll(players);
			players = playerSet;
		}
		
		for(ServerPlayer p: players)
			playerToConnectionMap.get(p).sendTCP(obj);
	}
	
	@Nullable
	public ServerPlayer getPlayerByName(String name) {
		for(ServerPlayer player: playerToConnectionMap.keySet())
			if(player.getName().equalsIgnoreCase(name))
				return player;
		
		return null;
	}
	
	public boolean isAdmin(@Nullable ServerPlayer player) {
		if(player == null) return true; // from server command prompt
		PlayerData data = connectionToPlayerDataMap.get(playerToConnectionMap.get(player));
		if(data == null) return false; // unrecognized player (should never happen)
		return data.op;
	}
	
	public boolean setAdmin(@NotNull ServerPlayer player, boolean op) {
		PlayerData data = connectionToPlayerDataMap.get(playerToConnectionMap.get(player));
		if(data == null) return false; // unrecognized player (should never happen)
		data.op = op;
		return true;
	}
	
	public void sendMessage(@Nullable ServerPlayer sender, ServerPlayer reciever, String msg) {
		sendToPlayer(reciever, getMessage(sender, msg));
	}
	public void broadcastMessage(@Nullable ServerPlayer sender, String msg) {
		broadcast(getMessage(sender, msg));
	}
	
	public Message getMessage(@Nullable ServerPlayer sender, String msg) {
		if(sender == null) return new Message("Server: "+msg, Color.LIGHT_GRAY);
		return new Message(sender.getName()+": "+msg, Color.WHITE);
	}
	
	private void sendEntityValidation(@NotNull PlayerData pData) {
		ServerPlayer player = pData.player;
		if(!pData.connection.isConnected()) {
			System.err.println("Server not sending entity validation to player "+player.getName()+" because player has disconnected; stopping validation timer.");
			pData.validationTimer.stop();
			return;
		}
		
		ServerLevel level = player.getLevel();
		if(level == null) {
			System.out.println("Server: Level of player "+player.getName()+" is null during entity validation attempt, skipping validation.");
			return;
		}
		
		
		Rectangle area = null;
		for(Chunk c: level.getAreaChunks(player.getCenter(), Level.X_LOAD_RADIUS+1, Level.Y_LOAD_RADIUS+1, true, false)) {
			if(area == null)
				area = c.getBounds();
			else
				area.merge(c.getBounds());
		}
		
		//Array<Entity> entities = level.getOverlappingEntities(area, player);
		EntityValidation validation = new EntityValidation(level, area, player);
		pData.connection.sendTCP(validation);
	}
	
	public void playEntitySound(String soundName, Entity source) { playEntitySound(soundName, source, true); }
	public void playEntitySound(String soundName, Entity source, boolean broadcast) {
		if(source instanceof Player)
			playGenericSound("player/"+soundName, source.getCenter());
		//else if(source instanceof MobAi)
		//	playSound("entity/"+((MobAi)source).getType().name().toLowerCase()+"/");
		else
			playGenericSound("entity/"+soundName, source.getCenter());
	}
	public void playTileSound(String soundName, Tile tile, TileTypeEnum type) {
		//playGenericSound("tile/"+type+"/"+soundName, tile.getCenter());
		playGenericSound("tile/"+soundName, tile.getCenter());
	}
	public void playGenericSound(String soundName, Vector2 source) {
		for(ServerPlayer player: playerToConnectionMap.keySet()) {
			if(player.getPosition().dst(source) <= GameCore.SOUND_RADIUS)
				playerToConnectionMap.get(player).sendTCP(new SoundRequest(soundName));
		}
	}
	
	public void printStatus(MessageBuilder out) {
		out.println("Server Running: "+(server.getUpdateThread() != null));
		out.println("FPS: " + ServerCore.getFPS());
		out.println("Players connected: "+playerToConnectionMap.size());
		for(PlayerData pd: connectionToPlayerDataMap.values()) {
			out.print("     Player \""+pd.player.getName()+"\" ");
			if(pd.op) out.print("(admin) ");
			out.print(pd.player.getLocation(true));
			out.println();
		}
	}
	
	void stop() { server.stop(); }
}
