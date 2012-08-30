import org.bukkit.command.CommandSender;
import org.digiplex.bukkitplugin.commander.api.BadEVPathException;
import org.digiplex.bukkitplugin.commander.api.CmdrEnvVarModule;


public class SleeperEVM implements CmdrEnvVarModule {
	private ServerSleeperPlugin plugin;
	
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
