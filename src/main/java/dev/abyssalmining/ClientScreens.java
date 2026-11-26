package dev.abyssalmining;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Abyssal.ID, value = Dist.CLIENT)
public final class ClientScreens {
    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(Abyssal.MENU.get(), Screen::new);
    }

    static void registerCompatibility() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            net.neoforged.bus.api.EventPriority.LOWEST, ClientScreens::placeSortButtons);
    }

    private static void placeSortButtons(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof Screen screen)) return;
        for (var listener : java.util.List.copyOf(event.getListenersList())) {
            if (!(listener instanceof net.minecraft.client.gui.components.AbstractWidget widget)
                || !listener.getClass().getName().equals("invtweaks.gui.InvTweaksButtonSort")) continue;
            if (widget.getY() - screen.getGuiTop() < 144) event.removeListener(listener);
            else {
                widget.setX(screen.getGuiLeft() + 151);
                widget.setY(screen.getGuiTop() + 133);
            }
        }
    }

    private static Component label(String key, Object... args) {
        return Component.translatable("gui." + Abyssal.ID + "." + key, args);
    }

    private static final class Screen extends AbstractContainerScreen<MachineMenu> {
        Screen(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            imageWidth = 246;
            imageHeight = 228;
            inventoryLabelX = 8;
            inventoryLabelY = 136;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
            super.render(graphics, mouseX, mouseY, tick);
            if (hoveredSlot != null && hoveredSlot.index < BigStackStorage.SLOTS && hoveredSlot.hasItem()) {
                ItemStack item = hoveredSlot.getItem();
                var tooltip = new java.util.ArrayList<>(getTooltipFromContainerItem(item));
                tooltip.add(label("quantity", menu.outputCount(hoveredSlot.index),
                    BigStackStorage.capacity(item, menu.data(MachineMenu.UPGRADES))));
                graphics.renderTooltip(font, tooltip, item.getTooltipImage(), mouseX, mouseY);
            } else {
                renderTooltip(graphics, mouseX, mouseY);
            }
            for (int i = 0; i < Machine.SOURCES; i++) {
                var slot = menu.getSlot(BigStackStorage.SLOTS + i);
                int x = leftPos + slot.x;
                int y = topPos + slot.y + 16;
                if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 5) continue;
                var lines = new java.util.ArrayList<Component>();
                int offset = MachineMenu.WORK_DATA + 3 * i;
                lines.add(label("status" + menu.data(offset + 2)));
                lines.add(label("progress", menu.data(offset)));
                lines.add(label("parallel", menu.data(offset + 1)));
                ItemStack item = slot.getItem();
                if (!item.isEmpty() && !menu.battlefield) {
                    if (item.is(Abyssal.MINER.get()) || item.has(net.minecraft.core.component.DataComponents.UNBREAKABLE))
                        lines.add(label("unbreakable"));
                    else if (item.isDamageableItem()) lines.add(label("durability", item.getMaxDamage() - item.getDamageValue(), item.getMaxDamage()));
                }
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }

        @Override protected void renderBg(GuiGraphics graphics, float tick, int mouseX, int mouseY) {
            int x = leftPos, y = topPos;
            graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xff261b32);
            graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xff785d80);
            graphics.fillGradient(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xff32233e, 0xff211a2e);
            graphics.fill(x + 5, y + 24, x + 171, y + 137, 0xff1b1728);
            graphics.fill(x + 5, y + 144, x + 171, y + 222, 0xff261d32);
            graphics.fill(x + 175, y + 25, x + 239, y + 223, 0xff291f33);
            graphics.fill(x + 6, y + 17, x + 240, y + 18, menu.battlefield ? 0xff9b658b : 0xff5b8a97);
            for (var slot : menu.slots) {
                graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xff715783);
                graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xff100d19);
            }
            for (int i = 0; i < Machine.SOURCES; i++) {
                int offset = MachineMenu.WORK_DATA + 3 * i;
                var source = menu.getSlot(BigStackStorage.SLOTS + i);
                int start = x + source.x;
                int bar = y + source.y + 17;
                int state = menu.data(offset + 2);
                int color = state == 3 ? 0xffe6a354 : state == 2 ? 0xffad778f : 0xff4ddcd4;
                graphics.fill(start, bar, start + 16, bar + 2, 0xff493953);
                graphics.fill(start, bar, start + menu.data(offset) * 16 / 100, bar + 2, color);
            }
        }

        @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
            int text = 0xffead9f8;
            int right = MachineMenu.SIDE_X;
            graphics.drawString(font, title, 8, 7, text, false);
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, text, false);
            graphics.drawString(font, label(menu.battlefield ? "sources" : "miners"), right, 19, text, false);
            int occupied = 0;
            for (int i = 0; i < BigStackStorage.SLOTS; i++) if (menu.outputCount(i) > 0) occupied++;
            graphics.drawString(font, label("output_short", occupied), 8, 19, text, false);
            var capacity = Component.literal(Integer.toString(BigStackStorage.capacity(64, menu.data(MachineMenu.UPGRADES))));
            graphics.drawString(font, capacity, 168 - font.width(capacity), 19, 0xff91c8d3, false);
            graphics.drawString(font, label("stack"), right, 110, text, false);
            graphics.drawString(font, label("parallel_title"), right, 138, text, false);
            boolean exportOnline = menu.data(MachineMenu.EXPORT_STATE) == 1;
            if (menu.battlefield) {
                graphics.drawString(font, label("maintenance"), right + 1, 195, text, false);
                graphics.drawString(font, label("me"), right + 39, 195, exportOnline ? 0xff4ddcd4 : text, false);
                graphics.drawString(font, label("weapon"), right + 4, 86, text, false);
                graphics.drawString(font, label("fuel"), right + 34, 86, text, false);
                graphics.drawString(font, label("loot", 1 + menu.data(MachineMenu.UPGRADES + 2)), right, 166, text, false);
            } else {
                graphics.drawString(font, label(exportOnline ? "me_online" : "me"), right, 205, text, false);
            }
        }

        @Override protected void renderSlotContents(GuiGraphics graphics, ItemStack item, net.minecraft.world.inventory.Slot slot, String countText) {
            if (slot.index >= BigStackStorage.SLOTS) {
                super.renderSlotContents(graphics, item, slot, countText);
                return;
            }
            super.renderSlotContents(graphics, item, slot, "");
            int count = menu.outputCount(slot.index);
            if (count <= 1) return;
            String value = Integer.toString(count);
            int width = font.width(value);
            float scale = Math.min(1, 15f / width);
            graphics.pose().pushPose();
            graphics.pose().translate(slot.x + 16 - width * scale, slot.y + 16 - font.lineHeight * scale, 200);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, value, 0, 0, 0xfff4e5ff, true);
            graphics.pose().popPose();
        }
    }
}
