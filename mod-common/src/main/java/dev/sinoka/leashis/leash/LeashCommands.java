package dev.sinoka.leashis.leash;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import dev.sinoka.leashis.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Administrator commands shared by the mod loaders. */
public final class LeashCommands {
    private LeashCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("leashis")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reconnect")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> status(), false);
                            return 1;
                        })
                        .then(Commands.argument("keep", BoolArgumentType.bool())
                                .executes(context -> {
                                    try {
                                        Config.setKeepOnLogout(BoolArgumentType.getBool(context, "keep"));
                                    } catch (java.io.IOException | RuntimeException e) {
                                        context.getSource().sendFailure(Component.literal("설정을 저장하지 못했습니다: " + e.getMessage()));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> status(), true);
                                    return 1;
                                }))));
    }
    private static Component status() {
        return Component.literal("재접속 시 묶임 유지: " + (Config.KEEP_ON_LOGOUT.get() ? "켜짐 (true)" : "꺼짐 (false)"));
    }
}
