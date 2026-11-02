package team.terrafirmagreg.exporter.modern;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import team.terrafirmagreg.exporter.engine.live.CommandReply;
import team.terrafirmagreg.exporter.engine.live.LiveExportWiringBase;

public final class ExportClientCommands {
    private final LiveExportWiringBase<?> dump;

    public ExportClientCommands(LiveExportWiringBase<?> dump) {
        this.dump = dump;
    }

    @SubscribeEvent
    public void onRegister(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("gameexporter")
                .then(Commands.literal("dump")
                        .executes(ctx -> reply(ctx.getSource(), dump.dumpCommand(null)))
                        .then(Commands.argument("products", StringArgumentType.greedyString())
                                .executes(ctx -> reply(
                                        ctx.getSource(),
                                        dump.dumpCommand(StringArgumentType.getString(ctx, "products"))))))
                .then(Commands.literal("status").executes(ctx -> reply(ctx.getSource(), dump.statusCommand())))
                .then(Commands.literal("out")
                        .executes(ctx -> reply(ctx.getSource(), dump.outShowCommand()))
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> reply(
                                        ctx.getSource(),
                                        dump.outSetCommand(StringArgumentType.getString(ctx, "path"))))));
        event.getDispatcher().register(root);
    }

    private static int reply(CommandSourceStack source, CommandReply reply) {
        if (reply.ok()) {
            source.sendSuccess(() -> Component.literal(reply.message()), false);
            return 1;
        }
        source.sendFailure(Component.literal(reply.message()));
        return 0;
    }
}
