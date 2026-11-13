package ru.backpacks.mod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;

/**
 * Draws a backpack on the back of every player whose entity id is present in
 * {@link BackpackState}. Rendered as a normal render layer of the player model,
 * so it follows body rotation (sneaking, swimming, elytra).
 */
public final class BackpackLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("backpacks", "textures/entity/backpack.png");

    private BackpackLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (type != EntityTypes.PLAYER) {
                return;
            }
            RenderLayerParent<AvatarRenderState, PlayerModel> parent =
                    (RenderLayerParent<AvatarRenderState, PlayerModel>) renderer;
            helper.register(new BackpackLayer(parent, context.getModelSet()));
        });
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light,
                       AvatarRenderState state, float yRot, float xRot) {
        BackpackState.Wear wear = BackpackState.wearOf(state.id);
        if (state.isInvisible || wear == null) {
            return;
        }

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        BackpackModel.render(pose, collector, light, TEXTURE, wear);
        pose.popPose();
    }
}
