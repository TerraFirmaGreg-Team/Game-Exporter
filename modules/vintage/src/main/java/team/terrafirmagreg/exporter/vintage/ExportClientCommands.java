package team.terrafirmagreg.exporter.vintage;

import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import team.terrafirmagreg.exporter.engine.live.CommandReply;
import team.terrafirmagreg.exporter.engine.live.LiveExportWiringBase;

public final class ExportClientCommands extends CommandBase {
    private final LiveExportWiringBase<?> dump;

    private ExportClientCommands(LiveExportWiringBase<?> dump) {
        this.dump = dump;
    }

    public static void register(LiveExportWiringBase<?> dump) {
        ClientCommandHandler.instance.registerCommand(new ExportClientCommands(dump));
    }

    private static String join(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public String getName() {
        return "gameexporter";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/gameexporter <dump [products]|status|out [path]>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
            return;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "dump" -> {
                String products = args.length > 1 ? join(args, 1) : null;
                reply(sender, dump.dumpCommand(products));
                return;
            }
            case "status" -> {
                reply(sender, dump.statusCommand());
                return;
            }
            case "out" -> {
                reply(sender, args.length == 1 ? dump.outShowCommand() : dump.outSetCommand(join(args, 1)));
                return;
            }
        }
        sender.sendMessage(new TextComponentString(getUsage(sender)));
    }

    private static void reply(ICommandSender sender, CommandReply reply) {
        sender.sendMessage(new TextComponentString(reply.message()));
    }
}
