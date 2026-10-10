package dev.sinoka.leashis;

import com.mojang.logging.LogUtils;
import dev.sinoka.leashis.leash.LeashEvents;
import dev.sinoka.leashis.leash.LeashManager;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Leashis.MODID)
public final class Leashis {
    public static final String MODID = "leashis";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Leashis(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(new LeashEvents(new LeashManager()));
        LOGGER.info("Leashis initialized");
    }
}
