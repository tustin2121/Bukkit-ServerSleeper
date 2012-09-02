package org.digiplex.bukkitplugin.serversleeper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;


public class ServerSleeperPlugin extends JavaPlugin {
	public static final Logger Log = Logger.getLogger("Minecraft");
	public static final String MSG_PARSE_ERR = "Could not parse time format. Use format \"0h 0m\" or \"00:00\"";
	
	public Configuration config;
	
	public enum SLEEP_MODE {
		INACTIVE,
		TIMER,
	}
	public SLEEP_MODE sleepMode = null;
	protected int timeUntilShutdown = 60; //default 1 hour
	
	protected int timeInactive = 0;
	protected int postponeTime = 0;
	
	private final ServerSleeperPlugin ssplugin = this;
	
	public ServerSleeperPlugin() {}
	
	@Override public void onEnable() {
		config = this.getConfig();
		config.options().copyDefaults(true);
		this.saveConfig();
		
		//timeUntilShutdown = config.getInt("timeout", 60);
		sleepMode = SLEEP_MODE.INACTIVE;
		timeUntilShutdown = parseHourMinuteFormat(config.getString("timeout"), 60);
		timeInactive = 0;
		postponeTime = 0;
		if (timeUntilShutdown == -1) {
			Log.info("[ServerSleeper] Server inactivity shutdown disabled.");
		} else {
			Log.info("[ServerSleeper] Server inactivity shutdown set for "+timeUntilShutdown+" minutes.");
		}
		
		//register the inactivity monitor, initial delay is 10 sec, interval is 60 sec
		this.getServer().getScheduler().scheduleAsyncRepeatingTask(this, new InactivityMonitor(), 10*20L, 60*20L);
		
		loadCommanderEVM();
		
		this.getCommand("sleeper").setExecutor(new AdminCommand());
		
		Log.info("[ServerSleeper] Enabled");
	}

	@Override public void onDisable() {
		if (evmLoaded) unloadCommanderEVM();
		
		Log.info("[ServerSleeper] Disabled");
	}
	
	public void onReload() {
		this.reloadConfig();
		
		timeUntilShutdown = config.getInt("timeout", 60);
	}
	
	private static Pattern stdtimep = Pattern.compile("(\\d*)\\:(\\d{2})", Pattern.CASE_INSENSITIVE);
	private static Pattern hmtimep = Pattern.compile("(?:(\\d+)h)?\\s*(?:(\\d+)m)?", Pattern.CASE_INSENSITIVE);
	public int parseHourMinuteFormat(String s, int def) {
		if (s == null || s.isEmpty()) {
			Log.severe("[ServerSleeper] "+MSG_PARSE_ERR+". (null or empty string)");
			return def;
		}
		Matcher m;
		if ((m = stdtimep.matcher(s)).matches()) { //apparently Configuration converts times in std format
			String hs = m.group(1);
			String ms = m.group(2);
			int h = Integer.parseInt(hs);
			int min = Integer.parseInt(ms);
			
			return h * 60 + min;
		} else if ((m = hmtimep.matcher(s)).matches()) {
			String hs = m.group(1);
			String ms = m.group(2);
			if ((hs == null) && (ms == null)) {
				Log.severe("[ServerSleeper] "+MSG_PARSE_ERR+". ("+s+")");
				return def;
			}
			int h = (hs==null)?0:Integer.parseInt(hs);
			int min = (ms==null)?0:Integer.parseInt(ms);
			
			return h * 60 + min;
		} else {
			try {
				int i = Integer.parseInt(s);
				if (i <= 0) throw new NumberFormatException();
				return i;
			} catch (NumberFormatException ex) {
				Log.severe("[ServerSleeper] "+MSG_PARSE_ERR+". ("+s+")");
				return def;
			}
		}
	}
	
