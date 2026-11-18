package top.kircute.copycatcutaways.config;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Set;

public final class ClientConfig {

    private static final Set<Integer> VALID_SEGMENTS = Set.of(2, 4, 8, 16);

    private static final int INVALID_FALLBACK = 16;

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<Integer> PLACEMENT_SEGMENTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "Client-only placement snapping resolution.",
                "Valid values: 2, 4, 8, 16. Invalid values are reset to 16 when the config loads."
        );
        PLACEMENT_SEGMENTS = builder.define("placementSegments", 8);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static int placementSegments() {
        int value = PLACEMENT_SEGMENTS.get();
        return VALID_SEGMENTS.contains(value) ? value : INVALID_FALLBACK;
    }

    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        int value = PLACEMENT_SEGMENTS.get();
        if (!VALID_SEGMENTS.contains(value)) {
            PLACEMENT_SEGMENTS.set(INVALID_FALLBACK);
            PLACEMENT_SEGMENTS.save();
        }
    }
}
