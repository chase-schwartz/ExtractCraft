package com.chaseschwartz.extractcraft.durability;

import java.util.Locale;
import java.util.Optional;

import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfile;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfileRegistry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class DurabilityService {
    public static final String CUSTOM_DATA_KEY = "extractcraft_durability";
    private static final String TYPE_KEY = "type";
    private static final String PRISTINE_MAX_KEY = "pristineMaxDurability";
    private static final String CURRENT_MAX_KEY = "currentMaxDurability";
    private static final String CURRENT_KEY = "currentDurability";
    private static final String REPAIR_COUNT_KEY = "repairCount";

    private DurabilityService() {
    }

    public static Optional<DurabilityProfile> profileFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return ItemCarryProfileRegistry.get(stack)
                .filter(profile -> profile.durabilityEnabled())
                .flatMap(profile -> profile.maxDurability().map(max -> fromProfile(stack, profile, max)));
    }

    public static Optional<DurabilityData> getOrInitialize(ItemStack stack) {
        Optional<DurabilityProfile> profile = profileFor(stack);
        if (profile.isEmpty()) {
            clear(stack);
            return Optional.empty();
        }

        DurabilityData data = readRaw(stack).orElseGet(() -> pristineData(profile.get()));
        data = reconcile(data, profile.get());
        write(stack, data);
        return Optional.of(data);
    }

    public static Optional<DurabilityData> read(ItemStack stack) {
        if (profileFor(stack).isEmpty()) {
            clear(stack);
            return Optional.empty();
        }
        return readRaw(stack).map(data -> reconcile(data, profileFor(stack).orElseThrow()));
    }

    public static Optional<DurabilityData> damage(ItemStack stack, int amount) {
        if (amount <= 0) {
            return getOrInitialize(stack);
        }
        return getOrInitialize(stack).map(data -> {
            DurabilityData damaged = new DurabilityData(
                    data.type(),
                    data.pristineMaxDurability(),
                    data.currentMaxDurability(),
                    Math.max(0, data.currentDurability() - amount),
                    data.repairCount());
            write(stack, damaged);
            return damaged;
        });
    }

    public static Optional<DurabilityData> set(ItemStack stack, int currentDurability, Optional<Integer> currentMaxDurability) {
        return getOrInitialize(stack).map(data -> {
            int currentMax = currentMaxDurability.orElse(data.currentMaxDurability());
            DurabilityData updated = new DurabilityData(
                    data.type(),
                    data.pristineMaxDurability(),
                    currentMax,
                    currentDurability,
                    data.repairCount());
            write(stack, updated);
            return updated;
        });
    }

    public static Optional<DurabilityData> reset(ItemStack stack) {
        return profileFor(stack).map(profile -> {
            DurabilityData pristine = pristineData(profile);
            write(stack, pristine);
            return pristine;
        });
    }

    public static void clear(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(CUSTOM_DATA_KEY)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.remove(CUSTOM_DATA_KEY));
    }

    public static String summary(DurabilityData data) {
        return data.currentDurability() + "/" + data.currentMaxDurability()
                + " (pristine max " + data.pristineMaxDurability()
                + ", repairs " + data.repairCount()
                + ", type " + label(data.type()) + ")";
    }

    private static DurabilityProfile fromProfile(ItemStack stack, ItemCarryProfile profile, int maxDurability) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String type = profile.durabilityType().orElseGet(() -> inferType(profile));
        return new DurabilityProfile(itemId, type, profile.tier().orElse(0), maxDurability);
    }

    private static String inferType(ItemCarryProfile profile) {
        if (profile.repairTargetCategory().isPresent()) {
            return "repair_kit";
        }
        if (profile.repairCategory().isPresent()) {
            return profile.repairCategory().orElseThrow().toLowerCase(Locale.ROOT).replace(' ', '_');
        }
        if (profile.equipmentSlot().isPresent()) {
            return profile.equipmentSlot().orElseThrow().toLowerCase(Locale.ROOT).replace("equipped_", "");
        }
        return "equipment";
    }

    private static Optional<DurabilityData> readRaw(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        CompoundTag root = customData.copyTag();
        if (!root.contains(CUSTOM_DATA_KEY, 10)) {
            return Optional.empty();
        }
        CompoundTag tag = root.getCompound(CUSTOM_DATA_KEY);
        String type = tag.contains(TYPE_KEY, 8) ? tag.getString(TYPE_KEY) : "equipment";
        int pristineMax = tag.contains(PRISTINE_MAX_KEY, 3) ? tag.getInt(PRISTINE_MAX_KEY) : 1;
        int currentMax = tag.contains(CURRENT_MAX_KEY, 3) ? tag.getInt(CURRENT_MAX_KEY) : pristineMax;
        int current = tag.contains(CURRENT_KEY, 3) ? tag.getInt(CURRENT_KEY) : currentMax;
        int repairCount = tag.contains(REPAIR_COUNT_KEY, 3) ? tag.getInt(REPAIR_COUNT_KEY) : 0;
        return Optional.of(new DurabilityData(type, pristineMax, currentMax, current, repairCount));
    }

    private static DurabilityData reconcile(DurabilityData data, DurabilityProfile profile) {
        if ("repair_kit".equals(profile.type())
                && profile.pristineMaxDurability() > data.pristineMaxDurability()
                && data.repairCount() == 0
                && data.currentMaxDurability() == data.pristineMaxDurability()
                && data.currentDurability() == data.currentMaxDurability()) {
            return pristineData(profile);
        }
        int pristineMax = Math.max(data.pristineMaxDurability(), profile.pristineMaxDurability());
        int currentMax = data.currentMaxDurability() <= 0 ? profile.pristineMaxDurability() : data.currentMaxDurability();
        int current = data.currentDurability();
        if (current <= 0 && data.currentMaxDurability() <= 0) {
            current = currentMax;
        }
        return new DurabilityData(profile.type(), pristineMax, currentMax, current, data.repairCount());
    }

    private static DurabilityData pristineData(DurabilityProfile profile) {
        return new DurabilityData(profile.type(), profile.pristineMaxDurability(), profile.pristineMaxDurability(), profile.pristineMaxDurability(), 0);
    }

    static void write(ItemStack stack, DurabilityData data) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag tag = new CompoundTag();
            tag.putString(TYPE_KEY, data.type());
            tag.putInt(PRISTINE_MAX_KEY, data.pristineMaxDurability());
            tag.putInt(CURRENT_MAX_KEY, data.currentMaxDurability());
            tag.putInt(CURRENT_KEY, data.currentDurability());
            tag.putInt(REPAIR_COUNT_KEY, data.repairCount());
            root.put(CUSTOM_DATA_KEY, tag);
        });
    }

    private static String label(String raw) {
        return raw.replace('_', ' ');
    }
}