	public class AdminCommand implements CommandExecutor {
		@Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
			if (args.length < 1) return false;
			if (args[0].equalsIgnoreCase("reload")) {
				onReload();
				sender.sendMessage("Reload complete!");
				return true;
				
			} else if (args[0].equalsIgnoreCase("reset")) {
				timeInactive = 0;
				postponeTime = 0;
				sleepMode = SLEEP_MODE.INACTIVE;
				sender.sendMessage("Inactivity and postponement counters reset.");
				return true;
				
			} else if (args[0].matches("(?i)postpone|delay|pp")) {
				if (args.length < 2) {
					sender.sendMessage(String.format("/%s %s [time to postpone]", label, args[0]));
					sender.sendMessage("'time to postpone' can be minutes or hours with 'm' after minutes and 'h' after hours");
				} else {
					int time = parseHourMinuteFormat(args[1], -1);
					if (time == -1) {
						sender.sendMessage(MSG_PARSE_ERR);
						return true;
					}
					
					postponeTime += time;
					sender.sendMessage("Postponed shutdown another "+time+" minutes (total "+postponeTime+").");
				}
				return true;
				
			} else if (args[0].matches("(?i)timer|sleep")) {
				if (args.length < 2) {
					sender.sendMessage(String.format("/%s %s [time till sleep]", label, args[0]));
					sender.sendMessage("'time till sleep' can be minutes or hours with 'm' after minutes and 'h' after hours");
				} else if (args[1].matches("cancel")) {
					sleepMode = SLEEP_MODE.INACTIVE;
					timeInactive = 0;
					sender.sendMessage("Sleep timer shutdown canceled.");
				} else {
					int time = parseHourMinuteFormat(args[1], -1);
					if (time == -1) {
						sender.sendMessage(MSG_PARSE_ERR);
						return true;
					}
					sleepMode = SLEEP_MODE.TIMER;
					postponeTime = time;
					sender.sendMessage("Sleep timer enabled: server will shutdown in "+postponeTime+" minutes");
					Bukkit.broadcastMessage("[ServerSleeper] Notice: Server will shut down in "+postponeTime+" minutes.");
				}
				return true;
				
			} else if (args[0].matches("(?i)set|timeout")) {
				if (args.length < 2) {
					sender.sendMessage(String.format("/%s %s [time]", label, args[0]));
					sender.sendMessage("'time' can be minutes or hours with 'm' after minutes and 'h' after hours");
				} else {
					int time = parseHourMinuteFormat(args[1], -1);
					if (time == -1) {
						sender.sendMessage(MSG_PARSE_ERR);
						return true;
					}
					if (time < timeUntilShutdown) timeInactive = 0;
					timeUntilShutdown = time;
					sender.sendMessage("Inactivity shutdown timer set to "+timeUntilShutdown);
					if (sleepMode != SLEEP_MODE.INACTIVE)
						sender.sendMessage("Notice: Inactivity shutdown not active: current mode is "+sleepMode.toString());
				}
				return true;
				
			}
			return false;
		}
	}
	
	private class InactivityMonitor implements Runnable {
		Callable<Integer> getPlayers = new Callable<Integer>() {
			@Override public Integer call() throws Exception {
				return ssplugin.getServer().getOnlinePlayers().length;
			}
		};
		
		@Override public void run() {
			switch (sleepMode) {
			case INACTIVE:
				if (timeUntilShutdown == -1) return;
				if (postponeTime > 0) { 
					postponeTime--; return;
				}
				try {
					int numPlayers = getServer().getScheduler().callSyncMethod(ssplugin, getPlayers).get();
					//Log.info("[ServerSleeper:InactivityMonitor] Players on server: "+numPlayers);
					if (numPlayers == 0) {
						//increase inactivity counters
						{
							timeInactive++;
							if (timeInactive + 5 == timeUntilShutdown) {
								Log.info("[ServerSleeper] Server will shut down due to inactivity in 5 minutes.");
							} else if (timeInactive >= timeUntilShutdown) {
								shutdownServer("due to inactivity");
							}
						}
					} else {
						timeInactive = 0;
					}
					//Log.info("[ServerSleeper:InactivityMonitor] tI="+timeInactive+"  pp="+postponeTime+"  tus="+timeUntilShutdown);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
				break;
			case TIMER:
				try {
					postponeTime--;
					if (postponeTime >= 60) {
						if (postponeTime % 60 == 0) {
							//warn every hour
							int hoursUntil = postponeTime / 60;
							broadcastMessage("[ServerSleeper] Notice: Server will shut down in "+hoursUntil+" hours time.");
						}
					} else {
						if (postponeTime == 0) {
							//alert and shut down
							broadcastMessage("[ServerSleeper] Alert: Server is shutting down NOW!");
							shutdownServer("due to sleep timer");
						} else if (postponeTime <= 5) {
							//alert last 5 minutes
							broadcastMessage("[ServerSleeper] Alert: Server will shut down in "+postponeTime+" minute(s)!");
						} else if (postponeTime % 15 == 0) {
							//warn every final 15 minutes
							broadcastMessage("[ServerSleeper] Notice: Server will shut down in "+postponeTime+" minutes.");
						}
					}
				} finally {}
				break;
			}
		}
	}
	
	/////////////// Main Thread Action Methods ///////////////
	
	public void shutdownServer(final String reason) {
		getServer().getScheduler().scheduleSyncDelayedTask(ssplugin, new Runnable() {
			//shut down from main server thread
			@Override public void run() { 
				Bukkit.broadcastMessage("[ServerSleeper] Shutting down server "+reason);
				Bukkit.shutdown();
			}
		}, 10);
	}
	
	public void broadcastMessage(final String message) {
		getServer().getScheduler().scheduleSyncDelayedTask(ssplugin, new Runnable() {
			@Override public void run() { 
				Bukkit.broadcastMessage(message);
			}
		});
	}
	
	//////////////// Commander Hooks ///////////////////
	private boolean evmLoaded = false;
	
	private void loadCommanderEVM() {
		if (!this.getServer().getPluginManager().isPluginEnabled("Commander"))
			return;
		
		try {
			Class<?> c = Class.forName("org.digiplex.bukkitplugin.serversleeper.SleeperEVM");
			Method m = c.getDeclaredMethod("loadCommanderEVM", ServerSleeperPlugin.class);
			m.invoke(null, this);
			evmLoaded = true;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}
	
	private void unloadCommanderEVM() {
		if (!evmLoaded) return;
		try {
			Class<?> c = Class.forName("org.digiplex.bukkitplugin.serversleeper.SleeperEVM");
			Method m = c.getDeclaredMethod("unloadCommanderEVM");
			m.invoke(null);
			evmLoaded = false;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}
}
