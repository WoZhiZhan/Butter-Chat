package com.wzz.butterchat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Unique
    private long animationStartTime = 0;

    @Unique
    private static final long ANIMATION_DURATION = 300;

    @Unique
    private boolean isClosing = false;

    @Unique
    private long closeStartTime = 0;

    @Unique
    private boolean shouldActuallyClose = false;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        animationStartTime = System.currentTimeMillis();
        isClosing = false;
        shouldActuallyClose = false;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderStart(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        long currentTime = System.currentTimeMillis();
        float progress;

        if (isClosing) {
            long elapsed = currentTime - closeStartTime;
            progress = 1.0f - Mth.clamp((float) elapsed / ANIMATION_DURATION, 0.0f, 1.0f);

            if (elapsed >= ANIMATION_DURATION && !shouldActuallyClose) {
                shouldActuallyClose = true;
                if (this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
                return;
            }
        } else {
            long elapsed = currentTime - animationStartTime;
            progress = Mth.clamp((float) elapsed / ANIMATION_DURATION, 0.0f, 1.0f);
        }

        float easedProgress = isClosing ? easeInBack(progress) : easeOutBack(progress);

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();

        float scale = 0.9f + (easedProgress * 0.1f);
        float yOffset = (1.0f - easedProgress) * 30.0f;

        float centerX = this.width / 2.0f;
        float bottomY = this.height;

        poseStack.translate(centerX, bottomY, 0);
        poseStack.scale(scale, scale, 1.0f);
        poseStack.translate(-centerX, -bottomY + yOffset, 0);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, easedProgress);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        var poseStack = guiGraphics.pose();
        poseStack.popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void onRemoved(CallbackInfo ci) {
        if (!isClosing && !shouldActuallyClose) {
            isClosing = true;
            closeStartTime = System.currentTimeMillis();
            ci.cancel();
        }
    }

    @Unique
    private float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return (float) (1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2));
    }

    @Unique
    private float easeInBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return (float) (c3 * Math.pow(x, 3) - c1 * Math.pow(x, 2));
    }
}