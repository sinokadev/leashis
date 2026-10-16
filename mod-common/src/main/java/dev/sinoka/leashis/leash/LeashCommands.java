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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, LeashManager manager) {
        dispatcher.register(Commands.literal("leashis")
                .then(Commands.literal("pull").executes(context -> pull(context.getSource(), manager)))
                .then(policy("reconnect"))
                .then(policy("death"))
                .then(policy("projectile"))
                .then(policy("fencebreak"))
                .then(policy("piston")));
        if (dispatcher.getRoot().getChild("l") == null)
            dispatcher.register(Commands.literal("l").executes(context -> pull(context.getSource(), manager)));
    }
    private static int pull(CommandSourceStack source, LeashManager manager) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var result = manager.pull(source.getPlayerOrException());
        Component message = Component.literal(switch (result) {
            case STARTED -> "목줄을 당깁니다.";
            case NO_TARGET -> "직접 끌고 있는 플레이어가 없습니다.";
            case COOLDOWN -> "잠시 후 다시 당길 수 있습니다.";
        });
        source.sendSuccess(() -> message, false);
        return result == dev.sinoka.leashis.core.LeashEngine.PullResult.STARTED ? 1 : 0;
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> policy(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> status(name), false);
                    return 1;
                })
                .then(Commands.argument("keep", BoolArgumentType.bool()).executes(context -> {
                    try {
                        boolean keep = BoolArgumentType.getBool(context, "keep");
                        switch (name) {
                            case "reconnect" -> Config.setKeepOnLogout(keep);
                            case "death" -> Config.setKeepOnDeath(keep);
                            case "projectile" -> Config.setProtectProjectile(keep);
                            case "fencebreak" -> Config.setProtectFenceBreak(keep);
                            case "piston" -> Config.setProtectPiston(keep);
                            default -> throw new IllegalArgumentException(name);
                        }
                    } catch (java.io.IOException | RuntimeException e) {
                        context.getSource().sendFailure(Component.literal("설정을 저장하지 못했습니다: " + e.getMessage()));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> status(name), true);
                    return 1;
                }));
    }
    private static Component status(String name) {
        boolean enabled = switch (name) {
            case "reconnect" -> Config.KEEP_ON_LOGOUT.get();
            case "death" -> Config.KEEP_ON_DEATH.get();
            case "projectile" -> Config.PROTECT_PROJECTILE.get();
            case "fencebreak" -> Config.PROTECT_FENCE_BREAK.get();
            case "piston" -> Config.PROTECT_PISTON.get();
            default -> throw new IllegalArgumentException(name);
        };
        String label = switch (name) {
            case "reconnect" -> "재접속 시 묶임 유지";
            case "death" -> "사망 시 울타리 묶임 유지";
            case "projectile" -> "목줄 투사체 보호";
            case "fencebreak" -> "묶인 플레이어의 울타리 파괴 방지";
            case "piston" -> "울타리 피스톤 이동 방지";
            default -> throw new IllegalArgumentException(name);
        };
        return Component.literal(label + ": " + (enabled ? "켜짐 (true)" : "꺼짐 (false)"));
    }
}
