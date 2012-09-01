package org.digiplex.bukkitplugin.serversleeper;
import org.bukkit.command.CommandSender;
import org.digiplex.bukkitplugin.commander.api.BadEVPathException;
import org.digiplex.bukkitplugin.commander.api.CmdrEnvVarModule;
import org.digiplex.bukkitplugin.commander.api.CommanderAPI;


public class SleeperEVM implements CmdrEnvVarModule {
	private ServerSleeperPlugin plugin;
	
	public static void loadCommanderEVM(ServerSleeperPlugin plugin) {
		CommanderAPI.registerEVM(new SleeperEVM(plugin));
	}
	
	public static void unloadCommanderEVM() {
		CommanderAPI.unregisterEVM("ServerSleeper");
	}
	
	public SleeperEVM(ServerSleeperPlugin plugin) {
		this.plugin = plugin;
	}

	@Override public String getNamespace() {
		return "ServerSleeper";
	}

	@Override public Object getEVValue(String varname, CommandSender sender) throws BadEVPathException {
		return null;
	}

}
