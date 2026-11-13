package ru.backpacks.mod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * A small backpack built from boxes. Geometry lives in the player body's local space:
 * the body cube goes from y=0 (shoulders) down to y=12 (waist) and from z=-2 (chest)
 * to z=2 (back), so the backpack sits on top of the back at positive z.
 *
 * <p>Two independent meshes are baked: the main body (tinted with the tier body colour)
 * and the accents — flap, buckle and shoulder straps — tinted with the accent colour.
 * Both share one neutral texture, the colour comes from the per-submit tint.</p>
 */
public final class BackpackModel extends Model<Float> {
    private BackpackModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
    }

    public static BackpackModel base() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeListBuilder main = CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, 1.0F, 2.2F, 8.0F, 9.0F, 3.2F);
        CubeListBuilder pocket = CubeListBuilder.create().texOffs(24, 0)
                .addBox(-2.6F, 5.0F, 5.4F, 5.2F, 4.0F, 0.9F);
        CubeListBuilder bottom = CubeListBuilder.create().texOffs(0, 16)
                .addBox(-3.5F, 9.9F, 2.6F, 7.0F, 1.1F, 2.4F);

        root.addOrReplaceChild("body", main, PartPose.ZERO);
        root.addOrReplaceChild("pocket", pocket, PartPose.ZERO);
        root.addOrReplaceChild("bottom", bottom, PartPose.ZERO);
        return new BackpackModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
    }

    public static BackpackModel accents() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeListBuilder flap = CubeListBuilder.create().texOffs(0, 32)
                .addBox(-4.2F, 0.5F, 2.1F, 8.4F, 2.9F, 3.9F);
        CubeListBuilder buckle = CubeListBuilder.create().texOffs(28, 32)
                .addBox(-1.2F, 2.9F, 5.9F, 2.4F, 1.3F, 0.7F);
        CubeListBuilder strapLeft = CubeListBuilder.create().texOffs(28, 40)
                .addBox(-3.7F, 0.2F, -2.5F, 1.5F, 1.2F, 5.2F);
        CubeListBuilder strapRight = CubeListBuilder.create().texOffs(44, 40)
                .addBox(2.2F, 0.2F, -2.5F, 1.5F, 1.2F, 5.2F);

        root.addOrReplaceChild("flap", flap, PartPose.ZERO);
        root.addOrReplaceChild("buckle", buckle, PartPose.ZERO);
        root.addOrReplaceChild("strap_left", strapLeft, PartPose.ZERO);
        root.addOrReplaceChild("strap_right", strapRight, PartPose.ZERO);
        return new BackpackModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
    }

    @Override
    public void setupAnim(Float state) {
        // Static geometry: nothing to animate.
    }

    /** Renders both passes at the current pose, with the tier colours as tint. */
    public static void render(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                              int light, net.minecraft.resources.Identifier texture, BackpackState.Wear wear) {
        var renderType = net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture);
        collector.submitModel(BASE, 0.0F, pose, renderType, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                0xFF000000 | wear.bodyColor());
        collector.submitModel(ACCENTS, 0.0F, pose, renderType, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                0xFF000000 | wear.accentColor());
    }

    private static final BackpackModel BASE = base();
    private static final BackpackModel ACCENTS = accents();
}
