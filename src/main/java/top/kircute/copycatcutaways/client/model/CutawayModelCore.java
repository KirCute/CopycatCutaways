package top.kircute.copycatcutaways.client.model;

import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.neoforge.CopycatRenderContextNeoForge;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.neoforge.CopycatRenderContextNeoForge.CopycatBakedQuad;
import net.minecraft.world.level.block.state.BlockState;
import top.kircute.copycatcutaways.geometry.CutPlane;

public class CutawayModelCore extends CopycatModelCore.WithData<CutPlane> {

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {

        if (!(context instanceof CopycatRenderContextNeoForge neoForgeContext)) {
            return;
        }

        CutPlane plane = getData();
        if (plane == null) {
            return;
        }

        for (CopycatBakedQuad source : neoForgeContext.source()) {
            CutQuadClipper.clipAndEmit(source, plane, neoForgeContext.destination(), key);
        }

        CutQuadClipper.emitCap(plane, neoForgeContext.source(), neoForgeContext.destination(), key);
    }
}
