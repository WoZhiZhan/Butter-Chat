package com.wzz.butterchat.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    private int chatScrollbarPos;

    @Unique
    private final Map<GuiMessage.Line, Long> lineAnimationTimes = new ConcurrentHashMap<>();

    @Unique
    private static final long MESSAGE_SLIDE_IN_DURATION = 300;

    @Unique
    private int currentTickCount;

    @Unique
    private int previousTrimmedSize = 0;

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
            at = @At("HEAD"))
    private void beforeMessageAdded(Component message, MessageSignature signature, int ticks, GuiMessageTag tag, boolean refresh, CallbackInfo ci) {
        if (!refresh) {
            previousTrimmedSize = trimmedMessages.size();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
            at = @At("TAIL"))
    private void afterMessageAdded(Component message, MessageSignature signature, int ticks, GuiMessageTag tag, boolean refresh, CallbackInfo ci) {
        if (!refresh) {
            long currentTime = System.currentTimeMillis();
            int currentSize = trimmedMessages.size();
            int newLinesCount = currentSize - previousTrimmedSize;
            for (int i = 0; i < newLinesCount && i < trimmedMessages.size(); i++) {
                GuiMessage.Line line = trimmedMessages.get(i);
                lineAnimationTimes.put(line, currentTime);
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderStart(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        this.currentTickCount = tickCount;
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int redirectDrawString(GuiGraphics guiGraphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        GuiMessage.Line currentLine = null;
        for (GuiMessage.Line line : trimmedMessages) {
            if (line.content() == text) {
                currentLine = line;
                break;
            }
        }

        if (currentLine != null) {
            Long animStartTime = lineAnimationTimes.get(currentLine);

            if (animStartTime != null) {
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - animStartTime;
                if (elapsed < MESSAGE_SLIDE_IN_DURATION) {
                    float progress = (float) elapsed / MESSAGE_SLIDE_IN_DURATION;
                    float easedProgress = easeOutCubic(progress);

                    float xOffset = -(1.0f - easedProgress) * 100.0f;

                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(xOffset, 0, 0);
                    int alpha = (color >> 24) & 0xFF;
                    int rgb = color & 0xFFFFFF;
                    int newAlpha = (int) (alpha * easedProgress);
                    color = (newAlpha << 24) | rgb;

                    int result = guiGraphics.drawString(font, text, x, y, color);
                    guiGraphics.pose().popPose();

                    return result;
                } else {
                    lineAnimationTimes.remove(currentLine);
                }
            }

            boolean isChatOpen = Minecraft.getInstance().screen instanceof ChatScreen;
            if (!isChatOpen) {
                int messageAge = currentTickCount - currentLine.addedTime();
                if (messageAge > 160 && messageAge <= 200) {
                    float fadeProgress = (float) (messageAge - 160) / 40.0f;
                    float easedFade = easeInCubic(fadeProgress);

                    float xOffset = -easedFade * 120.0f;

                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(xOffset, 0, 0);

                    int alpha = (color >> 24) & 0xFF;
                    int rgb = color & 0xFFFFFF;
                    int newAlpha = (int) (alpha * (1.0f - fadeProgress));
                    color = (newAlpha << 24) | rgb;

                    int result = guiGraphics.drawString(font, text, x, y, color);
                    guiGraphics.pose().popPose();

                    return result;
                }
            }
        }

        return guiGraphics.drawString(font, text, x, y, color);
    }

    @Unique
    private float easeOutCubic(float x) {
        return 1 - (float) Math.pow(1 - x, 3);
    }

    @Unique
    private float easeInCubic(float x) {
        return (float) Math.pow(x, 3);
    }
}