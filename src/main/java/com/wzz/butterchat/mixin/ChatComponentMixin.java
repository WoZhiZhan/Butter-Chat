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
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private int chatScrollbarPos;

    @Shadow
    private boolean newMessageSinceScroll;

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract int getHeight();

    @Shadow
    public abstract double getScale();

    @Shadow
    public abstract int getLinesPerPage();

    @Shadow protected abstract boolean isChatHidden();

    @Shadow protected abstract boolean isChatFocused();

    @Shadow protected abstract int getMessageEndIndexAt(double p_249245_, double p_252282_);

    @Shadow protected abstract double screenToChatX(double p_240580_);

    @Shadow protected abstract double screenToChatY(double p_240548_);

    @Shadow protected abstract int getLineHeight();

    @Shadow protected abstract int getTagIconLeft(GuiMessage.Line p_240622_);

    @Shadow protected abstract void drawTagIcon(GuiGraphics p_283206_, int p_281677_, int p_281878_, GuiMessageTag.Icon p_282783_);

    @Unique
    private final Map<GuiMessage.Line, Long> lineAnimationTimes = new WeakHashMap<>();

    @Unique
    private static final long MESSAGE_SLIDE_IN_DURATION = 300;

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

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void customRender(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.isChatHidden()) {
            ci.cancel();
            return;
        }

        int linesPerPage = this.getLinesPerPage();
        int totalLines = this.trimmedMessages.size();

        if (totalLines <= 0) {
            ci.cancel();
            return;
        }

        boolean isChatOpen = this.isChatFocused();
        float scale = (float) this.getScale();
        int width = Mth.ceil((float) this.getWidth() / scale);
        int screenHeight = guiGraphics.guiHeight();

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.pose().translate(4.0F, 0.0F, 0.0F);

        int yBase = Mth.floor((float) (screenHeight - 40) / scale);
        int messageLineIndex = this.getMessageEndIndexAt(this.screenToChatX((double) mouseX), this.screenToChatY((double) mouseY));

        double chatOpacity = this.minecraft.options.chatOpacity().get() * 0.9 + 0.1;
        double bgOpacity = this.minecraft.options.textBackgroundOpacity().get();
        double lineSpacing = this.minecraft.options.chatLineSpacing().get();

        int lineHeight = this.getLineHeight();
        int lineOffset = (int) Math.round(-8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing);

        int renderedLines = 0;

        for (int i = 0; i + this.chatScrollbarPos < this.trimmedMessages.size() && i < linesPerPage; i++) {
            int lineIndex = i + this.chatScrollbarPos;
            GuiMessage.Line line = this.trimmedMessages.get(lineIndex);

            if (line == null) continue;

            int messageAge = tickCount - line.addedTime();

            // 检查消息是否应该显示
            if (messageAge >= 200 && !isChatOpen) {
                continue;
            }

            // 计算透明度
            double timeFactor = isChatOpen ? 1.0 : getTimeFactor(messageAge);
            int textAlpha = (int) (255.0 * timeFactor * chatOpacity);
            int bgAlpha = (int) (255.0 * timeFactor * bgOpacity);

            renderedLines++;

            if (textAlpha <= 3) continue;

            int yPos = yBase - i * lineHeight;
            int textY = yPos + lineOffset;

            // 计算滑动动画偏移
            float xOffset = 0.0f;
            float fadeMultiplier = 1.0f;

            Long animStartTime = lineAnimationTimes.get(line);

            // 滑入动画
            if (animStartTime != null) {
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - animStartTime;
                if (elapsed < MESSAGE_SLIDE_IN_DURATION) {
                    float progress = (float) elapsed / MESSAGE_SLIDE_IN_DURATION;
                    float easedProgress = easeOutCubic(progress);
                    xOffset = -(1.0f - easedProgress) * 100.0f;
                    fadeMultiplier = easedProgress;
                } else {
                    lineAnimationTimes.remove(line);
                }
            }

            // 滑出动画
            if (!isChatOpen && messageAge > 160 && messageAge <= 200) {
                float fadeProgress = (float) (messageAge - 160) / 40.0f;
                float easedFade = easeInCubic(fadeProgress);
                xOffset = -easedFade * 120.0f;
                fadeMultiplier = 1.0f - fadeProgress;
            }

            // 应用淡入淡出到透明度
            textAlpha = (int) (textAlpha * fadeMultiplier);
            bgAlpha = (int) (bgAlpha * fadeMultiplier);

            if (textAlpha <= 3) continue;

            // 绘制背景
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(xOffset, 0.0F, 0.0F);
            guiGraphics.pose().translate(0.0F, 0.0F, 50.0F);

            guiGraphics.fill(-4, yPos - lineHeight, width + 4 + 4, yPos, bgAlpha << 24);

            // 绘制消息标签
            GuiMessageTag tag = line.tag();
            if (tag != null) {
                int tagColor = tag.indicatorColor() | (textAlpha << 24);
                guiGraphics.fill(-4, yPos - lineHeight, -2, yPos, tagColor);

                if (lineIndex == messageLineIndex && tag.icon() != null) {
                    int iconX = this.getTagIconLeft(line);
                    Objects.requireNonNull(this.minecraft.font);
                    int iconY = textY + 9;
                    this.drawTagIcon(guiGraphics, iconX, iconY, tag.icon());
                }
            }

            // 绘制文本
            guiGraphics.pose().translate(0.0F, 0.0F, 50.0F);
            int color = 16777215 + (textAlpha << 24);
            guiGraphics.drawString(this.minecraft.font, line.content(), 0, textY, color);

            guiGraphics.pose().popPose();
        }

        // 绘制队列提示
        long queueSize = this.minecraft.getChatListener().queueSize();
        if (queueSize > 0L) {
            int queueAlpha = (int) (128.0 * chatOpacity);
            int queueBgAlpha = (int) (255.0 * bgOpacity);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, (float) yBase, 50.0F);
            guiGraphics.fill(-2, 0, width + 4, 9, queueBgAlpha << 24);
            guiGraphics.pose().translate(0.0F, 0.0F, 50.0F);
            guiGraphics.drawString(this.minecraft.font, Component.translatable("chat.queue", queueSize), 0, 1, 16777215 + (queueAlpha << 24));
            guiGraphics.pose().popPose();
        }

        // 绘制滚动条
        if (isChatOpen) {
            int lineHeightTotal = totalLines * lineHeight;
            int visibleHeight = renderedLines * lineHeight;
            int scrollbarPos = this.chatScrollbarPos * visibleHeight / totalLines - yBase;
            int scrollbarSize = visibleHeight * visibleHeight / lineHeightTotal;

            if (lineHeightTotal != visibleHeight) {
                int scrollbarAlpha = scrollbarPos > 0 ? 170 : 96;
                int scrollbarColor = this.newMessageSinceScroll ? 13382451 : 3355562;
                int scrollbarX = width + 4;

                guiGraphics.fill(scrollbarX, -scrollbarPos, scrollbarX + 2, -scrollbarPos - scrollbarSize, scrollbarColor + (scrollbarAlpha << 24));
                guiGraphics.fill(scrollbarX + 2, -scrollbarPos, scrollbarX + 1, -scrollbarPos - scrollbarSize, 13421772 + (scrollbarAlpha << 24));
            }
        }

        guiGraphics.pose().popPose();
        ci.cancel();
    }

    @Unique
    private static double getTimeFactor(int messageAge) {
        double factor = (double) messageAge / 200.0;
        factor = 1.0 - factor;
        factor *= 10.0;
        factor = Mth.clamp(factor, 0.0, 1.0);
        factor *= factor;
        return factor;
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