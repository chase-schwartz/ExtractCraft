package com.chaseschwartz.extractcraft.itemidentity;

import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ItemStackVariantFactory {
    private static final ResourceLocation TACZ_GUN = ResourceLocation.fromNamespaceAndPath("tacz", "modern_kinetic_gun");
    private static final ResourceLocation TACZ_ATTACHMENT = ResourceLocation.fromNamespaceAndPath("tacz", "attachment");
    private static final ResourceLocation TACZ_AMMO = ResourceLocation.fromNamespaceAndPath("tacz", "ammo");
    private static final ResourceLocation TACZ_AMMO_BOX = ResourceLocation.fromNamespaceAndPath("tacz", "ammo_box");

    private ItemStackVariantFactory() {
    }

    public static Optional<ItemStack> create(String lookupKey, int count) {
        ParsedKey key = parse(lookupKey);
        if (!BuiltInRegistries.ITEM.containsKey(key.baseItemId())) {
            return Optional.empty();
        }

        Item item = BuiltInRegistries.ITEM.get(key.baseItemId());
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        key.variantId().ifPresent(variantId -> applyVariant(stack, key.baseItemId(), variantId));
        return Optional.of(stack);
    }

    public static boolean isUnsafeBareVariantBase(ResourceLocation itemId) {
        return itemId.equals(TACZ_GUN)
                || itemId.equals(TACZ_ATTACHMENT)
                || itemId.equals(TACZ_AMMO)
                || itemId.equals(TACZ_AMMO_BOX);
    }

    private static void applyVariant(ItemStack stack, ResourceLocation baseItemId, ResourceLocation variantId) {
        CompoundTag tag = new CompoundTag();
        if (baseItemId.equals(TACZ_GUN)) {
            tag.putString("GunId", variantId.toString());
            tag.putInt("GunCurrentAmmoCount", 0);
            tag.putString("GunFireMode", "SEMI");
            tag.putBoolean("HasBulletInBarrel", false);
        } else if (baseItemId.equals(TACZ_ATTACHMENT)) {
            tag.putString("AttachmentId", variantId.toString());
        } else if (baseItemId.equals(TACZ_AMMO) || baseItemId.equals(TACZ_AMMO_BOX)) {
            tag.putString("AmmoId", variantId.toString());
        }

        if (!tag.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static ParsedKey parse(String lookupKey) {
        int separator = lookupKey.indexOf('#');
        if (separator < 0) {
            return new ParsedKey(ResourceLocation.parse(lookupKey), Optional.empty());
        }

        return new ParsedKey(ResourceLocation.parse(lookupKey.substring(0, separator)), Optional.of(ResourceLocation.parse(lookupKey.substring(separator + 1))));
    }

    private record ParsedKey(ResourceLocation baseItemId, Optional<ResourceLocation> variantId) {
    }
}
