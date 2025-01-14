package net.rupyber_studios.improved_end.entity.client;

import net.minecraft.util.Identifier;
import net.rupyber_studios.improved_end.ImprovedEnd;
import net.rupyber_studios.improved_end.entity.custom.BlastlingEntity;
import net.rupyber_studios.improved_end.entity.custom.MawlingEntity;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import software.bernie.geckolib3.model.provider.data.EntityModelData;

public class MawlingModel extends AnimatedGeoModel<MawlingEntity> {
    @Override
    public Identifier getModelResource(MawlingEntity object) {
        return new Identifier(ImprovedEnd.MOD_ID, "geo/mawling.geo.json");
    }

    @Override
    public Identifier getTextureResource(MawlingEntity object) {
        return new Identifier(ImprovedEnd.MOD_ID, "textures/entity/enderman/mawling.png");
    }

    @Override
    public Identifier getAnimationResource(MawlingEntity animatable) {
        return new Identifier(ImprovedEnd.MOD_ID, "animations/mawling.animations.json");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void setLivingAnimations(MawlingEntity entity, Integer uniqueID, AnimationEvent customPredicate) {
        super.setLivingAnimations(entity, uniqueID, customPredicate);
        IBone head = this.getAnimationProcessor().getBone("head");

        EntityModelData extraData = (EntityModelData) customPredicate.getExtraDataOfType(EntityModelData.class).get(0);
        if (head != null) {
            head.setRotationX(extraData.headPitch * ((float) Math.PI / 180F));
            head.setRotationY(extraData.netHeadYaw * ((float) Math.PI / 180F));
        }
    }
}