package org.digiplex.bukkitplugin.serversleeper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;


public class ServerSleeperPlugin extends JavaPlugin {
	public static final Logger Log = Logger.getLogger("Minecraft");
	
	public Configuration config;
	
	public int timeInactive = 0;
	public int postponeTime = 0;
	public int timeUntilShutdown = 60; //default 1 hour
	
	private final ServerSleeperPlugin ssplugin = this;
	
	public ServerSleeperPlugin() {}
	
	@Override public void onEnable() {
		config = this.getConfig();
		config.options().copyDefaults(true);
		this.saveConfig();
		
		timeUntilShutdown = config.getInt("timeout", 60);
		
		//register the inactivity monitor, initial delay is 10 sec, interval is 60 sec
		this.getServer().getScheduler().scheduleAsyncRepeatingTask(this, new InactivityMonitor(), 10*20L, 60*20L);
		
		loadCommanderEVM();
		
		
		
		Log.info("[ServerSleeper] Enabled");
	}

	@Override public void onDisable() {
		if (evmLoaded) unloadCommanderEVM();
		
		Log.info("[ServerSleeper] Disabled");
	}
	
	public class InactivityMonitor implements Runnable {
		Callable<Integer> getPlayers = new Callable<Integer>() {
			@Override public Integer call() throws Exception {
				return ssplugin.getServer().getOnlinePlayers().length;
			}
		};
		
		@Override public void run() {
			try {
				int numPlayers = getServer().getScheduler().callSyncMethod(ssplugin, getPlayers).get();
				Log.info("[ServerSleeper:InactivityMonitor] Players on server: "+numPlayers);
				if (numPlayers == 0) {
					//increase inactivity counters
					if (postponeTime > 0) postponeTime--;
					else {
						timeInactive++;
						if (timeInactive + 5 == timeUntilShutdown) {
							Log.info("[ServerSleeper] Server will shut down due to inactivity in 5 minutes.");
						} else if (timeInactive >= timeUntilShutdown) {
							getServer().getScheduler().scheduleSyncDelayedTask(ssplugin, new Runnable() {
								//shut down from main server thread
								@Override public void run() { shutdownServer(); }
							});
						}
					}
				} else {
					timeInactive = 0;
				}
				Log.info("[ServerSleeper:InactivityMonitor] tI="+timeInactive+"  pp="+postponeTime+"  tus="+timeUntilShutdown);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void shutdownServer() {
		Bukkit.broadcastMessage("[ServerSleeper] Shutting down server due to inactivity.");
		Bukkit.shutdown();
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
