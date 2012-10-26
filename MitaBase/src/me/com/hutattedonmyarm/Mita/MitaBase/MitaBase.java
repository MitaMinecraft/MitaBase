package me.com.hutattedonmyarm.Mita.MitaBase;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.permission.*;

public class MitaBase extends JavaPlugin implements Listener {
	private Logger logger = Bukkit.getServer().getLogger();
	
	private Permission permission;
	
	private String pluginPrefix = "[MitaBase] ";
	private String databaseName = "MitaBaseDB.db";
	
	private ConsoleCommandSender console;
	
	private SQLite sqlite = new SQLite(logger, "[MitaBase]", databaseName, "plugins/MitaBase/");
	
	private boolean cmdlogger = true;

	private void setupDatabase() {		 
		if (!sqlite.tableExists("users")) {
			String query = "CREATE TABLE users (userid INTEGER PRIMARY KEY, username TEXT, numofhomes INTEGER, afk INTEGER, banned INTEGER, reason TEXT, until TEXT, by TEXT, muted INTEGER)";
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
		
	}
	private boolean setupPermissions()
    {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            permission = permissionProvider.getProvider();
        }
        return (permission != null);
    }
	private void loadStuff() {
		console.sendMessage(pluginPrefix + ChatColor.RESET + "Enabling MitaBase...");
		console.sendMessage(pluginPrefix + "Setting up...");
		setupDatabase();
	    setupPermissions();
	    cmdlogger = getConfig().getBoolean("command_logger");
	    console.sendMessage(pluginPrefix + "Scanning for worlds...");
	    List<World> worlds = Bukkit.getServer().getWorlds();
	    for(int i = 0; i < worlds.size(); i++) {
	    	try {
	    		ResultSet rs = sqlite.readQuery("SELECT worldid FROM worlds WHERE worldname = '" + worlds.get(i).getName() + "'");
				if(rs != null && !rs.next()) { //World doesn't exist in DB
					sqlite.modifyQuery("INSERT INTO worlds (worldname) VALUES ('" + worlds.get(i).getName() + "')");
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
	    }
	    console.sendMessage(pluginPrefix + ChatColor.GREEN + "Found " + worlds.size() + " worlds");
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
		if(itemString.contains(":") && itemString.length() != itemString.indexOf(":")+1) { //String contains ':', but it's not the last character
			matString = itemString.split(":")[0];
			dmgString = itemString.split(":")[1];
		} else {
			matString = itemString;
		}
		Material m = null;
		try {
			m = Material.getMaterial(Integer.parseInt(matString));
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
		} else {
			sender.sendMessage(sender.getName() + " " + sender.toString());
		}
		if(p == null || p.hasPermission("MitaBase.give")) {
			/* /give [player] <item> [amount]
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
					sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
							sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
			sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
	public void playerJoin(PlayerJoinEvent evt) {
		Player p = evt.getPlayer();
		ResultSet rs = sqlite.readQuery("SELECT userid, banned, reason, until FROM users WHERE username = '" + p.getName() + "'");
		try {
			if(rs != null && !rs.next()) { //User doesn't exist in DB
				Bukkit.getServer().dispatchCommand(p, "spawn");
				sqlite.modifyQuery("INSERT INTO users (username, numofhomes, afk, banned, muted) VALUES ('" + p.getName() + "', 0, 0, 0, 0)");
				console.sendMessage(pluginPrefix + ChatColor.GREEN + "New user " + p.getName());
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
				Bukkit.getServer().broadcastMessage(mc + getConfig().getString("new_user.welcome_message").replace("{username}", uc + p.getName() + mc));
			} else {
				int banned = rs.getInt("banned");
				if(banned == 1){
					Date until = new Date();
					String u = rs.getString("until");
					if (!u.equals("Forever")) {
						until = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss ZZ").parse(u);
					}
					if(until.before(new Date())) {
						sqlite.modifyQuery("UPDATE users SET banned=0, reason='', until='', by='' WHERE username='" + p.getName() + "'");
					} else {
						p.kickPlayer("You're banned until " + u + ". Reason: " + rs.getString("reason"));
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		String group = permission.getPrimaryGroup(p);
		console.sendMessage("group: " + group);
		ChatColor c = ChatColor.WHITE;
		try {
			c = ChatColor.valueOf(getConfig().getString("colors.groups." + group));
			console.sendMessage("groupcolor: " + getConfig().getString("colors.groups." + group));
		} catch (Exception e) {
			
		}
		try {
			c = ChatColor.valueOf(getConfig().getString("colors.players." + p.getName()));
			console.sendMessage("playercolor: " + getConfig().getString("colors.players." + p.getName()));
		} catch (Exception e) {
			
		}
		p.setPlayerListName(c + p.getName());
	}
	@EventHandler
	public void playerLogout(PlayerQuitEvent evt){
		sqlite.modifyQuery("UPDATE users SET afk=0 WHERE username = '" + evt.getPlayer().getName() + "'");
	}
	@EventHandler
	public void playerCommand(PlayerCommandPreprocessEvent evt) {
		if(cmdlogger) {
			console.sendMessage(ChatColor.GRAY + "Player " + evt.getPlayer().getName() + " entered command: " + evt.getMessage());
		}
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
	}
	@EventHandler(priority = EventPriority.LOWEST)	
	public void playerOuchByMob(EntityDamageByEntityEvent evt) {
		World w = evt.getEntity().getWorld();
		ResultSet rs = sqlite.readQuery("SELECT mobdmg FROM worlds WHERE worldname = '" + w.getName() + "'");
		boolean dmg = true;
		try {
			dmg = rs.getBoolean("mobdmg");
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (!dmg && !evt.getDamager().getType().equals(EntityType.PLAYER)) {
			evt.setCancelled(true);
			evt.setDamage(0);
		}
	}
	@EventHandler(priority = EventPriority.MONITOR)
	public void blockPlaced(BlockPlaceEvent evt) {
		Block b = evt.getBlockPlaced();
		Player player = evt.getPlayer();
		Location l = b.getLocation();
		Location lo = b.getLocation();
		
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
		Block b = evt.getBlock();
		//Player player = evt.getPlayer();
		if(b.getType().equals(Material.CHEST)) {
			sqlite.modifyQuery("DELETE FROM chests WHERE locX = '" + b.getX()  + "' AND locY = '" + b.getY()  + "' AND locZ =  '" + b.getZ()  + "' AND world = '" + b.getWorld().getName() + "'");	
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
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(!cmdlogger) {
			String argString = " ";
			for(int i = 0; i < args.length; i++) {
				argString += args[i] + " ";
			}
			console.sendMessage(pluginPrefix + sender.getName() + " wrote command: " + cmd.getName() + argString);
		}		
		Player p = null;
		if(sender instanceof Player) {
			p = (Player) sender;
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
				if(!afk) Bukkit.getServer().broadcastMessage(ChatColor.DARK_AQUA +  p.getName() + " is now afk");
				if(afk) Bukkit.getServer().broadcastMessage(ChatColor.DARK_AQUA +  p.getName() + " is no longer afk");
			} else {
				sender.sendMessage(ChatColor.RED + "Only players can use this command");
			}
		}else if(cmd.getName().equalsIgnoreCase("ban")) {
			if(p == null || p.hasPermission("MitaBase.ban")) {
				if(args.length < 1) {
					sender.sendMessage(ChatColor.RED + "Too few arguments");
				} else {
					Player p2 = Bukkit.getOfflinePlayer(args[0]).getPlayer();
					if(p2 == null) {
						sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found! Trying to ban offline player");
					}
						if(p2 == null || !p2.hasPermission("MitaBase.banprotect") || (p == null || p.hasPermission("MitaBase.banprotected"))) {
							int sec = 0;
							String reason = "Banned by an operator";
							if(args.length > 1) {
								try {
									sec = Integer.parseInt(args[1]);
								} catch(Exception e){
									sec = 0;
								}
								if(sec == 0){
									for(int i = 1; i < args.length; i++) {
										reason += args[i];
										reason += "";
									}
								} else if (args.length > 2){
									reason = "";
									for(int i = 2; i < args.length; i++) {
										reason += args[i];
										reason += "";
									}
								}
							} else {
								sec = 0;
							}
							String bannername = "Console";
							if(p != null) bannername = p.getName();
							DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZ");
							Date date = new Date();
							String now = df.format(date);
							Calendar c = Calendar.getInstance();
							c.add(Calendar.SECOND, sec);
							Date banUntil = c.getTime();
							String until = df.format(banUntil);
							if (sec == 0) until = "Forever";
							String finalText = args[0] + "|" + now + "|" + bannername + "|" + until + "|" + reason;
							File f = new File("banned-players.txt");
							if(!f.exists()) {
								sender.sendMessage(ChatColor.RED + "An error occured, the ban-file cannot be found!");
							} else {
								FileWriter fstream;
								try {
									fstream = new FileWriter(f.getAbsolutePath(),true);
									BufferedWriter out = new BufferedWriter(fstream);
									out.write(finalText);
									out.newLine();
									out.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
								
							}
							sqlite.modifyQuery("UPDATE users SET banned=1, reason='" + reason + "', until='"+ until + "', by='" + bannername + "' WHERE username='" + args[0] + "'");
							Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "Player " + args[0] + " has been banned by " + bannername + " until " + until + ". Reason: " + reason);
							p.kickPlayer("You're banned until " + until + ". Reason: " + reason);
						
					}
				}
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
				sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
					sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
		} else if(cmd.getName().equalsIgnoreCase("invsee")) {
			if (p != null && p.hasPermission("MitaBase.invsee.see")) {
				if (args.length == 1) {
					Player p2 = Bukkit.getServer().getPlayer(args[0]);
					if (p2 != null) {
						p.openInventory(p2.getInventory());
					} else {
						p.sendMessage(ChatColor.RED + "Player " + args[0] + " not found");
					}
				} else {
					return false;
				}
			} else if (p == null) {
				sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
					sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
		} else if (cmd.getName().equalsIgnoreCase("sethome")){
			setHome(sender, args, cmd);
		} else if(cmd.getName().equalsIgnoreCase("setspawn")) {
			if(p == null) {
				sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
				sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
				sender.sendMessage(ChatColor.RED + "Only players can use this command");
			} else if (!p.hasPermission("MitaBase.setwspawn")){
				noPermission(sender, cmd, args);
			} else {
				p.getWorld().setSpawnLocation(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
				p.sendMessage(ChatColor.GREEN + "Spawn of world " + p.getLocation().getWorld().getName() + " successfully set");
			}
		} else if(cmd.getName().equalsIgnoreCase("spawn")) {
			if(p == null) {
				sender.sendMessage(ChatColor.RED + "Only players can use this command");
			} else if (!p.hasPermission("MitaBase.spawn")){
				noPermission(sender, cmd, args);
			}else {
				p.teleport(new Location(Bukkit.getServer().getWorld(getConfig().getString("spawn.world")), getConfig().getDouble("spawn.x"), getConfig().getDouble("spawn.y"), getConfig().getDouble("spawn.z")));
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
				} else if (p == null) {
					sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
					sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
					sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
					sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
					sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
				boolean van = true;
				 for(Player player: getServer().getOnlinePlayers()) {
				        if(!player.hasPermission("MitaBase.seevanished") && player.canSee(p)) {
				           player.hidePlayer(p);
				        } else if (!player.hasPermission("MitaBase.seevanished") && !player.canSee(p)) {
				        	player.showPlayer(p);
				        	van = false;
				        }
				 }
				 if(van) {
					 p.sendMessage(ChatColor.GREEN + "You have been vanished");
				 } else {
					 p.sendMessage(ChatColor.GREEN + "You have been unvanished");
				 }
			} else if (p == null) {
				sender.sendMessage(ChatColor.RED + "Only players can use this command");
				return true;
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
					sender.sendMessage(ChatColor.RED + "Only players can use this command");
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
				sender.sendMessage(ChatColor.RED + "Only players can use this command");
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