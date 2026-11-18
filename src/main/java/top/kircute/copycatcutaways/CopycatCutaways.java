package top.kircute.copycatcutaways;
import top.kircute.copycatcutaways.network.CommitCutPayload;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(CopycatCutaways.MODID)
public class CopycatCutaways {

    public static final String MODID = "copycatcutaways";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CopycatCutaways(IEventBus modEventBus, ModContainer ignoredModContainer) {

        ModRegistry.register(modEventBus);

        modEventBus.addListener(CommitCutPayload::register);
    }
}
