package dev.elytraswap;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public class ElytraSwapClient implements ClientModInitializer {
    // ---- настройки ----
    private static final boolean AUTO_SWAP = true;
    private static final double AUTO_FALL_DISTANCE = 2.0;
    private static final int COOLDOWN_TICKS = 10;
    private static final int GROUND_GRACE_TICKS = 20;
    private static final int DOUBLE_PRESS_TICKS = 10;
    // -------------------
    
    private static final int CHEST_SCREEN_SLOT = 6;
    
    private KeyMapping swapKey;
    private int cooldown = 0;
    private boolean autoSwapped = false;
    private int tickCounter = 0;
    private int lastPressTick = -1000;
    private int lastJumpTick = -1000;
    private int graceUntilTick = -1000;
    private boolean jumpWasDown = false;
    
    @Override
    public void onInitializeClient() {
        swapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.elytraswap.swap",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            KeyMapping.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    
    private void tick(Minecraft client) {
        LocalPlayer p = client.player;
        if (p == null || client.gameMode == null) return;
        tickCounter++;
        if (cooldown > 0) cooldown--;
        
        // двойное нажатие клавиши R
        int presses = 0;
        while (swapKey.consumeClick()) presses++;
        
        boolean keyDouble = false;
        if (presses > 0) {
            if (presses >= 2 || tickCounter - lastPressTick <= DOUBLE_PRESS_TICKS) {
                keyDouble = true;
                lastPressTick = -1000;
            } else {
                lastPressTick = tickCounter;
            }
        }
        
        // двойное нажатие пробела
        boolean jumpDown = client.options.keyJump.isDown();
        boolean jumpDouble = false;
        if (jumpDown && !jumpWasDown) {
            if (tickCounter - lastJumpTick <= DOUBLE_PRESS_TICKS) {
                jumpDouble = true;
                lastJumpTick = -1000;
            } else {
                lastJumpTick = tickCounter;
            }
        }
        jumpWasDown = jumpDown;
        
        if (cooldown > 0) return;
        
        if (keyDouble || jumpDouble) {
            boolean wearingElytra = p.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
            if (wearingElytra) {
                // пробелом в воздухе с элитрой не трогаем
                boolean safe = keyDouble || (!p.isFallFlying());
                if (safe) {
                    autoSwapped = false;
                    msg(p, equip(client, p, false) ? "Chestplate" : "No chestplate in inventory");
                }
            } else {
                autoSwapped = false;
                boolean ok = equip(client, p, true);
                if (ok) graceUntilTick = tickCounter + GROUND_GRACE_TICKS;
                msg(p, ok ? "Elytra" : "No elytra in inventory");
            }
            return;
        }
        
        if (!AUTO_SWAP) return;
        
        boolean wearingElytra = p.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        
        if (!wearingElytra && !p.onGround() && !p.isInWater() && !p.getAbilities().flying && p.fallDistance >= AUTO_FALL_DISTANCE) {
            if (equip(client, p, true)) {
                autoSwapped = true;
                msg(p, "Auto: Elytra");
            }
        } else if (wearingElytra && !p.isFallFlying() && tickCounter > graceUntilTick && (p.onGround() || (autoSwapped && p.isInWater()))) {
            // стоишь на земле -> всегда нагрудник
            if (equip(client, p, false)) {
                autoSwapped = false;
                msg(p, "Auto: Chestplate");
            }
        }
    }
    
    private boolean equip(Minecraft c, LocalPlayer p, boolean elytra) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (elytra ? isUsableElytra(s) : isChestplate(s)) {
                swapWithChest(c, p, i);
                cooldown = COOLDOWN_TICKS;
                return true;
            }
        }
        return false;
    }
    
    private static boolean isUsableElytra(ItemStack s) {
        return s.is(Items.ELYTRA) && s.getDamageValue() < s.getMaxDamage();
    }
    
    private static boolean isChestplate(ItemStack s) {
        if (s.isEmpty() || s.is(Items.ELYTRA)) return false;
        var eq = s.get(DataComponents.EQUIPPABLE);
        return eq != null && eq.slot() == EquipmentSlot.CHEST;
    }
    
    private void swapWithChest(Minecraft c, LocalPlayer p, int invIndex) {
        int slot = invIndex < 9 ? invIndex + 36 : invIndex;
        int id = p.inventoryMenu.containerId;
        var gm = c.gameMode;
        gm.handleContainerInput(id, slot, 0, ContainerInput.PICKUP, p);
        gm.handleContainerInput(id, CHEST_SCREEN_SLOT, 0, ContainerInput.PICKUP, p);
        if (!p.inventoryMenu.getCarried().isEmpty()) {
            gm.handleContainerInput(id, slot, 0, ContainerInput.PICKUP, p);
        }
    }
    
    private static void msg(LocalPlayer p, String s) {
        p.sendSystemMessage(Component.literal(s));
    }
}
