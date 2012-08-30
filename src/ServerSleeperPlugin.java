import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;
import org.digiplex.bukkitplugin.commander.api.CommanderAPI;


public class ServerSleeperPlugin extends JavaPlugin {
	public static final Logger Log = Logger.getLogger("Minecraft");
	
	public Configuration config;
	public boolean evmLoaded = false;
	
	public int timeInactive = 0;
	public int postponeTime = 0;
	public int timeUntilShutdown = 60; //default 1 hour
	
	private final ServerSleeperPlugin ssplugin = this;
	
	public ServerSleeperPlugin() {}
	
	@Override public void onEnable() {
		config = this.getConfig();
		config.options().copyDefaults(true);
		this.saveConfig();
		
		//register the inactivity monitor, initial delay is 10 sec, interval is 60 sec
		this.getServer().getScheduler().scheduleAsyncRepeatingTask(this, new InactivityMonitor(), 10*20L, 60*20L);
		
		if (this.getServer().getPluginManager().isPluginEnabled("Commander")) 
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
				
				if (numPlayers == 0) {
					//increase inactivity counters
					if (postponeTime > 0) postponeTime--;
					else {
						timeInactive++;
						if (timeInactive >= timeUntilShutdown) {
							getServer().getScheduler().scheduleSyncDelayedTask(ssplugin, new Runnable() {
								//shut down from main server thread
								@Override public void run() { shutdownServer(); }
							});
						}
					}
				} else {
					timeInactive = 0;
				}
				
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
	
	private void loadCommanderEVM() {
		CommanderAPI.registerEVM(new SleeperEVM(this));
		evmLoaded = true;
	}
	
	private void unloadCommanderEVM() {
		CommanderAPI.unregisterEVM("ServerSleeper");
	}
}
