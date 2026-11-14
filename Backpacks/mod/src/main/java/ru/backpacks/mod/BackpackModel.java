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
 * A detailed backpack built from boxes. Geometry lives in the player body's local space:
 * the body cube goes from y=0 (shoulders) down to y=12 (waist) and from z=-2 (chest)
 * to z=2 (back), so the backpack sits on the back at positive z.
 *
 * <p>Four independent meshes are baked: the main body (tinted with the tier body colour),
 * the accents (flap, buckle, shoulder straps), a metal frame for tiers 4+ and a bedroll
 * for tiers 10+. All share one neutral texture, the colour comes from the per-submit
 * tint.</p>
 */
public final class BackpackModel extends Model<Float> {
    private static final float FLAP_TINT = 0.0F;

    private BackpackModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
    }

    /** Cloth, leather and the padded shell. */
    public static BackpackModel base() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeListBuilder body = CubeListBuilder.create().texOffs(0, 0)
                // Main shell.
                .addBox(-3.6F, 1.4F, 2.1F, 7.2F, 7.8F, 2.9F)
                // Reinforced bottom.
                .addBox(-3.4F, 9.2F, 2.2F, 6.8F, 0.7F, 2.7F)
                // Rolled top.
                .addBox(-3.4F, 0.9F, 2.2F, 6.8F, 0.55F, 2.7F)
                .addBox(-3.9F, 0.95F, 2.3F, 0.55F, 0.5F, 2.5F)
                .addBox(3.35F, 0.95F, 2.3F, 0.55F, 0.5F, 2.5F)
                // Side pockets.
                .addBox(-4.0F, 4.3F, 2.35F, 0.45F, 3.0F, 2.4F)
                .addBox(3.55F, 4.3F, 2.35F, 0.45F, 3.0F, 2.4F)
                // Front pocket.
                .addBox(-2.4F, 4.9F, 4.95F, 4.8F, 2.8F, 0.55F)
                // Back panel resting against the player.
                .addBox(-3.5F, 1.5F, 2.0F, 7.0F, 7.5F, 0.15F);

        root.addOrReplaceChild("body", body, PartPose.ZERO);
        return new BackpackModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
    }

    /**
     * Flap, centre strap, buckles, shoulder straps and the handle. Every box stays
     * inside or directly on the body silhouette, so the accent colour never shows
     * as a glowing rim around the backpack.
     */
    public static BackpackModel accents() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeListBuilder accents = CubeListBuilder.create().texOffs(0, 32)
                // Flap: a thin cap on the top plus a strip draping down the front.
                // Both stay inside the body's width, so no accent rim is visible from the side.
                .addBox(-3.45F, 1.33F, 2.2F, 6.9F, 0.14F, 2.86F)
                .addBox(-3.45F, 1.33F, 4.98F, 6.9F, 1.97F, 0.08F)
                // Centre strap and buckle.
                .addBox(-0.75F, 2.9F, 5.05F, 1.5F, 1.7F, 0.15F)
                .addBox(-0.95F, 3.4F, 5.2F, 1.9F, 1.0F, 0.2F)
                // Shoulder straps against the player's back.
                .addBox(-3.1F, 0.4F, 1.8F, 0.9F, 3.6F, 0.3F)
                .addBox(2.2F, 0.4F, 1.8F, 0.9F, 3.6F, 0.3F)
                // Strap adjusters.
                .addBox(-3.05F, 2.3F, 2.05F, 0.8F, 0.5F, 0.15F)
                .addBox(2.25F, 2.3F, 2.05F, 0.8F, 0.5F, 0.15F)
                // Handle and its posts.
                .addBox(-1.3F, 0.55F, 3.2F, 2.6F, 0.55F, 1.0F)
                .addBox(-1.45F, 0.95F, 3.1F, 0.4F, 0.5F, 1.2F)
                .addBox(1.05F, 0.95F, 3.1F, 0.4F, 0.5F, 1.2F)
                // Rivets on the flap corners.
                .addBox(-3.3F, 2.9F, 5.06F, 0.4F, 0.4F, 0.06F)
                .addBox(2.9F, 2.9F, 5.06F, 0.4F, 0.4F, 0.06F)
                .addBox(-3.3F, 1.6F, 5.06F, 0.4F, 0.4F, 0.06F)
                .addBox(2.9F, 1.6F, 5.06F, 0.4F, 0.4F, 0.06F);

        root.addOrReplaceChild("accents", accents, PartPose.ZERO);
        return new BackpackModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
    }

    /** Metal corner caps and a top rail, shown from tier 4. */
    public static BackpackModel frame() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeListBuilder frame = CubeListBuilder.create().texOffs(0, 48)
                .addBox(-3.75F, 9.1F, 2.2F, 0.6F, 0.6F, 0.7F)
                .addBox(3.15F, 9.1F, 2.2F, 0.6F, 0.6F, 0.7F)
                .addBox(-3.75F, 9.1F, 4.3F, 0.6F, 0.6F, 0.7F)
                .addBox(3.15F, 9.1F, 4.3F, 0.6F, 0.6F, 0.7F)
                .addBox(-3.4F, 0.95F, 4.85F, 6.8F, 0.35F, 0.17F);

        root.addOrReplaceChild("frame", frame, PartPose.ZERO);
        return new BackpackModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
    }

    /** A rolled bedroll strapped on top, shown from tier 10. */
    public static BackpackModel bedroll() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeListBuilder roll = CubeListBuilder.create().texOffs(32, 48)
                .addBox(-3.4F, 0.15F, 2.2F, 6.8F, 0.6F, 2.7F)
                .addBox(-3.65F, 0.2F, 2.3F, 0.3F, 0.5F, 2.5F)
                .addBox(3.35F, 0.2F, 2.3F, 0.3F, 0.5F, 2.5F)
                .addBox(-2.2F, 0.1F, 2.15F, 0.4F, 0.7F, 2.8F)
                .addBox(1.8F, 0.1F, 2.15F, 0.4F, 0.7F, 2.8F);

        root.addOrReplaceChild("bedroll", roll, PartPose.ZERO);
        return new BackpackModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
    }

    @Override
    public void setupAnim(Float state) {
        // Static geometry: nothing to animate.
    }

    /** Renders every pass at the current pose, with the tier colours as tint. */
    public static void render(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                              int light, net.minecraft.resources.Identifier texture, BackpackState.Wear wear) {
        var renderType = net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture);
        collector.submitModel(BASE, FLAP_TINT, pose, renderType, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                0xFF000000 | wear.bodyColor());
        collector.submitModel(ACCENTS, FLAP_TINT, pose, renderType, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                0xFF000000 | wear.accentColor());
        if (wear.tier() >= 4) {
            collector.submitModel(FRAME, FLAP_TINT, pose, renderType, light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    0xFF000000 | darken(wear.accentColor(), 0.72F));
        }
        if (wear.tier() >= 10) {
            collector.submitModel(BEDROLL, FLAP_TINT, pose, renderType, light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    0xFF000000 | darken(wear.bodyColor(), 0.8F));
        }
    }

    private static int darken(int rgb, float factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static final BackpackModel BASE = base();
    private static final BackpackModel ACCENTS = accents();
    private static final BackpackModel FRAME = frame();
    private static final BackpackModel BEDROLL = bedroll();
}
