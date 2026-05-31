package com.chaseschwartz.extractcraft.itemidentity;

import java.util.Optional;

import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ItemIdentityResolver {
    private static final ResourceLocation TACZ_GUN = ResourceLocation.fromNamespaceAndPath("tacz", "modern_kinetic_gun");
    private static final ResourceLocation TACZ_ATTACHMENT = ResourceLocation.fromNamespaceAndPath("tacz", "attachment");
    private static final ResourceLocation TACZ_AMMO = ResourceLocation.fromNamespaceAndPath("tacz", "ammo");
    private static final ResourceLocation TACZ_AMMO_BOX = ResourceLocation.fromNamespaceAndPath("tacz", "ammo_box");

    private ItemIdentityResolver() {
    }

    public static ItemIdentity resolve(ItemStack stack) {
        ResourceLocation baseItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Optional<ResourceLocation> variantId = resolveVariant(baseItemId, stack);
        String normalizedKey = variantId.map(variant -> baseItemId + "#" + variant).orElse(baseItemId.toString());
        return new ItemIdentity(baseItemId, variantId, normalizedKey, stack.getHoverName().getString(), categoryHint(baseItemId));
    }

    private static Optional<ResourceLocation> resolveVariant(ResourceLocation baseItemId, ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return Optional.empty();
        }

        CompoundTag tag = customData.copyTag();
        if (baseItemId.equals(TACZ_GUN)) {
            return readResourceLocation(tag, "GunId");
        }
        if (baseItemId.equals(TACZ_ATTACHMENT)) {
            return readResourceLocation(tag, "AttachmentId");
        }
        if (baseItemId.equals(TACZ_AMMO)) {
            return readResourceLocation(tag, "AmmoId")
                    .or(() -> findResourceLocationByKey(tag, "AmmoId"))
                    .or(() -> findResourceLocationByKey(tag, "ammo"));
        }
        if (baseItemId.equals(TACZ_AMMO_BOX)) {
            return readResourceLocation(tag, "AmmoId")
                    .or(() -> readResourceLocation(tag, "AmmoBoxId"))
                    .or(() -> findResourceLocationByKey(tag, "AmmoId"))
                    .or(() -> findResourceLocationByKey(tag, "ammo"));
        }

        return Optional.empty();
    }

    private static Optional<ResourceLocation> readResourceLocation(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return Optional.empty();
        }

        String value = tag.getString(key);
        try {
            return Optional.of(ResourceLocation.parse(value));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static Optional<ResourceLocation> findResourceLocationByKey(Tag tag, String keyNeedle) {
        if (tag instanceof CompoundTag compoundTag) {
            for (String key : compoundTag.getAllKeys()) {
                Tag child = compoundTag.get(key);
                if (key.toLowerCase().contains(keyNeedle.toLowerCase()) && child instanceof StringTag) {
                    Optional<ResourceLocation> value = parseResourceLocation(child.getAsString());
                    if (value.isPresent()) {
                        return value;
                    }
                }
                if (child != null) {
                    Optional<ResourceLocation> nested = findResourceLocationByKey(child, keyNeedle);
                    if (nested.isPresent()) {
                        return nested;
                    }
                }
            }
        } else if (tag instanceof ListTag listTag) {
            for (int i = 0; i < listTag.size(); i++) {
                Optional<ResourceLocation> nested = findResourceLocationByKey(listTag.get(i), keyNeedle);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<ResourceLocation> parseResourceLocation(String value) {
        try {
            return Optional.of(ResourceLocation.parse(value));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static Optional<ItemCategory> categoryHint(ResourceLocation baseItemId) {
        if (baseItemId.equals(TACZ_GUN)) {
            return Optional.of(ItemCategory.GUNS);
        }
        if (baseItemId.equals(TACZ_ATTACHMENT)) {
            return Optional.of(ItemCategory.ATTACHMENTS);
        }
        if (baseItemId.equals(TACZ_AMMO) || baseItemId.equals(TACZ_AMMO_BOX)) {
            return Optional.of(ItemCategory.AMMO);
        }
        return Optional.empty();
    }
}
