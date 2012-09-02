package org.digiplex.bukkitplugin.serversleeper;
import org.bukkit.command.CommandSender;
import org.digiplex.bukkitplugin.commander.api.BadEVPathException;
import org.digiplex.bukkitplugin.commander.api.CmdrEnvVarModule;
import org.digiplex.bukkitplugin.commander.api.CommanderAPI;
import org.digiplex.bukkitplugin.serversleeper.ServerSleeperPlugin.SLEEP_MODE;


public class SleeperEVM implements CmdrEnvVarModule {
	private ServerSleeperPlugin plugin;
	private static final String NAMESPACE = "ServerSleeper";
	
	public static void loadCommanderEVM(ServerSleeperPlugin plugin) {
		CommanderAPI.registerEVM(new SleeperEVM(plugin));
	}
	
	public static void unloadCommanderEVM() {
		CommanderAPI.unregisterEVM(NAMESPACE);
	}
	
	public SleeperEVM(ServerSleeperPlugin plugin) {
		this.plugin = plugin;
	}

	@Override public String getNamespace() {
		return NAMESPACE;
	}

	@Override public Object getEVValue(String varname, CommandSender sender) throws BadEVPathException {
		String[] varpath = varname.split("\\.");
		
		switch (varpath.length){
		case 0: throw new BadEVPathException("No EV path!");
		case 1:
			if (varpath[0].matches("(?i)mode")) return plugin.sleepMode.toString().toLowerCase();
			if (varpath[0].matches("(?i)time-?till")) throw new BadEVPathException("Time-till needs sub path!");
			break;
		case 2:
			if (varpath[0].matches("(?i)mode")) {
				if (varpath[1].matches("(?i)inactive|normal")) return plugin.sleepMode == SLEEP_MODE.INACTIVE;
				if (varpath[1].matches("(?i)sleep|timer")) return plugin.sleepMode == SLEEP_MODE.TIMER;
			}
			if (varpath[0].matches("(?i)time-?till")) {
				switch (plugin.sleepMode) {
				case INACTIVE:
					if (varpath[1].matches("(?i)shutdown")) return plugin.timeUntilShutdown - plugin.timeInactive + plugin.postponeTime;
					break;
				case TIMER:
					if (varpath[1].matches("(?i)shutdown")) return plugin.postponeTime;
					break;
				}
			}
			break;
		case 3:
			if (varpath[0].matches("(?i)mode")) {
				if (varpath[1].matches("(?i)inactive|normal")) {
					if (plugin.sleepMode != SLEEP_MODE.INACTIVE) return 0; //can't return if not in that mode
					if (varpath[2].matches("(?i)timeout")) return plugin.timeUntilShutdown;
					if (varpath[2].matches("(?i)inactive")) return plugin.timeInactive;
				}
				if (varpath[1].matches("(?i)sleep|timer")) {
					if (plugin.sleepMode != SLEEP_MODE.TIMER) return 0; //can't return if not in that mode
					if (varpath[2].matches("(?i)timeout")) return plugin.postponeTime;
					if (varpath[2].matches("(?i)inactive")) return 0;
				}
			}
			break;
		}
		throw new BadEVPathException("Nothing for the given path!");
	}

}
