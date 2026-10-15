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
                .then(policy("reconnect", false))
                .then(policy("death", true)));
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> policy(String name, boolean death) {
        return Commands.literal(name)
                .executes(context -> {
                    context.getSource().sendSuccess(() -> status(death), false);
                    return 1;
                })
                .then(Commands.argument("keep", BoolArgumentType.bool()).executes(context -> {
                    try {
                        boolean keep = BoolArgumentType.getBool(context, "keep");
                        if (death) Config.setKeepOnDeath(keep);
                        else Config.setKeepOnLogout(keep);
                    } catch (java.io.IOException | RuntimeException e) {
                        context.getSource().sendFailure(Component.literal("설정을 저장하지 못했습니다: " + e.getMessage()));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> status(death), true);
                    return 1;
                }));
    }
    private static Component status(boolean death) {
        boolean keep = death ? Config.KEEP_ON_DEATH.get() : Config.KEEP_ON_LOGOUT.get();
        return Component.literal((death ? "사망 시 울타리 묶임 유지: " : "재접속 시 묶임 유지: ") + (keep ? "켜짐 (true)" : "꺼짐 (false)"));
    }
}
