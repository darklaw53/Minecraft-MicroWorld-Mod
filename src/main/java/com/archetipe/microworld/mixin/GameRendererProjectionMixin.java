package com.archetipe.microworld.mixin;

import com.archetipe.microworld.item.ShrinkRayItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GameRenderer.class)
public class GameRendererProjectionMixin {

    // Vanilla near plane is 0.05. The shrunken camera sits ~0.02 from any
    // block it's touching, which is behind the near plane, so the block face
    // gets clipped and you see through it. 0.005 keeps the near plane well
    // clear of the shrunken camera without hurting depth precision at
    // Minecraft's render distances.
    private static final float SHRUNK_NEAR_PLANE = 0.005f;

    @ModifyConstant(method = "getProjectionMatrix", constant = @Constant(floatValue = 0.05f))
    private float microworld$shrinkNearPlane(float original) {
        Player player = Minecraft.getInstance().player;
        if (player != null && ShrinkRayItem.isShrunk(player)) {
            return SHRUNK_NEAR_PLANE;
        }
        return original;
    }
}