package me.com.hutattedonmyarm.Mita.MitaBase;
import java.lang.IllegalArgumentException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import lib.net.darqy.SQLib.SQLite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.*;
import net.minecraft.server.MobEffect;
import net.minecraft.server.Packet41MobEffect;

public class MitaBase extends JavaPlugin implements Listener {
	private static Logger logger = Bukkit.getServer().getLogger();
	
	private String pluginPrefix = "[MitaBase] ";
	private static String databaseName = "MitaBaseDB.db";
	private String console_last_messaged = "";
	
	private ConsoleCommandSender console;
	SimpleCommandMap cm;
	
	private boolean cmdlogger = true;
	
	private Permission permission;
	private static SQLite sqlite = new SQLite(logger, "[MitaBase]", databaseName, "plugins/MitaBase/");
	private Chat chat = null;

	private void setupDatabase() {
		if (!sqlite.tableExists("users")) {
			String query = "CREATE TABLE users (userid INTEGER PRIMARY KEY, username TEXT, numofhomes INTEGER, afk INTEGER, muted INTEGER, jailed INTEGER, jaileduntil TEXT, pvp INTEGER, last_messaged INTEGER, vanished INTEGER, socialspy INTEGER, nick TEXT, last_seen TEXT, lastLocX INTEGER, lastLocY INTEGER, LastLocZ INTEGER, lastWorld TEXT)";
			sqlite.modifyQuery(query);
		}
		if (!sqlite.tableExists("worlds")) {
			String query = "CREATE TABLE worlds (worldid INTEGER PRIMARY KEY, worldname TEXT, mobdmg INTEGER, boom INTEGER)";
			sqlite.modifyQuery(query);
		}
		if(!sqlite.tableExists("homes")) {
			String query = "CREATE TABLE homes (homeid INTEGER PRIMARY KEY, homename TEXT, locX REAL, locY REAL, locZ REAL, world TEXT, userid INTEGER)";
			sqlite.modifyQuery(query);
		}
		if(!sqlite.tableExists("warps")) {
			String query = "CREATE TABLE warps (warpid INTEGER PRIMARY KEY, warpname TEXT, locX REAL, locY REAL, locZ REAL, world TEXT)";
			sqlite.modifyQuery(query);
		}
		if(!sqlite.tableExists("chests")) {
			String query = "CREATE TABLE chests (chestid INTEGER PRIMARY KEY, locX REAL, locY REAL, locZ REAL, world TEXT, gm INTEGER)";
			sqlite.modifyQuery(query);
		}
		if(!sqlite.tableExists("jails")) {
			String query = "CREATE TABLE jails (jailid INTEGER PRIMARY KEY, jailname TEXT, locX REAL, locY REAL, locZ REAL, world TEXT)";
			sqlite.modifyQuery(query);
		}
		if(!sqlite.tableExists("warnings")) {
			String query = "CREATE TABLE warnings (warningid INTEGER PRIMARY KEY, userid INTEGER, date TEXT, reason TEXT, by INTEGER, level INTEGER)";
			sqlite.modifyQuery(query);
		}
		if(!sqlite.tableExists("mail")) {
			String query = "CREATE TABLE mail (mailid INTEGER PRIMARY KEY, senderid INTEGER, receiverid INTEGER, message TEXT)";
			sqlite.modifyQuery(query);
		}
		if(!sqlite.tableExists("bans")) {
			String query = "CREATE TABLE bans (banid INTEGER PRIMARY KEY, playerid INTEGER, by INTEGER, reason TEXT, date TEXT)";
			sqlite.modifyQuery(query);
		}
		if(!sqlite.tableExists("tpa")) {
			String query = "CREATE TABLE tpa (tpaid INTEGER PRIMARY KEY, senderid INTEGER, receiverid INTEGER)";
			sqlite.modifyQuery(query);
		}
		
	}
	private boolean setupPermissions()
    {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            permission = permissionProvider.getProvider();
        }
        return (permission != null);
    }
	private boolean setupChat() {
        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        if(rsp != null) {
        	chat = rsp.getProvider();
        }
        return chat != null;
    }
	private void loadStuff() {
		console.sendMessage(pluginPrefix + ChatColor.RESET + "Enabling MitaBase...");
		console.sendMessage(pluginPrefix + "Setting up...");
		setupDatabase();
	    setupPermissions();
	    setupChat();
	    cmdlogger = getConfig().getBoolean("command_logger");
	    cm = new SimpleCommandMap(getServer());
	    //cm.registerAll(fallbackPrefix, commands);
	    console.sendMessage(pluginPrefix + "Scanning for worlds...");
	    
	    //Getting all worlds and add them to the DB if they don't exist 
	    List<World> worlds = Bukkit.getServer().getWorlds();
	    for(int i = 0; i < worlds.size(); i++) {
	    	try {
	    		ResultSet rs = sqlite.readQuery("SELECT worldid FROM worlds WHERE worldname = '" + worlds.get(i).getName() + "'");
				if(rs != null && !rs.next()) { //World doesn't exist in DB
					sqlite.modifyQuery("INSERT INTO worlds (worldname, mobdmg, boom) VALUES ('" + worlds.get(i).getName() + "', '1', '1')");
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
	    }
	    console.sendMessage(pluginPrefix + ChatColor.GREEN + "Found " + worlds.size() + " worlds");
	    
	    //Set the spawnpoint in the config to the spawnof the mainworld if it's not set
	    if(getConfig().getString("spawn.world") == null) {
	    	getConfig().options().copyDefaults(true);
	    	getConfig().addDefault("spawn.world", Bukkit.getServer().getWorlds().get(0).getName());
	    	getConfig().addDefault("spawn.x", Bukkit.getServer().getWorlds().get(0).getSpawnLocation().getX());
	    	getConfig().addDefault("spawn.y", Bukkit.getServer().getWorlds().get(0).getSpawnLocation().getY());
	    	getConfig().addDefault("spawn.z", Bukkit.getServer().getWorlds().get(0).getSpawnLocation().getZ());
	    	saveConfig();
	    }
	    saveDefaultConfig();
	}
	private void noPermission(CommandSender sender, Command cmd, String args[]) {
		sender.sendMessage(ChatColor.RED + "You don't have permission to do that. This incident will be logged!");
		String argString = "";
		for(int i = 0; i < args.length; i++) {
			argString += args[i] + " ";
		}
		console.sendMessage(pluginPrefix + sender.getName() + " was denied access to command /" + cmd.getLabel() + " " + argString);
		Bukkit.getServer().broadcast(sender.getName() + " was denied access to command /" + cmd.getLabel() + " " + argString, "MitaBase.watchPerms");
	}
	private ItemStack parseMaterial(String itemString, int amount) {
		ItemStack is = null;
		String matString = "";
		String dmgString = "";
		if(itemString.contains(":") && itemString.length() != itemString.indexOf(":")+1) { //String contains ':', but it's not the last character. So we're dealing with damage values
			matString = itemString.split(":")[0];
			dmgString = itemString.split(":")[1];
		} else {
			matString = itemString;
		}
		Material m = null;
		try {
			m = Material.getMaterial(Integer.parseInt(matString)); //is it the ID pr the name? We just try parsing it as ID, if it fails it seems to be a name
		} catch (Exception e) {
			m = Material.getMaterial(matString.toUpperCase());
		}
		if (m == null) {
			return null;
		}
		if (!dmgString.equals("")) {
			short dmg;
			try {
				dmg = Short.parseShort(dmgString);
				is = new ItemStack(m, amount, dmg);
			} catch (Exception e) {
				return null;
			}
		} else {
			is = new ItemStack(m, amount);
		}
		return is;
	}
	private void give(CommandSender sender, String[] args, Command cmd) {
		Player p = null;
		if(sender instanceof Player) {
			p = (Player) sender;
		}
		if(p == null || p.hasPermission("MitaBase.give")) {
			/* Command possibilities:
			 * /give [player] <item> [amount]
			 * 1) No arguments
			 * 2) 1 argument: item
			 * 3) 2 arguments: player & item (Console: Yes)
			 * 4) 2 arguments: item & amount
			 * 5) 3 arguments: player, item, amount (Console: Yes)
			 */
			if (args.length == 0) { //1)
				sender.sendMessage(cmd.getUsage());
			} else if(args.length == 1) { //2
				if(p != null) {
					//First we need to parse the Item.. damage, etc...
					String itemString = args[0];
					ItemStack is = parseMaterial(itemString, 64);
					if(is == null) {
						p.sendMessage(ChatColor.RED + "Material " + itemString + " not found");
						return;
					}
					p.getInventory().addItem(is);
				} else {
					playerOnly(sender);
				}
			} else if (args.length == 2) { //3) and 4)
				Player p2 = Bukkit.getPlayer(args[0]);
				if (p2 != null) { //3)
					if (p  == null || p.hasPermission("MitaBase.give")) {
						String itemString = args[1];
						ItemStack is = parseMaterial(itemString, 64);
						if(is == null) {
							p.sendMessage(ChatColor.RED + "Material " + itemString + " not found");
							return;
						}
						p2.getInventory().addItem(is);
					} else {
						noPermission(sender, cmd, args);
					}
				} else { //4)
					if(Bukkit.getOfflinePlayer(args[0]) == null ) {
						sender.sendMessage(ChatColor.RED + "Player must be online");
					} else {
						if (p != null && p.hasPermission("MitaBase.give")) {
							String itemString = args[0];
							int amount = 64;
							try {
								amount = Integer.parseInt(args[1]);
							} catch (Exception e) {
								p.sendMessage(ChatColor.RED + "Invalid amount " + args[1]);
								return;
							}
							if (amount > 0) {
								ItemStack is = parseMaterial(itemString, amount);
								if (is != null) {
									p.getInventory().addItem(is);
								} else {
									p.sendMessage(ChatColor.RED + "Material " + itemString + " not found");
								}
							} else {
								p.sendMessage(ChatColor.RED + "Invalid amount " + args[1]);
							}
						} else {
							playerOnly(sender);
						}
					}
				}
			} else if (args.length == 3) { //5)
				Player p2 = Bukkit.getPlayer(args[0]);
				if(p == null || p.hasPermission("MitaBase.give")) {
					if (p2 != null) {
						int amount = 64;
						try {
							amount = Integer.parseInt(args[2]);
						} catch (Exception e) {
							sender.sendMessage(ChatColor.RED + "Invalid amount " + args[2]);
							return;
						}
						if(amount > 0) {
							ItemStack is = parseMaterial(args[1], amount);
							p2.getInventory().addItem(is);
						} else {
							sender.sendMessage(ChatColor.RED + "Invalid amount " + args[2]);
							return;
						}
					} else {
						sender.sendMessage("Player " + args[0] + " not found");
					}
				} else {
					noPermission(sender, cmd, args);
				}
			}
		} else {
			noPermission(sender, cmd, args);
		}
	}
	private void setHome(CommandSender sender, String[] args, Command cmd) {
		Player p = null;
		if(sender instanceof Player) {
			p = (Player) sender;
		}
		if(p == null) {
			playerOnly(sender);
		} else if (!p.hasPermission("MitaBase.sethome")){
			noPermission(sender, cmd, args);
		} else {
			//Check the amount of homes this user can set
			ResultSet rs = sqlite.readQuery("SELECT userid, numofhomes FROM users WHERE username = '" + p.getName() + "'");
			int maxNumHomes = 0;
			int userid = 0;
			try {
				maxNumHomes = rs.getInt("numofhomes");
				userid = rs.getInt("userid");
			} catch(Exception e){
				e.printStackTrace();
			}
			if(maxNumHomes == 0){ //Using the default value
				maxNumHomes = getConfig().getInt("max_num_of_homes");
			}
			//Now we check how many homes are already set
			rs = sqlite.readQuery("SELECT COUNT(*) AS numHomes FROM users, homes WHERE username = '" + p.getName() + "' AND users.userid = homes.userid");
			try {
				int ctr = rs.getInt("numHomes");
				if(ctr < maxNumHomes || p.hasPermission("MitaBase.unlimitedHomes")){
					String hname = "";
					if(args.length > 0) hname = args[0];
					//Check if a home with that name already exists
					rs = sqlite.readQuery("SELECT COUNT(*) AS numHomesWithThatName FROM users, homes WHERE users.username = '" + p.getName() + "' AND homename = '" + hname + "' AND homes.userid = users.userid");
					int nhwn = 0;
					try {
						nhwn = rs.getInt("numHomesWithThatName");
					} catch (Exception e) {
						e.printStackTrace();
					}
					if(nhwn > 0) {
						//p.sendMessage(ChatColor.RED + "You already have a home with that name. Please choose a different name");
						//Update the coordinates
						sqlite.modifyQuery("UPDATE homes SET locX='" + p.getLocation().getX() + "', locY='" + p.getLocation().getY() + "', locZ='" + p.getLocation().getX() + "', world='" + p.getWorld().getName() + "' WHERE homes.homename = '" + hname + "' AND homes.userid = homes.userid = (SELECT userid FROM users WHERE username = '" + p.getName() + "')");
						p.sendMessage(ChatColor.GREEN + "Succesfully updated your home");
					} else {
						//Set the new home... finally!
						sqlite.modifyQuery("INSERT INTO homes (homename, locX, locY, locZ, world, userid) VALUES ('" + hname + "','" + p.getLocation().getX() + "','" + p.getLocation().getY() + "','" + p.getLocation().getZ() + "','" + p.getWorld().getName() + "','" + userid + "' )");
						p.sendMessage(ChatColor.GREEN + "Congrats, you now have a home called " + hname + " :)");
					}
				} else {
					p.sendMessage(ChatColor.RED + "You have already set the maximum number of homes");
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
		}
	}
	private boolean preventPlayerFromBreakingOut(Player p) {
		ResultSet rs = sqlite.readQuery("SELECT jailed, jaileduntil FROM users WHERE username='" + p.getName() + "'");
		boolean jailed = false;
		try {
			jailed = rs.getBoolean("jailed");
		} catch (Exception e) {
			e.printStackTrace();
		}
		if(jailed) {
			p.sendMessage(ChatColor.RED + "You're not allowed to do that while in jail");
		}
		return jailed;
		//We can't cancel the event here since we don't know if it's cancellable or not
	}
	private void playerOnly (CommandSender sender) {
		sender.sendMessage(ChatColor.RED + "Only players can use this command");
	}
    private String colorize(String s){
    	if(s == null) return null;
    	return s.replaceAll("&([0-9a-f])", "\u00A7$1");
    }
	/**
	 * Get the ID of a player in the database. Returns -1 if an error occured
	 * @param name
	 * @return
	 */
    public static int getPlayerID(String name) {
		if(name == null) {
			return -1;
		}
		ResultSet rs = sqlite.readQuery("SELECT userid FROM users WHERE username = '" + name + "'");
		try {
			return rs.getInt("userid");
		} catch (Exception e) {
			return -1;
		}
	}
    public void onEnable(){
		getServer().getPluginManager().registerEvents(this, this);
		sqlite.open();
		console = Bukkit.getServer().getConsoleSender();
		loadStuff();
	}
	public void onDisable() {
		console.sendMessage(pluginPrefix + "Disabling MitaBase...");
		sqlite.close();
	}
	@EventHandler
	public void playerLogin(PlayerLoginEvent evt) {
		Player p = evt.getPlayer();
		String nick = "";
		ResultSet rs = sqlite.readQuery("SELECT nick FROM users WHERE username = '" + p.getName() + "'");
		try {
			nick = rs.getString("nick");	
		} catch (Exception e) {
		}
		if(!nick.equals("")) {
			p.setDisplayName(colorize(nick));
		}
		sqlite.modifyQuery("UPDATE users SET last_seen = '" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZ").format(new Date()) + "' WHERE username = '" + p.getName() + "'");
	}
	@EventHandler
	public void playerJoin(PlayerJoinEvent evt) {
		Player p = evt.getPlayer();
		ResultSet rs = sqlite.readQuery("SELECT userid FROM users WHERE username = '" + p.getName() + "'");
		try {
			if(rs != null && !rs.next()) { //User doesn't exist in DB, so they joined the first time, We'll add them
				Bukkit.getServer().dispatchCommand(p, "spawn");
				sqlite.modifyQuery("INSERT INTO users (username, numofhomes, afk, muted, pvp, vanished, jailed, socialspy, townid) VALUES ('" + p.getName() + "', 0, 0, 0, 0, 0, 0, 0, '')");
				ChatColor mc = ChatColor.GREEN;
				ChatColor uc = ChatColor.YELLOW;
				try {
					mc = ChatColor.valueOf(getConfig().getString("new_user.message_color"));
					uc = ChatColor.valueOf(getConfig().getString("new_user.username_color"));
				} catch (IllegalArgumentException|NullPointerException e) {
					if(e instanceof NullPointerException) {
						console.sendMessage(pluginPrefix + ChatColor.RED + "No color specified in config in section \"new_user\"");
					} else {
						console.sendMessage(pluginPrefix + ChatColor.RED + "Invalid color in config in section \"new_user\", using default!");
					}
				}
				Bukkit.getServer().broadcastMessage(mc + getConfig().getString("new_user.welcome_message").replace("{username}", uc + p.getDisplayName() + mc));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		for(Player player: getServer().getOnlinePlayers()) {
		       rs = sqlite.readQuery("SELECT vanished FROM users WHERE username = '" + player.getName() + "'");
		       boolean van = false;
				try {
					van = rs.getBoolean("vanished");
				}catch (Exception e) {
					e.printStackTrace();
				}
				if(van) {
					p.hidePlayer(player);
				}
			}
	}
	@EventHandler
	public void playerLogout(PlayerQuitEvent evt){
		Player p = evt.getPlayer();
		sqlite.modifyQuery("UPDATE users SET afk=0, last_seen = '" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZ").format(new Date()) + "', lastLocX = '" + p.getLocation().getBlockX() + "', lastLocY = '" + p.getLocation().getBlockY() + "', lastLocZ = '" + p.getLocation().getBlockZ() + "', lastWorld = '" + p.getLocation().getWorld().getName() + "' WHERE username = '" + p.getName() + "'");
	}
	@EventHandler (priority = EventPriority.MONITOR)
	public void playerDeath(PlayerDeathEvent evt) {
		Player p = evt.getEntity();
		sqlite.modifyQuery("UPDATE users SET lastLocX = '" + p.getLocation().getBlockX() + "', lastLocY = '" + p.getLocation().getBlockY() + "', lastLocZ = '" + p.getLocation().getBlockZ() + "', lastWorld = '" + p.getLocation().getWorld().getName() + "' WHERE username = '" + p.getName() + "'");
	}
	@EventHandler
	public void playerCommand(PlayerCommandPreprocessEvent evt) {
		if(cmdlogger) {
			console.sendMessage(ChatColor.GRAY + "Player " + evt.getPlayer().getName() + " entered command: " + evt.getMessage());
		}
		for(Player player : getServer().getOnlinePlayers()) {
			ResultSet rs = sqlite.readQuery("SELECT socialspy FROM users WHERE username = '" + player.getName() + "'");
			try {
				if(rs.getBoolean("socialspy") && player.hasPermission("MitaBase.socialspy")) {
					player.sendMessage(ChatColor.GRAY + evt.getPlayer().getName() + " entered command: " + evt.getMessage());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		List<String> commands = getConfig().getStringList("allowed_commands_in_jail");
		String m = evt.getMessage().substring(1, evt.getMessage().length()).split(" ")[0];
		Command co = getServer().getPluginCommand(m);
		String cmd = null;
		if(co != null)cmd = co.getName();
		if(cmd != null && !commands.contains(cmd) && preventPlayerFromBreakingOut(evt.getPlayer())) {
			evt.setCancelled(true);
		}
	}
	@EventHandler
	public void playerTP(PlayerTeleportEvent evt) {
		Player p = evt.getPlayer();
		Location l = evt.getFrom();
		sqlite.modifyQuery("UPDATE users SET lastLocX = '" + l.getBlockX() + "', lastLocY = '" + l.getBlockY() + "', lastLocZ = '" + l.getBlockZ() + "', lastWorld = '" + l.getWorld().getName() + "' WHERE username = '" + p.getName() + "'");
	}
	@EventHandler
	public void playerChat(AsyncPlayerChatEvent evt) {
		ResultSet rs = sqlite.readQuery("SELECT muted FROM users WHERE username = '"+ evt.getPlayer().getName() + "'");
		try {
			if(rs.getBoolean("muted")) {
				evt.setCancelled(true);
				evt.getPlayer().sendMessage(ChatColor.RED + "You have been muted");
				console.sendMessage(ChatColor.BLUE + "Player " + evt.getPlayer().getName() + " tried to speak while being muted");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		if(!evt.isCancelled() && evt.getPlayer().hasPermission("MitaBase.coloredchat")) {
			evt.setMessage(colorize(evt.getMessage()));
		}
	}
	@EventHandler(priority = EventPriority.LOWEST)	
	public void playerOuchByMob(EntityDamageByEntityEvent evt) {
		World w = evt.getEntity().getWorld();
		if(evt.getDamager().getType().equals(EntityType.PLAYER)) {
			Player p1 = (Player) evt.getEntity();
			Player p2 = (Player) evt.getDamager();
			ResultSet rs = sqlite.readQuery("SELECT pvp FROM users WHERE username = '" + p1.getName() + "'");
			boolean pvp1 = false;
			try {
				pvp1 = rs.getBoolean("pvp");
			} catch (Exception e) {
				e.printStackTrace();
			}
			rs = sqlite.readQuery("SELECT pvp FROM users WHERE username = '" + p2.getName() + "'");
			boolean pvp2 = false;
			try {
				pvp2 = rs.getBoolean("pvp");
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(pvp1 && pvp2) {
				evt.setCancelled(false);
			} else {
				evt.setCancelled(true);
			}
			console.sendMessage("PVP1 " + pvp1 + " PVP2 " + pvp2);
	
		} else {
			ResultSet rs = sqlite.readQuery("SELECT mobdmg FROM worlds WHERE worldname = '" + w.getName() + "'");
			boolean dmg = true;
			try {
				dmg = rs.getBoolean("mobdmg");
			} catch (Exception e) {
				e.printStackTrace();
			}
				evt.setCancelled(!dmg);
		}
	}
	@EventHandler(priority = EventPriority.MONITOR)
	public void blockPlaced(BlockPlaceEvent evt) {
		Player player = evt.getPlayer();
		evt.setCancelled(preventPlayerFromBreakingOut(player));
		Block b = evt.getBlockPlaced();
		Location l = b.getLocation();
		Location lo = b.getLocation();
		
		//This is weird, but works... I tested it! But code needs to be improved!
		l.setX(lo.getX() + 1);
		Block p = l.getBlock();
		l.setX(lo.getX() - 1);
		Block q = l.getBlock();
		l.setZ(lo.getZ() + 1);
		l.setX(l.getX() + 1);
		Block r = l.getBlock();
		l.setZ(lo.getZ() - 1);
		Block s = l.getBlock();
		GameMode g = null;
		if(p.getType().equals(Material.CHEST)) {
			ResultSet rs = sqlite.readQuery("SELECT gm FROM chests WHERE locX = '" + p.getX() + "' AND locY = '" + p.getY() + "' AND locZ = '" + p.getZ() + "' AND world = '" + p.getWorld().getName() + "'");
			try {
				g = GameMode.getByValue(rs.getInt("gm"));
			} catch (Exception e) {
				
			}
		} else if(q.getType().equals(Material.CHEST)) {
			ResultSet rs = sqlite.readQuery("SELECT gm FROM chests WHERE locX = '" + q.getX() + "' AND locY = '" + q.getY() + "' AND locZ = '" + q.getZ() + "' AND world = '" + q.getWorld().getName() + "'");
			try {
				g = GameMode.getByValue(rs.getInt("gm"));
			} catch (Exception e) {
				
			}
		} else if(r.getType().equals(Material.CHEST)) {
			ResultSet rs = sqlite.readQuery("SELECT gm FROM chests WHERE locX = '" + r.getX() + "' AND locY = '" + r.getY() + "' AND locZ = '" + r.getZ() + "' AND world = '" + r.getWorld().getName() + "'");
			try {
				g = GameMode.getByValue(rs.getInt("gm"));
			} catch (Exception e) {
				
			}
		} else if(s.getType().equals(Material.CHEST)) {
			ResultSet rs = sqlite.readQuery("SELECT gm FROM chests WHERE locX = '" + s.getX() + "' AND locY = '" + s.getY() + "' AND locZ = '" + s.getZ() + "' AND world = '" + s.getWorld().getName() + "'");
			try {
				g = GameMode.getByValue(rs.getInt("gm"));
			} catch (Exception e) {
				
			}
		}
		if (g != null && !g.equals(player.getGameMode())) {
			evt.setCancelled(true);
			player.sendMessage(ChatColor.RED + "You've got the wrong gamemode to place a doublechest");
		} else {
			sqlite.modifyQuery("INSERT INTO chests (locX, locY, locZ, world, gm) VALUES ('" + b.getX()  + "', '" + b.getY()  + "', '" + b.getZ()  + "', '" + b.getWorld().getName() + "', '" + player.getGameMode().getValue() + "')");
		}
	}
	@EventHandler(priority = EventPriority.MONITOR)
	public void blockBroke(BlockBreakEvent evt) {
		Player player = evt.getPlayer();
		evt.setCancelled(preventPlayerFromBreakingOut(player));
		Block b = evt.getBlock();
		if(b.getType().equals(Material.CHEST)) {
			sqlite.modifyQuery("DELETE FROM chests WHERE locX = '" + b.getX()  + "' AND locY = '" + b.getY()  + "' AND locZ =  '" + b.getZ()  + "' AND world = '" + b.getWorld().getName() + "'");	
		}
		
	}
	@EventHandler(priority = EventPriority.LOWEST)
	public void endermanStoleBlock (EntityChangeBlockEvent evt) {
		if(evt.getEntityType().equals(EntityType.ENDERMAN)) {
			ResultSet rs = sqlite.readQuery("SELECT boom FROM worlds WHERE worldname = '" + evt.getBlock().getWorld().getName() + "'");
			boolean boom = true;
			try {
				boom = rs.getBoolean("boom");
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(!boom) evt.setCancelled(true);
		}
	}
	@EventHandler(priority = EventPriority.HIGHEST)
	public void enterVehicle(VehicleEnterEvent evt) {
		try {
			Player player = (Player) evt.getEntered();
			evt.setCancelled(preventPlayerFromBreakingOut(player));
		} catch (Exception e) {
			
		}
		
	}
	@EventHandler(priority = EventPriority.HIGHEST)
	public void emptyBucket(PlayerBucketEmptyEvent evt) {
			Player player = evt.getPlayer();
			evt.setCancelled(preventPlayerFromBreakingOut(player));
	}
	@EventHandler(priority = EventPriority.HIGHEST)
	public void projectileThrown(ProjectileLaunchEvent evt) {
		try {
			Player player = (Player) evt.getEntity().getShooter();
			evt.setCancelled(preventPlayerFromBreakingOut(player));
		} catch (Exception e) {
			
		}
		
	}
	@EventHandler
	public void openInv(PlayerInteractEvent evt) {
		Player p = (Player) evt.getPlayer();
		if(evt.getAction().equals(Action.RIGHT_CLICK_BLOCK) && evt.getClickedBlock().getType().equals(Material.CHEST)) {
			Block c = evt.getClickedBlock();
			double x = c.getX();
			double y = c.getY();
			double z = c.getZ();
			ResultSet rs = sqlite.readQuery("SELECT gm FROM chests WHERE locX = '" + x + "' AND locY = '" + y + "' AND locZ = '" + z + "' AND world = '" + c.getWorld().getName() + "'");
			GameMode g = null;
			try {
				g = GameMode.getByValue(rs.getInt("gm"));
			} catch (Exception e) {
				
			}
			if (!g.equals(p.getGameMode())) {
				evt.setCancelled(true);
				p.sendMessage(ChatColor.RED + "This chest has been placed in " + g.toString() + " but you are in " + p.getGameMode().toString());
			}
		} else if (evt.getAction().equals(Action.RIGHT_CLICK_BLOCK) && evt.getClickedBlock().getType().equals(Material.ENDER_CHEST) && evt.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
			p.sendMessage(ChatColor.RED + "You can't use enderchests in creative mode");
			evt.setCancelled(true);
		}
	}
	@EventHandler(priority = EventPriority.LOWEST)
	public void creeperBoom(EntityExplodeEvent evt) {
		if(evt.getEntityType().equals(EntityType.CREEPER)) {
			ResultSet rs = sqlite.readQuery("SELECT boom FROM worlds WHERE worldname = '" + evt.getLocation().getWorld().getName() + "'");
			boolean boom = true;
			try {
				boom = rs.getBoolean("boom");
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(!boom) evt.setCancelled(true);
		}
	}
    @EventHandler
    public void onSplash(PotionSplashEvent evt) {
    	ThrownPotion potion = evt.getPotion();
    	for(PotionEffect effect : potion.getEffects()) {
    		if(effect.getType() == PotionEffectType.INVISIBILITY) {
    			evt.setCancelled(true);
    			if (potion.getShooter() instanceof Player) {
    				((Player) potion.getShooter()).sendMessage(ChatColor.RED + "Invisibility potions are not enabled yet. We're working on this.");
    			}
    		}
    	}
   	}
    @EventHandler
    public void onUse(PlayerInteractEvent evt) {
    	Action a = evt.getAction();
    	Player p = evt.getPlayer();
    	ItemStack i = evt.getItem();
    	Block b = evt.getClickedBlock();
    	if(a == Action.RIGHT_CLICK_BLOCK && (b.getType() == Material.SIGN || b.getType() == Material.SIGN_POST || b.getType() == Material.WALL_SIGN)) {
    		if(!p.hasPermission("MitaBase.warpsigns.use")) {
					p.sendMessage(ChatColor.RED + "You don't have the permission to use warpsigns");
				} else {
					if(!((Sign)b.getState()).getLine(0).equalsIgnoreCase("[Warp]")) {
						return;
					}
					String wname = ((Sign)b.getState()).getLine(1);
					ResultSet rs = sqlite.readQuery("SELECT COUNT(*) AS numWarpsWithThatName FROM warps WHERE warpname = '" + wname + "'");
					int nwwn = 0;
					try {
						nwwn = rs.getInt("numWarpsWithThatName");
					} catch (Exception e) {
						e.printStackTrace();
					}
					if(nwwn > 0) {
						rs = sqlite.readQuery("SELECT locX, locY, locZ, world FROM warps WHERE warpname = '" + wname + "'");
						double x = 0;
						double y = 0;
						double z = 0;
						String world = "";
						try {
							x = rs.getDouble("locX");
							y = rs.getDouble("locY");
							z = rs.getDouble("locZ");
							world = rs.getString("world");
						} catch (Exception e) {
							e.printStackTrace();
						}
						p.teleport(new Location(Bukkit.getServer().getWorld(world), x, y, z));
					} else {
						p.sendMessage(ChatColor.RED + "Warp " + wname + " not found");
					}			
				}
    		evt.setCancelled(true);
    	} else if((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && i.getType() == Material.POTION) {
    		Potion potion = Potion.fromItemStack(i);
        	for(PotionEffect effect : potion.getEffects()) {
        		if(effect.getType() == PotionEffectType.INVISIBILITY) {
        			evt.setCancelled(true);
        				p.sendMessage(ChatColor.RED + "Invisibility potions are not enabled yet. We're working on this.");
        		}
        	}
        	
	    }
    }
    @EventHandler
    public void signThingy(SignChangeEvent evt) {
    	Player p = evt.getPlayer();
    	if(evt.getLines().length > 0 && evt.getLine(0).equalsIgnoreCase("[Warp]")) {
    		if (p.hasPermission("MitaBase.warpsigns.set") && (evt.getLines().length < 2 || evt.getLine(1).equalsIgnoreCase(""))){
    			p.sendMessage(ChatColor.RED + "First line: [Warp]");
    			p.sendMessage(ChatColor.RED + "Second line: warpname");
    			evt.getBlock().breakNaturally();
    			evt.setCancelled(true);
    		} else if (!p.hasPermission("MitaBase.warpsigns.set")){
    			p.sendMessage(ChatColor.RED + "You don't have the permission to create warpsigns");
    			evt.getBlock().breakNaturally();
    			evt.setCancelled(true);
    		}
    	}
    }
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(!cmdlogger) {
			String argString = " ";
			for(int i = 0; i < args.length; i++) {
				argString += args[i] + " ";
			}
			console.sendMessage(pluginPrefix + sender.getName() + " wrote command: " + cmd.getName() + argString);
		}		
		final Player p;
		if(sender instanceof Player) {
			p = (Player) sender;
		} else {
			p = null;
		}
		if(cmd.getName().equalsIgnoreCase("afk")) {
			if(p != null) {
				ResultSet rs = sqlite.readQuery("SELECT afk FROM users WHERE username = '" + p.getName() + "'");
				boolean afk = false;
				int afkInt = 0;
				try {
					afk = rs.getBoolean("afk");
				} catch (SQLException e) {
					e.printStackTrace();
				}
				if(!afk) afkInt = 1;
				sqlite.modifyQuery("UPDATE users SET afk="+afkInt+" WHERE username = '" + p.getName() + "'");
				if(!afk) Bukkit.getServer().broadcastMessage(ChatColor.DARK_AQUA +  p.getDisplayName() + " is now afk");
				if(afk) Bukkit.getServer().broadcastMessage(ChatColor.DARK_AQUA +  p.getDisplayName() + " is no longer afk");
			} else {
				playerOnly(sender);
			}
		}else if(cmd.getName().equalsIgnoreCase("back")) {
			if(p != null && p.hasPermission("MitaBase.back")) {
				ResultSet rs = sqlite.readQuery("SELECT lastLocX, lastLocY, lastLocZ, lastWorld FROM users WHERE username = '" + p.getName() +"'");
				try {
					Location l = new Location(Bukkit.getWorld(rs.getString("lastWorld")), rs.getInt("lastLocX"), rs.getInt("lastLocY"), rs.getInt("lastLocZ"));
					p.teleport(l);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (p == null) {
				playerOnly(sender);
			} else {
				noPermission(sender, cmd, args);
			}
		}else if(cmd.getName().equalsIgnoreCase("ban")) {
			String all = "";
			for(int i = 0; i < args.length; i++) {
				all += args[i] + "+";
			}
			console.sendMessage(all);
			if(p == null || p.hasPermission("MitaBase.ban")) {
				OfflinePlayer opfer = getServer().getOfflinePlayer(args[0]);
				if(opfer == null) {
					sender.sendMessage("Player " + args[0] + "not found");
				}
				String reason = "";
				long ticks = 0;
				if(args.length > 1) {
					try {
						ticks = Long.parseLong(args[1]) * 20;
						final String oname = opfer.getName();
						for(int i = 2; i < args.length; i++) {
							reason += args[i] + " ";
						}
						getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {

							   public void run() {
							       Bukkit.dispatchCommand(console, "ban " + oname);
							   }
						}, ticks);
					} catch (Exception e) {
						for(int i = 1; i < args.length; i++) {
							reason += args[i] + " ";
						}
					}
				}
				opfer.setBanned(!opfer.isBanned());
				if(opfer.isBanned()) { 
					sender.sendMessage(args[0] + ChatColor.RED + " has been banned");
					sqlite.modifyQuery("INSERT INTO bans (by, playerid, reason, date) VALUES ((SELECT userid FROM users WHERE username = '" + sender.getName() +"'), (SELECT userid FROM users WHERE username = '" + opfer.getName() +"'), '" + reason + "', '" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZ").format(new Date()) + "')");
					}
				if(!opfer.isBanned()) {
					sender.sendMessage(args[0] + ChatColor.GREEN + " has been unbanned");
					sqlite.modifyQuery("DELETE FROM bans WHERE playerid = (SELECT userid FROM users WHERE username = '" + opfer.getName() + "')");
				}
				if(opfer.isOnline()) { 
					Player o = (Player) opfer;
					o.kickPlayer(reason);
				}
			} else {
				noPermission(sender, cmd, args);
			}
		}else if(cmd.getName().equalsIgnoreCase("banreason")) {
			if(p == null || p.hasPermission("MitBase.banreson")) {
				if(args.length < 1) return false;
				ResultSet rs = sqlite.readQuery("SELECT reason, date, by FROM bans WHERE playerid = (SELECT userid FROM users WHERE username = '" + args[0] + "')");
				String msg = "";
				try {
					int by = rs.getInt("by");
					String reason = rs.getString("reason");
					String date = rs.getString("date");
					ResultSet rs2 = sqlite.readQuery("SELECT username FROM users WHERE userid = '" + by + "'");
					try {
						msg = "Player " + args[0] + " has been banned for " + reason + "by " + rs2.getString("username") + " on " + date;
					} catch (Exception e){
						msg = "Player " + args[0] + " has been banned for " + reason + "by Console on " + date;
					}
					
					sender.sendMessage(msg);
				} catch (Exception e) {
					sender.sendMessage(args[0] + ChatColor.RED + " is not banned");
				}
			} else {
				noPermission(sender, cmd, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("delhome")){
			if(p!= null && p.hasPermission("MitaBase.delhome")) {
				String h = "";
				if(args.length > 0) h = args[0];
				sqlite.modifyQuery("DELETE FROM homes WHERE homename = '" + h + "' AND userid = (SELECT userid FROM users WHERE username = '" + p.getName() + "')");
				p.sendMessage(ChatColor.GREEN + "Your home has been deleted, if you had it in the first place");
			} else if (p == null) {
				playerOnly(sender);
			} else {
				noPermission(sender, cmd, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("delwarp")){
			if(p == null || p.hasPermission("MitaBase.delwarp")) {
				String h = "";
				if(args.length > 0) h = args[0];
				sqlite.modifyQuery("DELETE FROM warps WHERE warpname = '" + h + "'");
				sender.sendMessage(ChatColor.GREEN + "Warp " + h + " has been deleted");
			} else {
				noPermission(sender, cmd, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("delwarp")){
			if(p == null || p.hasPermission("MitaBase.deljail")) {
				String h = "";
				if(args.length > 0) h = args[0];
				sqlite.modifyQuery("DELETE FROM jails WHERE jailname = '" + h + "'");
				sender.sendMessage(ChatColor.GREEN + "Jail " + h + " has been deleted");
			} else {
				noPermission(sender, cmd, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("enderchest")) {
			if (p != null && p.hasPermission("MitaBase.enderchest.see")) {
				if (args.length == 1) {
					Player p2 = Bukkit.getServer().getPlayer(args[0]);
					if (p2 != null) {
						p.openInventory(p2.getEnderChest());
					} else {
						p.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
					}
				} else {
					return false;
				}
			} else if (p == null) {
				playerOnly(sender);
			} else {
				noPermission(sender, cmd, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("feed")) {
			if(args.length >= 1) {
				if(p == null || p.hasPermission("MitaBase.feed")) {
					Player p2 = Bukkit.getPlayer(args[0]);
					if(p2 != null) {
						p2.setFoodLevel(20); //This is the maximum...
					} else {
						sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
					}
				} else {
					noPermission(sender, cmd, args);
				}
			} else {
				if(p != null && p.hasPermission("MitaBase.heal")) {
					p.setHealth(p.getMaxHealth());
					p.setFoodLevel(20); //This is the maximum...
				}
			}
		}else if(cmd.getName().equalsIgnoreCase("gamemode")) {
			if(args.length == 1) {
				if (p == null) {
					playerOnly(sender);
				} else if (!p.hasPermission("MitaBase.gm.self")) {
					noPermission(sender, cmd, args);
				} else {
					GameMode gm = null;
					try {
						gm = GameMode.getByValue(Integer.parseInt(args[0]));
					} catch (Exception e) {
						if (args[0].equalsIgnoreCase("survival")) gm = GameMode.SURVIVAL;
						if (args[0].equalsIgnoreCase("creative")) gm = GameMode.CREATIVE;
						if (args[0].equalsIgnoreCase("adventure")) gm = GameMode.ADVENTURE;
					}
					if(gm == null) {
						p.sendMessage(ChatColor.RED + "Invalid gamemode " + args[0]);
					} else {
						p.setGameMode(gm);
					}
				}
			} else if (args.length > 1) {
				Player p2 = Bukkit.getServer().getPlayer(args[0]);
				if (p != null && p.equals(p2)) {
					if (!p.hasPermission("MitaBase.gm.self")) {
						noPermission(sender, cmd, args);
					} else {
						GameMode gm = null;
						try {
							gm = GameMode.getByValue(Integer.parseInt(args[1]));
						} catch (Exception e) {
							if (args[1].equalsIgnoreCase("survival")) gm = GameMode.SURVIVAL;
							if (args[1].equalsIgnoreCase("creative")) gm = GameMode.CREATIVE;
							if (args[1].equalsIgnoreCase("adventure")) gm = GameMode.ADVENTURE;
						}
						if(gm == null) {
							p.sendMessage(ChatColor.RED + "Invalid gamemode " + args[0]);
						} else {
							p.setGameMode(gm);
						}
					}
				} else if (p != null && !p.hasPermission("MitaBase.gm.others")) {
					noPermission(sender, cmd, args);
				} else if (p2 == null) {
					sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
				} else {
					GameMode gm = null;
					try {
						gm = GameMode.getByValue(Integer.parseInt(args[1]));
					} catch (Exception e) {
						if (args[1].equalsIgnoreCase("survival")) gm = GameMode.SURVIVAL;
						if (args[1].equalsIgnoreCase("creative")) gm = GameMode.CREATIVE;
						if (args[1].equalsIgnoreCase("adventure")) gm = GameMode.ADVENTURE;
					}
					if(gm == null) {
						sender.sendMessage(ChatColor.RED + "Invalid gamemode " + args[1]);
					} else {
						p2.setGameMode(gm);
					}
				}
			} else if (args.length < 1) {
				return false;
			}	
		} else if(cmd.getName().equalsIgnoreCase("give")) {
			give(sender, args, cmd);
		} else if(cmd.getName().equalsIgnoreCase("groupmessage")) {
			if(p == null || p.hasPermission("MitaBase.groupmsg")) {
				if(args.length < 3) return false;
				String a = "";
				for(int i = 0; i < args.length; i++) {
					a += args[i] + " ";
				}
				a = a.substring(0, a.length()-1);
				if(!a.contains("message")) return false;
				String[] param = a.split("message", 2);
				String[] players = param[0].split(" ");
				String message = param[1];
				for(String playername : players) {
					Player plr = Bukkit.getPlayer(playername);
					if(plr == null)  {
						sender.sendMessage(ChatColor.RED + "Player " + playername + " not found. The message was sent to the others only");
					}
					plr.sendMessage(ChatColor.GRAY + "Groupmessage: [" + p.getDisplayName() + " -> me] " + message);
				}
			} else {
				noPermission(sender, cmd, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("heal")) {
			if(args.length >= 1) {
				if(p == null || p.hasPermission("MitaBase.heal")) {
					Player p2 = Bukkit.getPlayer(args[0]);
					if(p2 != null) {
						p2.setHealth(p2.getMaxHealth());
						p2.setFoodLevel(20); //This is the maximum...
					} else {
						sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
					}
				} else {
					noPermission(sender, cmd, args);
				}
			} else {
				if(p != null && p.hasPermission("MitaBase.heal")) {
					p.setHealth(p.getMaxHealth());
					p.setFoodLevel(20); //This is the maximum...
				}
			}
		} else if(cmd.getName().equalsIgnoreCase("home")) {
			if(p != null && p.hasPermission("MitaBase.home")) {
				String hname = "";
				if(args.length > 0) hname = args[0];
				ResultSet rs = sqlite.readQuery("SELECT COUNT(*) AS numHomesWithThatName FROM users, homes WHERE users.username = '" + p.getName() + "' AND homes.homename = '" + hname + "' AND users.userid = homes.userid");
				int count = 0;
				try {
					count = rs.getInt("numHomesWithThatName");
				} catch (Exception e) {
					
				}
				if(count == 0) {
					p.sendMessage(ChatColor.RED + "No home with that name found");
				} else {
					rs = sqlite.readQuery("SELECT locX, locY, locZ, world FROM users, homes WHERE users.username = '" + p.getName() + "' AND homes.homename = '" + hname + "' AND users.userid = homes.userid");
					try {
						p.teleport(new Location(Bukkit.getServer().getWorld(rs.getString("world")), rs.getDouble("locX"), rs.getDouble("locY"), rs.getDouble("locZ")));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} else if (p == null) {
				playerOnly(sender);
			} else {
				noPermission(sender, cmd, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("invsee")) {
			if (p != null && p.hasPermission("MitaBase.invsee.see")) {
				if (args.length == 1) {
					Player p2 = Bukkit.getServer().getPlayer(args[0]);
					if (p2 != null) {
						p.openInventory(p2.getInventory());
					} else {
						p.sendMessage(ChatColor.RED + "Player " + args[0] + " not found.");
						return true;
					}
				} else {
					return false;
				}
			} else if (p == null) {
				playerOnly(sender);
			} else {
				noPermission(sender, cmd, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("jail")) {
			if(p == null || p.hasPermission("MitaBase.jail")) {
				if(args.length < 2) return false;
				Player p2 = getServer().getPlayer(args[0]);
				if(p2 == null) {
					sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
					return true;
				}
				String jname = args[1];
				ResultSet rs = sqlite.readQuery("SELECT locX, locZ, locY, world, COUNT(*) AS numJailsWithThatName FROM jails WHERE jailname = '" + jname + "' GROUP BY world");
				int x = 0; int y = 0; int z = 0; String world = "";
				int njwn = 0;
				try {
					njwn = rs.getInt("numJailsWithThatName");
					x = rs.getInt("locX");
					y = rs.getInt("locY");
					z = rs.getInt("locZ");
					world = rs.getString("world");
				} catch (Exception e) {
				}
				if(njwn < 1) {
					sender.sendMessage(ChatColor.RED + "Jail " + args[1] + " not found");
					return true;
				}
				int seconds = 0;
				if (args.length >= 3) {
					try {
						seconds = Integer.parseInt(args[2]);
					} catch (Exception e) {
						sender.sendMessage(ChatColor.RED + args[2] + " is not a valid number");
						return false;
					}
				}
				DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZ");
				Calendar c = Calendar.getInstance();
				c.add(Calendar.SECOND, seconds);
				Date jailUntil = c.getTime();
				String until = df.format(jailUntil);
				if(seconds == 0) {
					until = "Forever";
				} else {
					final String pname = args[0];
					long ticks = seconds*20;
					getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {

						   public void run() {
						       Bukkit.dispatchCommand(console, "unjail " + pname);
						   }
					}, ticks);
				}
				sqlite.modifyQuery("UPDATE users SET jailed='1', jaileduntil='" + until + "' WHERE username='" + args[0] + "'");
				p2.teleport(new Location(getServer().getWorld(world), x, y, z));
				p2.sendMessage(ChatColor.RED + "You've been jailed");
			} else {
				noPermission(sender, cmd, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("kick")) {
			if(p == null || p.hasPermission("MitaBase.kick")) {
				if(args.length < 1) {
					return false;
				} else {
					Player victim = Bukkit.getServer().getPlayer(args[0]);
					if(victim == null) {
						p.sendMessage("Player " + args[0] + " couldn't be found!");
					} else {
						String reason = "You probably deserved it";
						if(args.length > 1) {
							reason = "";
							for(int i = 1; i < args.length; i++) {
								reason += args[i];
							}
						}
						victim.kickPlayer(reason);
					}
				}
			} else {
				noPermission(sender, cmd, args);
			}
			
		} else if(cmd.getName().equalsIgnoreCase("listgamemode")) {
			if(p == null || p.hasPermission("MitaBase.gm.list")) {
				String names = "";
			    if (args.length < 0) {
			    	return false;
			    }
			    GameMode gm = null;
			    if (args[0].equalsIgnoreCase("SURVIVAL")) gm = GameMode.SURVIVAL;
			    if (args[0].equalsIgnoreCase("CREATIVE")) gm = GameMode.CREATIVE;
			    if (args[0].equalsIgnoreCase("ADVENTURE")) gm = GameMode.ADVENTURE;
			    if (args[0].equalsIgnoreCase("0")) gm = GameMode.SURVIVAL;
			    if (args[0].equalsIgnoreCase("1")) gm = GameMode.CREATIVE;
			    if (args[0].equalsIgnoreCase("2")) gm = GameMode.ADVENTURE;
				if (gm == null) {
					sender.sendMessage(ChatColor.RED + args[0] + " is not a valid gamemode");
					return true;
				}
			    for(Player player: getServer().getOnlinePlayers()) {
			        if(player.getGameMode().equals(gm)) {
			            names += ChatColor.BLUE + player.getDisplayName() + ChatColor.RESET + ", ";
			        }     
			    }
			    if (names.length() > 2) names = names.substring(0, names.length()-2);
			    sender.sendMessage(names);
			} else {
				noPermission(sender, cmd, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("mail")) {
			if(p != null && p.hasPermission("MitaBase.mail")) {
				if(args.length == 0) {
					String msg_sender = "";
					String message = "";
					boolean new_msg = false;
					ResultSet rs = sqlite.readQuery("SELECT username, message FROM users AS u, mail AS m WHERE u.userid = m.senderid AND m.receiverid = (SELECT userid FROM users WHERE username = '" + p.getName() + "')");
					sender.sendMessage(ChatColor.BLUE + "You can write new mails by typing /mail <player> <message> or mark all your mails as read by doing /mail clear");
					try {
						while (rs.next()) {
							msg_sender = rs.getString("username");
							message = rs.getString("message");
							new_msg = true;
							sender.sendMessage(ChatColor.YELLOW+ msg_sender + ": " + ChatColor.RESET + message);
						}
						
					} catch (Exception e) {
						
					}
					if(!new_msg) {
						sender.sendMessage(ChatColor.BLUE + "You don't have any new mail");
					}
				} else if (args.length == 1) {
					if(args[0].equalsIgnoreCase("clear")) {
						sqlite.modifyQuery("DELETE FROM messages WHERE receiverid = (SELECT userid FROM users WHERE username = '" + sender.getName() + "')");
						sender.sendMessage(ChatColor.GREEN + "Mail cleared");
					} else {
						return false;
					}
				} else {
					String message = "";
					for(int i = 1; i < args.length; i++) {message += args[i] + "";}
					int pid = -1;
					ResultSet rs = sqlite.readQuery("SELECT userid FROM users WHERE username = '" + args[0] + "'");
					try {
						pid = rs.getInt("userid");
						sqlite.modifyQuery("INSERT INTO mail (senderid, receiverid, message) VALUES ((SELECT userid FROM users WHERE username = '" + sender.getName() + "'),  " + pid + ", '" + message + "')");
					} catch (Exception e) {
						sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
						return false;
					}
				}
			} else if (p == null) {
				playerOnly(sender);
			} else {
				noPermission(sender, cmd, args);
			}
			
		} else if(cmd.getName().equalsIgnoreCase("message")) {
			if(p == null || p.hasPermission("MitaBase.msg")) {
				if(args.length < 2) return false;
				String msg = "";
				for(int i = 1; i < args.length; i++) {
					msg += args[i] + " ";
				}
				Player partner = getServer().getPlayer(args[0]);
				if(partner == null && !args[0].equalsIgnoreCase("console")) {
					sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
					return true;
				}
				if(p == partner) { p.sendMessage("Haha, very funny..."); return true; }
				if(p == null) console_last_messaged = partner.getName();
				if (partner == null) {
					console.sendMessage(ChatColor.GRAY + "[" + sender.getName() + " -> Console] " + ChatColor.RESET + msg);
					sender.sendMessage(ChatColor.GRAY + "[" + sender.getName() + " -> Console] " + ChatColor.RESET + msg);
					sqlite.modifyQuery("UPDATE users SET last_messaged = '0' WHERE username = '" + sender.getName() + "'");
				} else {
					partner.sendMessage(ChatColor.GRAY + "[" +  sender.getName() + " -> " + partner.getName() + "] " + ChatColor.RESET + msg);
					sender.sendMessage(ChatColor.GRAY + "[" +  sender.getName() + " -> " + partner.getName() + "] " + ChatColor.RESET + msg);
					sqlite.modifyQuery("UPDATE users SET last_messaged = (SELECT userid FROM users WHERE username = '" + partner.getName() + "') WHERE username = '" + sender.getName() + "'");
				}
			} else {
				noPermission(sender, cmd, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("mitabase")) {
			if((p == null || p.hasPermission("MitaBase.reload")) && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
				onDisable();
				onEnable();
				sender.sendMessage(ChatColor.GREEN + "Reloaded MitaBase");
			}
		} else if(cmd.getName().equalsIgnoreCase("mobdamage")) {
			if (args.length == 0) {
				if (p != null && p.hasPermission("MitaBase.toggleMobDamage")) {
					World w = p.getWorld();
					ResultSet rs = sqlite.readQuery("SELECT mobdmg FROM worlds WHERE worldname = '" + w.getName() + "'");
					boolean dmg = true;
					try {
						dmg = rs.getBoolean("mobdmg");
					} catch (Exception e) {
						e.printStackTrace();
					}
					dmg = !dmg;
					if (dmg) {
						sqlite.modifyQuery("UPDATE worlds SET mobdmg='1'");
						sender.sendMessage(ChatColor.GREEN + "Mobdamage in world " + w.getName() + " is now " + ChatColor.RED + "on");
					} else {
						sqlite.modifyQuery("UPDATE worlds SET mobdmg='0'");
						sender.sendMessage(ChatColor.GREEN + "Mobdamage in world " + w.getName() + " is now off");
					}
					
				} else if (p == null) {
					playerOnly(sender);
				} else {
					noPermission(sender, cmd, args);
				}
			} else {
				if (p == null || p.hasPermission("MitaBase.toggleMobDamage")) {
					World w = getServer().getWorld(args[0]);
					if (w == null) {
						sender.sendMessage(ChatColor.RED + "World " + args[0] + " not found");
						return true;
					}
					ResultSet rs = sqlite.readQuery("SELECT mobdmg FROM worlds WHERE worldname = '" + w.getName() + "'");
					boolean dmg = true;
					try {
						dmg = rs.getBoolean("mobdmg");
					} catch (Exception e) {
						e.printStackTrace();
					}
					dmg = !dmg;
					if (dmg) {
						sqlite.modifyQuery("UPDATE worlds SET mobdmg='1'");
						sender.sendMessage(ChatColor.GREEN + "Mobdamage in world " + w.getName() + " is now " + ChatColor.RED + "on");
					} else {
						sqlite.modifyQuery("UPDATE worlds SET mobdmg='0'");
						sender.sendMessage(ChatColor.GREEN + "Mobdamage in world " + w.getName() + " is now off");
					}
					
				} else {
					noPermission(sender, cmd, args);
				}
			}
		} else if(cmd.getName().equalsIgnoreCase("mute")) {
			if(p == null || p.hasPermission("MitaBase.mute")) {
				if(args.length == 1) {
					Player p2 = Bukkit.getServer().getPlayer(args[0]);
					if (p2 == null) {
						sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
					} else {
						sqlite.modifyQuery("UPDATE users SET muted=1 WHERE username = '" + p2.getName() + "'");
					}		
				} else {
					return false;
				}
			} else {
				noPermission(sender, cmd, args);
			}	
		}  else if(cmd.getName().equalsIgnoreCase("reply")) {
			if(p == null || p.hasPermission("MitaBase.msg")) {
				if(args.length < 1) return false;
				String msg = "";
				for(int i = 0; i < args.length; i++) {
					msg += args[i] + " ";
				}
				
				String pa = "";
				int id = -1;
				if(p != null) {
					ResultSet rs = sqlite.readQuery("SELECT last_messaged FROM users WHERE username = '" + p.getName() + "'");
					try {
						id = rs.getInt("last_messaged");
						rs.close();
						rs = sqlite.readQuery("SELECT username FROM users WHERE userid = '" + id + "'");
						pa = rs.getString("username");
					} catch (Exception e) {
					}
				} else {
					pa = console_last_messaged;
				}
				if(id == 0) {
					pa = "Console";
				}
				Player partner = getServer().getPlayer(pa);
				if(partner == null && !pa.equalsIgnoreCase("console")) {
					sender.sendMessage(ChatColor.RED + "Player " + pa + " not found");
					return true;
				}
				if(p == partner) { p.sendMessage("Haha, very funny..."); return true; }
				if(p == null) console_last_messaged = partner.getName();
				if (partner == null) {
					console.sendMessage(ChatColor.GRAY + "[" + sender.getName() + " -> Console] " + ChatColor.RESET + msg);
					sender.sendMessage(ChatColor.GRAY + "[" + sender.getName() + " -> Console] " + ChatColor.RESET + msg);
					sqlite.modifyQuery("UPDATE users SET last_messaged = '0' WHERE username = '" + sender.getName() + "'");
				} else {
					partner.sendMessage(ChatColor.GRAY + "[" +  sender.getName() + " -> " + partner.getDisplayName() + "] " + ChatColor.RESET + msg);
					sender.sendMessage(ChatColor.GRAY + "[" +  sender.getName() + " -> " + partner.getDisplayName() + "] " + ChatColor.RESET + msg);
					sqlite.modifyQuery("UPDATE users SET last_messaged = (SELECT userid FROM users WHERE username = '" + partner.getName() + "') WHERE username = '" + sender.getName() + "'");
				}
			} else {
				noPermission(sender, cmd, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("nick")){
			if(args.length == 0) {
				if(p != null && p.hasPermission("MitaBase.nick.self")) {
					p.setDisplayName(p.getName());
					sqlite.modifyQuery("UPDATE users SET nick = '' WHERE username = '" + p.getName()+ "'");
					p.sendMessage(ChatColor.BLUE + "Your nick is now cleared");
				} else if(p == null) {
					playerOnly(sender);
				} else {
					noPermission(sender, cmd, args);
				}	
			} else if(args.length == 1) {
				Player pl = getServer().getPlayer(args[0]);
				if(pl != null && (p == null || p.hasPermission("MitaBase.nick.others"))) {
					pl.setDisplayName(pl.getName());
					sqlite.modifyQuery("UPDATE users SET nick = '' WHERE username = '" + pl.getName()+ "'");
					pl.sendMessage(ChatColor.BLUE + "Your nick is now cleared");
					sender.sendMessage(ChatColor.BLUE + "Successfully cleared nick of " + pl.getName());
				} else if(pl == null && p != null && p.hasPermission("MitaBase.nick.self")) {
					p.setDisplayName(colorize(args[0]));
					sqlite.modifyQuery("UPDATE users SET nick = '" + colorize(args[0]) + "' WHERE username = '" + p.getName()+ "'");
					p.sendMessage(ChatColor.BLUE + "Your nick is now " + colorize(args[0]));
				} else if(pl == null) {
					sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
				} else if(pl != null && p != null && !p.hasPermission("MitaBase.nick.others")) {
					noPermission(sender, cmd, args);
				} else {
					noPermission(sender, cmd, args);
				}
			} else if(args.length == 2) {
				if(p == null || p.hasPermission("MitaBase.nick.others")) {
					Player pl = getServer().getPlayer(args[0]);
					if(pl != null) {
						pl.setDisplayName(colorize(args[1]));
						sqlite.modifyQuery("UPDATE users SET nick = '" + colorize(args[1]) + "' WHERE username = '" + pl.getName()+ "'");
						pl.sendMessage(ChatColor.BLUE + "Your nick is now " + colorize(args[1]));
						sender.sendMessage(ChatColor.BLUE + "Successfully changed nick of " + pl.getName() + " to " +  colorize(args[1]));
					}else {
						sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
					}
				} else {
					noPermission(sender, cmd, args);
				}
			} else {
				return false;
			}
		} else if (cmd.getName().equalsIgnoreCase("pvp")){
			if(p != null && p.hasPermission("MitaBase.pvp")) {
				if(args.length < 1) return false;
				int pvp = 2;
				try {
					pvp = Integer.parseInt(args[0]);
				} catch (Exception e) {
					if (args[0].equalsIgnoreCase("on")) {
						pvp = 1;
						
					} else if (args[0].equalsIgnoreCase("off")) {
						pvp = 0;
					
					}
				}
				if (pvp == 1) {
					p.sendMessage(ChatColor.RED + "PVP is now on");
				} else if (pvp == 0) {
					p.sendMessage(ChatColor.GREEN + "PVP is now off");
				} else {
					return false;
				}
				sqlite.modifyQuery("UPDATE users SET pvp='" + pvp + "'");
			} else if (p == null) {
				playerOnly(sender);
			} else {
				noPermission(sender, cmd, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("seen")){
			if(p == null || p.hasPermission("MitaBase.seen")) {
				if(args.length != 1) {
					return false;
				}
				Player pl = getServer().getPlayer(args[0]);
				String plname = "";
				if(pl != null) {
					plname = pl.getName();
				} else {
					plname = args[0];
				}
				ResultSet rs = sqlite.readQuery("SELECT last_seen FROM users WHERE username = '" + plname + "'");
				try {
					String ls = rs.getString("last_seen");
					if(pl != null) {
						sender.sendMessage(pl.getDisplayName() + " is online since " + ls);
					} else if(pl == null) {
						sender.sendMessage(plname + " is offline and was last seen on " + ls);
					}
				} catch (Exception e) {
					sender.sendMessage(ChatColor.RED + "Player" + args[0] + "not found");
				}
			} else {
				noPermission(sender, cmd, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("sethome")){
			setHome(sender, args, cmd);
		} else if(cmd.getName().equalsIgnoreCase("setjail")) {
			if (p == null) {
				playerOnly(sender);
			} else if (!p.hasPermission("MitaBase.createjail")) {
				noPermission(sender, cmd, args);
			} else if (args.length < 1) {
				return false;
			} else {
				String jname = args[0];
				ResultSet rs = sqlite.readQuery("SELECT COUNT(*) AS numJailsWithThatName FROM jails WHERE jailname = '" + jname + "'");
				int njwn = 0;
				try {
					njwn = rs.getInt("numJailsWithThatName");
				} catch (Exception e) {
					e.printStackTrace();
				}
				if(njwn > 0) {
					sqlite.modifyQuery("UPDATE jails SET locX='"+p.getLocation().getX()+"', locY='"+p.getLocation().getY()+"', locZ='"+p.getLocation().getZ()+"' WHERE jailname='"+jname+"'");
					p.sendMessage(ChatColor.GREEN + "Jail " + jname + " has been succesfully updated");
				} else {
					sqlite.modifyQuery("INSERT INTO jails (jailname, locX, locY, locZ, world) VALUES ('" + jname + "', '" + p.getLocation().getX() + "', '" + p.getLocation().getY() + "', '" + p.getLocation().getZ() + "', '" + p.getWorld().getName() + "')");
					p.sendMessage(ChatColor.GREEN + "Jail " + jname + " has been succesfully created");
				}
			}
			
		} else if(cmd.getName().equalsIgnoreCase("setspawn")) {
			if(p == null) {
				playerOnly(sender);
			} else if (!p.hasPermission("MitaBase.setspawn")){
				noPermission(sender, cmd, args);
			} else {
				getConfig().set("spawn.x", p.getLocation().getX());
				getConfig().set("spawn.y", p.getLocation().getY());
				getConfig().set("spawn.z", p.getLocation().getZ());
				getConfig().set("spawn.world",p.getLocation().getWorld().getName());
				saveConfig();
				p.sendMessage(ChatColor.GREEN + "Spawn of server successfully set in world " + p.getLocation().getWorld().getName());
			}
		} else if(cmd.getName().equalsIgnoreCase("setwarp")) {
			if (p == null) {
				playerOnly(sender);
			} else if (!p.hasPermission("MitaBase.createwarp")) {
				noPermission(sender, cmd, args);
			} else if (args.length < 1) {
				return false;
			} else {
				String wname = args[0];
				ResultSet rs = sqlite.readQuery("SELECT COUNT(*) AS numWarpsWithThatName FROM warps WHERE warpname = '" + wname + "'");
				int nwwn = 0;
				try {
					nwwn = rs.getInt("numWarpsWithThatName");
				} catch (Exception e) {
					e.printStackTrace();
				}
				if(nwwn > 0) {
					sqlite.modifyQuery("UPDATE warps SET locX='"+p.getLocation().getX()+"', locY='"+p.getLocation().getY()+"', locZ='"+p.getLocation().getZ()+"' WHERE warpname='"+args[0]+"'");
					p.sendMessage(ChatColor.GREEN + "Warp " + args[0] + " has been succesfully updated");
				} else {
					sqlite.modifyQuery("INSERT INTO warps (warpname, locX, locY, locZ, world) VALUES ('" + args[0] + "', '" + p.getLocation().getX() + "', '" + p.getLocation().getY() + "', '" + p.getLocation().getZ() + "', '" + p.getWorld().getName() + "')");
					p.sendMessage(ChatColor.GREEN + "Warp " + args[0] + " has been succesfully created");
				}
			}
			
		} else if(cmd.getName().equalsIgnoreCase("setwspawn")) {
			if(p == null) {
				playerOnly(sender);
			} else if (!p.hasPermission("MitaBase.setwspawn")){
				noPermission(sender, cmd, args);
			} else {
				p.getWorld().setSpawnLocation(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
				p.sendMessage(ChatColor.GREEN + "Spawn of world " + p.getLocation().getWorld().getName() + " successfully set");
			}
		} else if(cmd.getName().equalsIgnoreCase("spawn")) {
			if(p == null) {
				playerOnly(sender);
			} else if (!p.hasPermission("MitaBase.spawn")){
				noPermission(sender, cmd, args);
			}else {
				p.teleport(new Location(Bukkit.getServer().getWorld(getConfig().getString("spawn.world")), getConfig().getDouble("spawn.x"), getConfig().getDouble("spawn.y"), getConfig().getDouble("spawn.z")));
			}
		} else if(cmd.getName().equalsIgnoreCase("socialspy")) {
			if(p != null && p.hasPermission("MitaBase.socialspy")) {
				if(args.length < 1) return false;
				int sosp = 2;
				try {
					sosp = Integer.parseInt(args[0]);
				} catch (Exception e) {
					if (args[0].equalsIgnoreCase("on")) {
						sosp = 1;
						
					} else if (args[0].equalsIgnoreCase("off")) {
						sosp = 0;
					
					}
				}
				if (sosp == 1) {
					p.sendMessage(ChatColor.BLUE + "Socialspy is now on");
				} else if (sosp == 0) {
					p.sendMessage(ChatColor.BLUE + "Socialspy is now off");
				} else {
					return false;
				}
				sqlite.modifyQuery("UPDATE users SET socialspy = '" + sosp + "'");
			} else if (p == null) {
				playerOnly(sender);
			} else {
				noPermission(sender, cmd, args);
			}
			
		} else if(cmd.getName().equalsIgnoreCase("sudo")) {
			if(p == null || p.hasPermission("MitaBase.sudo")) {
				if(args.length < 2) {
					return false;
				}
				Player pl = getServer().getPlayer(args[0]);
				if(pl == null) {
					sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
					return true;
				}
				String command = "";
				for(int i = 0; i < args.length; i++) {
					command += args[i] + " ";
				}
				command = command.substring(0, command.length()-1);
				getServer().dispatchCommand(pl, command);
			} else {
				noPermission(sender, cmd, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("time")) {
			/*
			 * 0 Arguments: Print time of current world
			 * 1 Argument: Print time of given world; Console: YES
			 * 2 Arguments: Set time of current world
			 * 3 Arguments: Set time of given world; Console: YES
			 */
			if(args.length == 0) {
				if(p != null && p.hasPermission("MitaBase.time")) {
					p.sendMessage(ChatColor.BLUE + "Time in world " + p.getWorld().getName() + " is " + p.getWorld().getTime() + " ticks");
					//MobEffect m = new MobEffect(14, 200);
					//((CraftPlayer)p).getHandle().netServerHandler.sendPacket(new Packet41MobEffect(getServer().getPlayer("mx44").getEntityId(), m));
				} else if (p == null) {
					playerOnly(sender);
					return true;
				} else {
					noPermission(sender, cmd, args);
				}
			} else if(args.length == 1) {
				if(p == null || p.hasPermission("MitaBase.time")) {
					World w = Bukkit.getServer().getWorld(args[0]);
					if(w != null) {
						sender.sendMessage(ChatColor.BLUE + "Time in world " + w.getName() + " is " + w.getTime() + " ticks");
					} else {
						sender.sendMessage(ChatColor.RED + "World " + args[0] + " not found");
					}
				} else {
					noPermission(sender, cmd, args);
				}
			} else if(args.length == 2) {
				if(p != null && p.hasPermission("MitaBase.setTime")) {
					if(args[0].equalsIgnoreCase("add")) {
						try {
							p.getWorld().setTime(p.getWorld().getTime() + Integer.parseInt(args[1]));
							p.sendMessage(ChatColor.BLUE + "Time in world " + p.getWorld().getName() + " is now " + p.getWorld().getTime() + " ticks");
						} catch (Exception e) {
							return false;
						}
					} else if (args[0].equalsIgnoreCase("set")) {
						try {
							p.getWorld().setTime(Integer.parseInt(args[1]));
							p.sendMessage(ChatColor.BLUE + "Time in world " + p.getWorld().getName() + " is now " + p.getWorld().getTime() + " ticks");
						} catch (Exception e) {
							return false;
						}
					} else {
						return false;
					}
				} else if (p == null) {
					playerOnly(sender);
				} else {
					noPermission(sender, cmd, args);
				}
				
			} else if(args.length == 3) {
				if(p == null || p.hasPermission("MitaBase.time")) {
					World w = Bukkit.getServer().getWorld(args[2]);
					if(w != null) {
						if(args[0].equalsIgnoreCase("add")) {
							try {
								w.setTime(p.getWorld().getTime() + Integer.parseInt(args[1]));
								sender.sendMessage(ChatColor.BLUE + "Time in world " + w.getName() + " is now " + w.getTime() + " ticks");
							} catch (Exception e) {
								return false;
							}
						} else if (args[0].equalsIgnoreCase("set")) {
							try {
								w.setTime(Integer.parseInt(args[1]));
								sender.sendMessage(ChatColor.BLUE + "Time in world " + w.getName() + " is now " + w.getTime() + " ticks");
							} catch (Exception e) {
								return false;
							}
						} else {
							return false;
						}
					} else {
						sender.sendMessage(ChatColor.RED + "World " + args[2] + " not found");
					}
				} else {
					noPermission(sender, cmd, args);
				}
			} else {
				return false;
			}
		} else if (cmd.getName().equalsIgnoreCase("toggleboom")){
			if (args.length == 1) {
				if(p != null && p.hasPermission("MitaBase.toggleBoom")) {
					if(!(args[0].equals("0") || args[0].equals("1"))) {
						return false;
					}
					sqlite.modifyQuery("UPDATE worlds SET boom='" + args[0] + "' WHERE worldname = '" + p.getWorld().getName() + "'");
				} else if (p == null) {
					playerOnly(sender);
				} else {
					noPermission(sender, cmd, args);
				}
			} else if (args.length == 2){
				if (p == null || p.hasPermission("MitaBase.toggleBoom")) {
					if(!(args[0].equals("0") || args[0].equals("1"))) {
						return false;
					}
					World w = getServer().getWorld(args[1]);
					if (w == null) {
						sender.sendMessage(ChatColor.RED + "World " + args[1] + " not found");
						return true;
					}
					sqlite.modifyQuery("UPDATE worlds SET boom='" + args[0] + "' WHERE worldname = '" + w.getName() + "'");
				} else {
					noPermission(sender, cmd, args);
				}
			} else {
				return false;
			}
		
		} else if (cmd.getName().equalsIgnoreCase("tp")){
			if(args.length < 1) {
				sender.sendMessage(ChatColor.RED + "Please specify one player");
				return false;
			} else if (args.length == 1) {
				Player p2 = Bukkit.getServer().getPlayer(args[0]);
				if(p == null) {
					playerOnly(sender);
				} else if(!p.hasPermission("MitaBase.tp.self")){
					noPermission(sender, cmd, args);
				} else if (p2 == null){
					p.sendMessage(ChatColor.RED + "Player " + args[0] + " is not online");
				} else {
					p.teleport(p2);
				}
			} else if (args.length == 2) {
				if(p == null || p.hasPermission("MitaBase.tp.others")) {
					if(Bukkit.getServer().getPlayer(args[0]) == null) {
						sender.sendMessage(ChatColor.RED + "Player " + args[0] + " is not online");
					} else if(Bukkit.getServer().getPlayer(args[1]) == null) {
						sender.sendMessage(ChatColor.RED + "Player " + args[1] + " is not online");
					} else {
						Bukkit.getServer().getPlayer(args[0]).teleport(Bukkit.getServer().getPlayer(args[1]));
					}
				} else {
					noPermission(sender, cmd, args);
				}
			} else {
				sender.sendMessage(ChatColor.RED + "Too many arguments");
				return false;
			}
		} else if (cmd.getName().equalsIgnoreCase("tpa")){
			if(p != null && p.hasPermission("MitaBase.tpa.tpa")) {
				if(args.length != 1) return false;
				final Player pl = getServer().getPlayer(args[0]);
				if(pl == null) {
					sender.sendMessage(ChatColor.RED + "Player" + args[0] + " not found");
					return true;
				}
				sqlite.modifyQuery("INSERT INTO tpa (senderid, receiverid) VALUES ((SELECT userid FROM users WHERE username = '" + p.getName() + "'), (SELECT userid FROM users WHERE username = '" + args[0] + "'))");
				pl.sendMessage(ChatColor.BLUE + p.getDisplayName() + " has sent you a request to teleport you to them. Type /tpaccept to accept and /tpdeny to deny");
				p.sendMessage(ChatColor.BLACK + "Request sent to " + pl.getDisplayName());
				Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
					@Override
					public void run() {
						sqlite.modifyQuery("DELETE FROM tpa WHERE senderid = (SELECT userid FROM users WHERE username = '" + p.getName() + "') AND receiverid = SELECT userid FROM users WHERE username = '" + pl.getName() + "')");
					}
				}, 6000L);
			} else if(p == null) {
				playerOnly(sender);
			} else {
				noPermission(sender, cmd, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("tpaccept")){
			if(p != null && p.hasPermission("MitaBase.tpa.tpaccept")) {
				if(args.length == 0) {
					ResultSet rs = sqlite.readQuery("SELECT tpaid, username FROM tpa, users WHERE receiverid = (SELECT userid FROM users WHERE username = '" + p.getName() + "') AND senderid = userid");
					int h = 0;
					try {
						while(rs.next()) { h++; }
					} catch (Exception e) {
					}
					rs = sqlite.readQuery("SELECT tpaid, username FROM tpa, users WHERE receiverid = (SELECT userid FROM users WHERE username = '" + p.getName() + "') AND senderid = userid");
					try {
						if(h == 0) {
							p.sendMessage(ChatColor.BLUE + "You don't have any open tpa-requests");
						} else if (h == 1) {
							Player pl = getServer().getPlayer(rs.getString("username"));
							if(pl != null) {
								p.teleport(pl);
								sqlite.modifyQuery("DELETE FROM tpa WHERE tpaid = '" + rs.getInt("tpaid") + "'");
							} else {
								p.sendMessage(ChatColor.RED + "Player " + rs.getString("username") + " not found");
							}
						} else {
							p.sendMessage(ChatColor.BLUE + "You've got multiple tpa-requests. Please type /tpaccept <id>");
							while(rs.next()) {
								Player pl = getServer().getPlayer(rs.getString("username"));
								if(pl != null) {
									p.sendMessage(ChatColor.BLUE + "ID: " + rs.getInt("tpaid") + " from " + pl.getDisplayName());
								}
							}
						}
					} catch (Exception e) {
						p.sendMessage(ChatColor.BLUE + "You don't have any open tpa-requests");
						e.printStackTrace();
					}
				} else if(args.length == 1) {
					int tpaid = -1;
					try {
						tpaid = Integer.parseInt(args[0]);
					} catch (Exception e) {
						p.sendMessage(ChatColor.RED + "Invail ID: " + args[0]);
						return true;
					}
					ResultSet rs = sqlite.readQuery("SELECT username FROM users, tpa WHERE tpaid = '" + tpaid + "' AND receiverid = (SELECT userid FROM users WHERE username = '" + p.getName() + "') AND senderid = userid");
					try {
						Player pl = getServer().getPlayer(rs.getString("username"));
						if(pl != null) {
							p.teleport(pl);
							sqlite.modifyQuery("DELETE FROM tpa WHERE tpaid = '" + tpaid + "'");
						} else {
							p.sendMessage(ChatColor.RED + "Player " + rs.getString("username") + " not found");
							return true;
						}
					} catch (Exception e) {
						p.sendMessage(ChatColor.RED + "This request cannot be found");
						return true;
					}
				} else {
					return false;
				}
			} else if(p == null) {
				playerOnly(sender);
			} else {
				noPermission(sender, cmd, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("tpadeny")){
			if(p != null && p.hasPermission("MitaBase.tpa.tpdeny")) {
				if(args.length == 0) {
					ResultSet rs = sqlite.readQuery("SELECT receiverid, senderid, tpaid, username FROM tpa, users WHERE receiverid = (SELECT userid FROM users WHERE username = '" + p.getName() + "') AND senderid = userid");
					int h = 0;
					try {
						while(rs.next()) { h++; }
					} catch (Exception e) {
					}
					rs = sqlite.readQuery("SELECT receiverid, senderid, tpaid, username FROM tpa, users WHERE receiverid = (SELECT userid FROM users WHERE username = '" + p.getName() + "') AND senderid = userid");
					try {
						if(h == 0) {
							p.sendMessage(ChatColor.BLUE + "You don't have any open tpa-requests");
						} else if (h == 1) {
							try {
								getServer().getPlayer(rs.getString("username")).sendMessage(ChatColor.BLUE + p.getDisplayName() + " has denied your request");
							} catch (Exception e)  {
							}
							sqlite.modifyQuery("DELETE FROM tpa WHERE senderid = '" + rs.getInt("senderid") + "' AND receiverid = '" + rs.getInt("receiverid") + "'");
						} else {
							p.sendMessage(ChatColor.BLUE + "You've got multiple tpa-requests. Please type /tpadeny <id>");
							while(rs.next()) {
								Player pl = getServer().getPlayer(rs.getString("username"));
								if(pl != null) {
									p.sendMessage(ChatColor.BLUE + "ID: " + rs.getInt("tpaid") + " from " + pl.getDisplayName());
								} else {
									p.sendMessage(ChatColor.BLUE + "ID: " + rs.getInt("tpaid") + " from " + rs.getString("username"));
								}
							}
						}
					} catch (Exception e) {
						p.sendMessage(ChatColor.BLUE + "You don't have any open tpa-requests");
					}
				} else if(args.length == 1) {
					int tpaid = -1;
					try {
						tpaid = Integer.parseInt(args[0]);
					} catch (Exception e) {
						p.sendMessage(ChatColor.RED + "Invail ID: " + args[0]);
						return true;
					}
					ResultSet rs = sqlite.readQuery("SELECT username FROM users, tpa WHERE tpaid = '" + tpaid + "' AND receiverid = (SELECT userid FROM users WHERE username = '" + p.getName() + "') AND senderid = userid");
					try {
						Player pl = getServer().getPlayer(rs.getString("username"));
						if(pl != null) {
							pl.sendMessage(ChatColor.BLUE + p.getDisplayName() + " has denied your request");
							sqlite.modifyQuery("DELETE FROM tpa WHERE tpaid = '" + tpaid + "'");
						} else {
							p.sendMessage(ChatColor.RED + "Player " + rs.getString("username") + " not found");
							return true;
						}
					} catch (Exception e) {
						p.sendMessage(ChatColor.RED + "This request cannot be found");
						return true;
					}
				} else {
					
				}
			} else if(p == null) {
				playerOnly(sender);
			} else {
				noPermission(sender, cmd, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("warp")){
			if (args.length == 0){
				if(p == null || p.hasPermission("MitaBase.listwarp")) {
					ResultSet rs = sqlite.readQuery("SELECT warpname FROM warps");
					String warplist = "";
					try {
						rs.next();
						while(!rs.isAfterLast()) {
							warplist += rs.getString("warpname") + ", ";
							rs.next();
						}
						if(warplist.length() > 2) warplist = warplist.substring(0, warplist.length()-2);
					} catch (Exception e) {
						e.printStackTrace();
					}
					sender.sendMessage(ChatColor.GREEN + "List of warps: ");
					sender.sendMessage(warplist);
				} else {
					noPermission(sender, cmd, args);
				}
			} else {
				if(p == null) {
					playerOnly(sender);
				} else if(!p.hasPermission("MitaBase.warp."+args[0])) {
					noPermission(sender, cmd, args);
				} else {
					String wname = args[0];
					ResultSet rs = sqlite.readQuery("SELECT COUNT(*) AS numWarpsWithThatName FROM warps WHERE warpname = '" + wname + "'");
					int nwwn = 0;
					try {
						nwwn = rs.getInt("numWarpsWithThatName");
					} catch (Exception e) {
						e.printStackTrace();
					}
					if(nwwn > 0) {
						rs = sqlite.readQuery("SELECT locX, locY, locZ, world FROM warps WHERE warpname = '" + args[0] + "'");
						double x = 0;
						double y = 0;
						double z = 0;
						String world = "";
						try {
							x = rs.getDouble("locX");
							y = rs.getDouble("locY");
							z = rs.getDouble("locZ");
							world = rs.getString("world");
						} catch (Exception e) {
							e.printStackTrace();
						}		
						p.teleport(new Location(Bukkit.getServer().getWorld(world), x, y, z));
					} else {
						p.sendMessage(ChatColor.RED + "Warp " + wname + " not found");
					}			
				}
			}
		} else if(cmd.getName().equalsIgnoreCase("unjail")) {
			if(p == null || p.hasPermission("MitaBase.unjail")) {
				if(args.length < 1) {
					return false;
				}
				sqlite.modifyQuery("UPDATE users SET jailed='0', jaileduntil='' WHERE username = '" + args[0] + "'");
				try {
					getServer().getPlayer(args[0]).sendMessage(ChatColor.GREEN + "You have been unjailed and you can now teleport home");
				} catch (Exception e) {
					sqlite.modifyQuery("INSERT INTO mail (senderid, receiverid, message) VALUES ((SELECT userid FROM users WHERE username = '" + args[0] + "'), (SELECT userid FROM users WHERE username = '" + sender.getName() + "'), 'You have been unjailed and you can now teleport home')");
				}
			} else {
				noPermission(sender, cmd, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("unmute")) {
					if(p == null || p.hasPermission("MitaBase.mute")) {
						if(args.length == 1) {
							Player p2 = Bukkit.getServer().getPlayer(args[0]);
							if (p2 == null) {
								sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
							} else {
								sqlite.modifyQuery("UPDATE users SET muted=0 WHERE username = '" + p2.getName() + "'");
							}		
						} else {
							return false;
						}
					} else {
						noPermission(sender, cmd, args);
					}	
		} else if (cmd.getName().equalsIgnoreCase("vanish")){
			if (p != null && p.hasPermission("MitaBase.vanish")) {
				ResultSet rs = sqlite.readQuery("SELECT vanished FROM users WHERE username = '" + p.getName() + "'");
				boolean van = false;
				try {
					van = rs.getBoolean("vanished");
				}catch (Exception e) {
					e.printStackTrace();
				}
				if(van){
					for(Player player: getServer().getOnlinePlayers()) {
				       player.showPlayer(p);				       
					}
					sqlite.modifyQuery("UPDATE users SET vanished = '0' WHERE username = '" + p.getName() + "'");
					p.sendMessage(ChatColor.BLUE + "You've been unvanished");
				} else {
					for(Player player: getServer().getOnlinePlayers()) {
					       if(!player.hasPermission("MitaBase.seevanished")) player.hidePlayer(p);
					}
					sqlite.modifyQuery("UPDATE users SET vanished = '1' WHERE username = '" + p.getName() + "'");
					p.sendMessage(ChatColor.BLUE + "You've been vanished");
				}
			} else if (p == null) {
				playerOnly(sender);
				return true;
			} else {
				noPermission(sender, cmd, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("warning")){
			if(args.length == 0) {
				if(p != null && p.hasPermission("MitaBase.warnings.list.self")) {
					ResultSet rs = sqlite.readQuery("SELECT * FROM warnings WHERE userid = (SELECT userid FROM users WHERE username = '" + p.getName() + "')");
					int c = 0;
					try {
						while(rs.next()) {
							p.sendMessage(ChatColor.YELLOW + "Level " + rs.getInt("level") + " warning for: " + rs.getString("reason") + ". Date: " + rs.getString("date"));
							c += rs.getInt("level");
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					sender.sendMessage("Total level: " + c);
					if(c == 0) p.sendMessage(ChatColor.GREEN + "You don't have any warnings. Take a cookie :)");
				} else if (p == null) {
					playerOnly(sender);
				} else {
					noPermission(sender, cmd, args);
				}
			} else {
				if(p == null || p.hasPermission("MitaBase.warnings.list.others")) {
					ResultSet rs = sqlite.readQuery("SELECT * FROM warnings WHERE userid = (SELECT userid FROM users WHERE username = '" + args[0] + "')");
					int c = 0;
					try {
						while(rs.next()) {
							sender.sendMessage(ChatColor.YELLOW + "Level " + rs.getInt("level") + " warning for: " + rs.getString("reason") + ". Date: " + rs.getString("date"));
							c += rs.getInt("level");
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					sender.sendMessage("Total level: " + c);
					if(c == 0) sender.sendMessage(ChatColor.GREEN + args[0] + " doesn't have any warnings.");
				} else {
					noPermission(sender, cmd, args);
				}
			}
		} else if (cmd.getName().equalsIgnoreCase("warn")){
			if(p == null || p.hasPermission("MitaBase.warnings.issue")) {
				if(args.length < 3) {
					return false;
				}
				ResultSet rs = sqlite.readQuery("SELECT level FROM warnings WHERE userid = (SELECT userid FROM users WHERE username = '" + args[0] + "')");
				int c = 0;
				try {
					while(rs.next()) {
						c += rs.getInt("level");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				int level = 0;
				try {
					level = Integer.parseInt(args[1]);
				} catch (Exception e) {
					sender.sendMessage(ChatColor.RED + "'level' must be a number");
				}
				String reason = "";
				for (int i=2; i < args.length; i++) {
					reason += args[i] + " ";
				}
				reason = reason.substring(0, reason.length()-1);
				DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZ");
				Date date = new Date();
				String now = df.format(date);
				sqlite.modifyQuery("INSERT INTO warnings (userid, date, reason, by, level) VALUES ((SELECT userid FROM users WHERE username = '" + args[0]+ "'), '" + now + "', '" + reason + "', (SELECT userid FROM users WHERE username = '" + sender.getName() + "'), '" + level + "')");
				int warning_level_limit = getConfig().getInt("warning_level_limit");
				console.sendMessage("Limit: " + warning_level_limit + ", level: " + (c + level));
				if(c +level >= warning_level_limit && warning_level_limit != 0) {
					sender.sendMessage(args[0] + ChatColor.RED + " has exceeded the limit of the warning level and will be banned.");
					String cmd_line = "ban " + args[0] + " You have exceeded your limits of warnings";
					getServer().dispatchCommand(console, cmd_line);
				}
			} else {
				noPermission(sender, cmd, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("weather")){
			if(args.length == 1) {
				if (p != null && p.hasPermission("MitaBase.weather")) {
						World w = p.getWorld();
						switch (args[0]) {
							case "sun":
								w.setStorm(false);
								w.setThundering(false);
								p.sendMessage(ChatColor.GREEN + "Weather set to sun in world " + w.getName());
								break;
							case "rain":
								w.setStorm(true);
								w.setThundering(false);
								p.sendMessage(ChatColor.GREEN + "Weather set to rain in world " + w.getName());
								break;
							case "storm":
								w.setStorm(true);
								w.setThundering(true);
								p.sendMessage(ChatColor.GREEN + "Weather set to storm in world " + w.getName());
								break;
							default:
								p.sendMessage(ChatColor.RED + "Weather needs to be sun, storm or rain");
						}
				} else if (p == null) {
					playerOnly(sender);
				} else {
					noPermission(sender, cmd, args);
				}
			} else if (args.length == 2) {
				if(p == null || p.hasPermission("MitaBase.weather")) {
					World w = Bukkit.getServer().getWorld(args[0]);
					if(w != null) {
						switch (args[1]) {
						case "sun":
							w.setStorm(false);
							w.setThundering(false);
							sender.sendMessage(ChatColor.GREEN + "Weather set to sun in world " + w.getName());
							break;
						case "rain":
							w.setStorm(true);
							w.setThundering(false);
							sender.sendMessage(ChatColor.GREEN + "Weather set to rain in world " + w.getName());
							break;
						case "storm":
							w.setStorm(true);
							w.setThundering(true);
							sender.sendMessage(ChatColor.GREEN + "Weather set to storm in world " + w.getName());
							break;
						default:
							sender.sendMessage(ChatColor.RED + "Weather needs to be sun, storm or rain");
						}
					} else {
						sender.sendMessage(ChatColor.RED + "World " + args[0] + " not found");
					}
				} else {
					noPermission(sender, cmd, args);
				}
			} else {
				return false;
			}
		} else if (cmd.getName().equalsIgnoreCase("wspawn")){
			if(p == null) {
				playerOnly(sender);
			} else if (!p.hasPermission("MitaBase.wspawn")){
				noPermission(sender, cmd, args);
			} else if (args.length == 0) {
				p.teleport(p.getWorld().getSpawnLocation());
			} else if (Bukkit.getServer().getWorld(args[0] + "") != null){;
				p.teleport(Bukkit.getServer().getWorld(args[0] + "").getSpawnLocation());
			} else {
				p.sendMessage(ChatColor.RED + "World " + args[0] + " doesn't exist.");
			}
		}
		return true;
	}
}