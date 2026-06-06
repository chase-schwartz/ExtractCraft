from __future__ import annotations

import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "run" / "Sprites"
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "extractcraft" / "textures" / "item"
MODELS = ROOT / "src" / "main" / "resources" / "assets" / "extractcraft" / "models" / "item"
LANG = ROOT / "src" / "main" / "resources" / "assets" / "extractcraft" / "lang" / "en_us.json"
PROFILES = ROOT / "src" / "main" / "resources" / "data" / "extractcraft" / "extractcraft" / "carry_profiles" / "extractcraft_equipment_profiles.json"
VALUES = ROOT / "src" / "main" / "resources" / "data" / "extractcraft" / "extractcraft" / "item_values" / "extractcraft_equipment_values.json"
DIRECT_ASSET_ITEMS = {
    "scrapline_helmet",
    "ranger_ballistic_helmet",
    "vector_rail_helmet",
    "apex_assault_helmet",
    "softshell_plate_carrier",
    "bulwark_plate_carrier",
    "warden_combat_armor",
    "juggernaut_assault_armor",
    "scout_chest_rig",
    "rangefinder_tactical_vest",
    "operator_load_bearing_vest",
    "specter_combat_rig",
    "sparrow_sling_pack",
    "fieldrunner_pack",
    "mule_tactical_pack",
    "atlas_raid_pack",
    "pioneer_lockbox",
    "blacksite_secure_case",
    "omega_safe_container",
    "combat_stim_syringe",
    "field_med_kit",
    "trauma_response_case",
    "helmet_rebuild_kit",
    "armor_rebuild_kit",
    "pack_rebuild_kit",
    "maintenance_key_fob",
    "security_badge_blank",
    "armory_maintenance_card",
    "blacksite_access_badge",
    "damaged_keypad_panel",
    "biometric_scanner",
    "compact_signal_jammer",
    "satellite_uplink_beacon",
    "mre_flavor_pack",
    "ration_coupon_stack",
    "signal_flare_tube",
    "water_purifier_cartridge",
    "brass_screw_tin",
    "duct_tape_stack",
    "low_grade_lubricant",
    "oily_cleaning_cloth",
    "worn_tool_roll",
    "sealed_cable_bundle",
    "field_radio_battery",
    "ceramic_fuse",
    "pocket_multimeter",
    "copper_wire_spool",
    "cracked_rangefinder_lens",
    "thermal_lens_module",
    "optic_calibration_tool",
    "laminated_zone_map",
    "field_notebook",
    "combat_tourniquet_pack",
    "sterile_gauze_brick",
    "antibiotic_strip",
    "plastic_med_vials",
    "lab_sample_cooler",
    "surgical_stapler_kit",
    "hemostatic_injector_pack",
    "ballistic_fiber_roll",
    "ceramic_plate_insert",
    "kevlar_stitch_kit",
    "smart_ballistic_plate",
    "reactive_armor_tile",
    "exo_fiber_harness",
    "precision_gyro_stabilizer",
    "drone_flight_controller",
    "tactical_tablet",
    "satellite_phone",
    "hardened_drive_caddy",
    "quantum_signal_chip",
    "classified_ssd_array",
    "secure_ops_laptop",
    "command_ai_processor",
    "spent_data_tape",
    "training_ammo_voucher",
    "folded_evacuate_notice",
    "redacted_patrol_orders",
    "sealed_evidence_pouch",
    "military_crypto_ledger",
    "restricted_medical_manifest",
    "commanders_encrypted_drive",
    "black_budget_payroll",
    "diplomatic_courier_pouch",
    "heart_of_the_dam",
    "blacksite_master_ledger",
    "gold_tactical_pack_frame",
    "classified_warhead_circuit",
    "omega_protocol_drive",
    "advanced_nvg_assembly",
    "adaptive_suppressor_core",
    "lab_grade_stimulant_case",
    "portable_blood_analyzer",
    "spark_plug_set",
    "hydraulic_valve",
    "cracked_phone_screen",
    "encrypted_usb_token",
    "secure_radio_module",
    "reinforced_cable_harness",
    "prototype_exo_core",
    "gold_wristwatch",
    "silver_locket",
    "diamond_earrings_case",
    "antique_compass",
    "camera_lens",
    "engraved_lighter",
    "perfume_bottle",
    "fountain_pen_case",
    "gold_chess_king",
    "gemstone_reliquary",
    "sealed_diplomatic_medal",
    "platinum_data_wafer",
    "ceremonial_dagger_hilt",
    "luxury_gold_bar",
    "royal_signet_ring",
    "antique_music_box",
    "pressure_gauge",
    "valve_wheel",
    "welding_nozzle",
    "bearing_set",
    "drill_chuck",
    "braided_hose_bundle",
    "fuse_box_module",
    "pump_impeller",
    "antique_pocket_watch",
    "pearl_bracelet",
    "designer_wallet",
    "rare_coin_case",
    "luxury_sunglasses",
    "jeweled_brooch",
    "silver_cigarette_case",
    "velvet_jewelry_pouch",
    "executive_keyring",
    "sealed_cash_envelope",
    "office_stamp_kit",
    "security_badge_stack",
    "locked_file_tube",
    "brass_nameplate",
    "shredded_document_bag",
    "encrypted_access_fob",
    "specimen_slide_case",
    "reagent_ampoule_rack",
    "microscope_lens",
    "biohazard_sample_tube",
    "centrifuge_rotor",
    "petri_dish_stack",
    "sealed_lab_notebook",
    "calibration_weight_set",
    "storm_lighter",
    "signal_mirror",
    "emergency_blanket_roll",
    "water_purification_tabs",
    "compact_camp_stove",
    "flare_case",
    "filter_straw",
    "firestarter_tin",
    "sealed_coffee_tin",
    "instant_noodle_cup",
    "vacuum_tea_brick",
    "canned_peaches",
    "chocolate_bar_pack",
    "cigarette_carton",
    "travel_mug",
    "sealed_spice_jar",
    "sealed_blacksite_relic",
    "glowing_core_fragment",
    "unknown_specimen_jar",
    "encrypted_prayer_tablet",
    "anomalous_compass",
    "redacted_photo_locket",
    "sealed_eye_capsule",
    "ancient_access_coin",
    "crystal_decanter",
    "antique_hand_mirror",
    "gold_cufflinks",
    "silver_compact_case",
    "pocket_cigar_case",
    "gemstone_tie_pin",
    "ivory_letter_opener",
    "velvet_ring_box",
    "vintage_camera",
    "silver_pocket_flask",
    "rare_stamp_album",
    "antique_porcelain_figurine",
    "gold_fountain_pen",
    "collector_card_slab",
    "silver_pocket_knife",
    "antique_music_cylinder",
    "pill_bottle_rx",
    "blister_painkillers",
    "first_aid_ointment",
    "glass_cough_syrup",
    "adhesive_bandage_box",
    "digital_thermometer",
    "inhaler_blue",
    "saline_eye_drops",
    "rusted_dog_tags",
    "nano_suture_cartridge",
}
INACTIVE_ITEMS = {
    "arsenal_elite_vest",
    "atlas_raid_pack_mk2",
    "quickclot_injector",
    "trauma_field_pack",
    "blackseal_med_case",
    "field_dressing_roll",
}


# id, display, sheet, cols, rows, row, col, category, weight, footprint w/h,
# tier, equipment slot, storage w/h, durability, repair category, max carry,
# heal, use ticks, fixes bleed, fixes bone, safe allowed, value
ITEMS = [
    ("scrapline_helmet", "Scrapline Helmet", "Armor.png", 5, 4, 1, 1, "armor", 2.2, 2, 2, 1, "helmet", None, None, 80, "Helmet", None, None, None, False, False, False, 95),
    ("ranger_ballistic_helmet", "Ranger Ballistic Helmet", "Armor.png", 5, 4, 1, 2, "armor", 2.8, 2, 2, 2, "helmet", None, None, 120, "Helmet", None, None, None, False, False, False, 150),
    ("vector_rail_helmet", "Vector Rail Helmet", "Armor.png", 5, 4, 1, 3, "armor", 3.3, 2, 2, 3, "helmet", None, None, 170, "Helmet", None, None, None, False, False, False, 230),
    ("apex_assault_helmet", "Apex Assault Helmet", "Armor.png", 5, 4, 1, 5, "armor", 4.2, 2, 2, 4, "helmet", None, None, 300, "Helmet", None, None, None, False, False, False, 480),
    ("softshell_plate_carrier", "Softshell Plate Carrier", "Armor.png", 5, 4, 2, 1, "armor", 5.0, 2, 3, 1, "armor", None, None, 140, "Armor", None, None, None, False, False, False, 180),
    ("bulwark_plate_carrier", "Bulwark Plate Carrier", "Armor.png", 5, 4, 2, 2, "armor", 6.6, 3, 3, 2, "armor", None, None, 210, "Armor", None, None, None, False, False, False, 300),
    ("warden_combat_armor", "Warden Combat Armor", "Armor.png", 5, 4, 2, 3, "armor", 8.2, 3, 3, 3, "armor", None, None, 300, "Armor", None, None, None, False, False, False, 480),
    ("juggernaut_assault_armor", "Juggernaut Assault Armor", "Armor.png", 5, 4, 2, 5, "armor", 10.0, 3, 3, 4, "armor", None, None, 420, "Armor", None, None, None, False, False, False, 700),
    ("scout_chest_rig", "Scout Chest Rig", "Armor.png", 5, 4, 3, 1, "armor", 2.4, 2, 2, 1, "equipped_vest", 3, 2, 90, "Vest", 8.0, None, None, False, False, False, 130),
    ("rangefinder_tactical_vest", "Rangefinder Tactical Vest", "Armor.png", 5, 4, 3, 2, "armor", 3.1, 2, 2, 2, "equipped_vest", 4, 2, 130, "Vest", 12.0, None, None, False, False, False, 210),
    ("operator_load_bearing_vest", "Operator Load-Bearing Vest", "Armor.png", 5, 4, 3, 3, "armor", 3.8, 2, 3, 3, "equipped_vest", 4, 3, 180, "Vest", 16.0, None, None, False, False, False, 320),
    ("specter_combat_rig", "Specter Combat Rig", "Armor.png", 5, 4, 3, 5, "armor", 4.6, 2, 3, 4, "equipped_vest", 5, 3, 240, "Vest", 20.0, None, None, False, False, False, 470),
    ("arsenal_elite_vest", "Arsenal Elite Vest", "Armor.png", 5, 4, 3, 5, "armor", 5.3, 2, 3, 5, "equipped_vest", 5, 4, 310, "Vest", 24.0, None, None, False, False, False, 650),
    ("sparrow_sling_pack", "Sparrow Sling Pack", "Armor.png", 5, 4, 4, 1, "tools", 1.2, 2, 2, 1, "equipped_backpack", 4, 4, 80, "Backpack", 14.0, None, None, False, False, False, 120),
    ("fieldrunner_pack", "Fieldrunner Pack", "Armor.png", 5, 4, 4, 2, "tools", 1.8, 2, 2, 2, "equipped_backpack", 5, 5, 130, "Backpack", 22.0, None, None, False, False, False, 200),
    ("mule_tactical_pack", "Mule Tactical Pack", "Armor.png", 5, 4, 4, 3, "tools", 2.6, 2, 3, 3, "equipped_backpack", 6, 6, 200, "Backpack", 32.0, None, None, False, False, False, 330),
    ("atlas_raid_pack", "Atlas Raid Pack", "Armor.png", 5, 4, 4, 4, "tools", 3.6, 3, 3, 4, "equipped_backpack", 7, 7, 280, "Backpack", 44.0, None, None, False, False, False, 520),
    ("atlas_raid_pack_mk2", "Atlas Raid Pack Mk II", "Armor.png", 5, 4, 4, 4, "tools", 4.4, 3, 3, 5, "equipped_backpack", 8, 8, 380, "Backpack", 56.0, None, None, False, False, False, 760),
    ("pioneer_lockbox", "Pioneer Lockbox", "Safe_Box.png", 4, 4, 3, 4, "tools", 1.5, 2, 2, 1, "equipped_safe_container", 2, 2, 90, "Safe Container", 8.0, None, None, False, False, True, 180),
    ("blacksite_secure_case", "Blacksite Secure Case", "Safe_Box.png", 4, 4, 3, 2, "tools", 2.2, 2, 2, 2, "equipped_safe_container", 3, 2, 160, "Safe Container", 10.0, None, None, False, False, True, 320),
    ("omega_safe_container", "Omega Safe Container", "Safe_Box.png", 4, 4, 3, 1, "tools", 3.2, 2, 3, 3, "equipped_safe_container", 3, 3, 260, "Safe Container", 12.0, None, None, False, False, True, 520),
    ("combat_stim_syringe", "Combat Stim Syringe", "Meds.png", 5, 4, 1, 1, "medical", 0.3, 1, 1, 1, None, None, None, None, None, None, 20, 40, True, False, True, 45),
    ("field_med_kit", "Field Med Kit", "Meds.png", 5, 4, 1, 3, "medical", 0.8, 1, 2, 2, None, None, None, None, None, None, 45, 80, True, False, True, 110),
    ("trauma_response_case", "Trauma Response Case", "Meds.png", 5, 4, 1, 4, "medical", 1.4, 2, 2, 3, None, None, None, None, None, None, 80, 120, True, False, True, 260),
    ("quickclot_injector", "QuickClot Injector", "Meds.png", 5, 4, 1, 1, "medical", 0.3, 1, 1, 1, None, None, None, None, None, None, 20, 40, True, False, True, 45),
    ("trauma_field_pack", "Trauma Field Pack", "Meds.png", 5, 4, 1, 3, "medical", 0.8, 1, 2, 2, None, None, None, None, None, None, 45, 80, True, False, True, 110),
    ("blackseal_med_case", "Blackseal Med Case", "Meds.png", 5, 4, 1, 4, "medical", 1.4, 2, 2, 3, None, None, None, None, None, None, 80, 120, True, False, True, 260),
    ("field_dressing_roll", "Field Dressing Roll", "Meds.png", 5, 4, 1, 5, "medical", 0.2, 1, 1, 1, None, None, None, None, None, None, None, 45, True, False, True, 28),
    ("helmet_rebuild_kit", "Helmet Rebuild Kit", "Meds.png", 5, 4, 3, 1, "armor_parts", 0.9, 2, 2, 1, None, None, None, None, None, None, None, None, False, False, True, 120),
    ("armor_rebuild_kit", "Armor Rebuild Kit", "Meds.png", 5, 4, 3, 5, "armor_parts", 1.8, 2, 3, 1, None, None, None, None, None, None, None, None, False, False, True, 220),
    ("pack_rebuild_kit", "Pack Rebuild Kit", "Meds.png", 5, 4, 4, 4, "tools", 1.2, 2, 2, 1, None, None, None, None, None, None, None, None, False, False, True, 150),
]

REPAIR_TARGETS = {
    "helmet_rebuild_kit": ("Helmet", 90),
    "armor_rebuild_kit": ("Armor", 160),
    "pack_rebuild_kit": ("Backpack", 120),
}


def crop_cell(sheet: Image.Image, cols: int, rows: int, row: int, col: int) -> Image.Image:
    width, height = sheet.size
    # Transparent production sheets are already clean. Slice the exact grid
    # cell with rounded float boundaries and preserve alpha/pixels as-is.
    x0 = round((col - 1) * width / cols)
    x1 = round(col * width / cols)
    y0 = round((row - 1) * height / rows)
    y1 = round(row * height / rows)
    return sheet.crop((x0, y0, x1, y1)).convert("RGBA")


def centered_icon_texture(image: Image.Image, size: int = 64, padding: int = 4) -> Image.Image:
    # Only transparent margins are trimmed. Opaque/semi-transparent art pixels
    # are never recolored or removed.
    bbox = image.getbbox()
    if bbox is None:
        return Image.new("RGBA", (size, size), (0, 0, 0, 0))

    icon = image.crop(bbox)
    max_side = max(1, size - padding * 2)
    scale = min(max_side / icon.width, max_side / icon.height)
    target_width = max(1, round(icon.width * scale))
    target_height = max(1, round(icon.height * scale))
    icon = icon.resize((target_width, target_height), Image.Resampling.NEAREST)

    output = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    output.alpha_composite(icon, ((size - target_width) // 2, (size - target_height) // 2))
    return output


def generate_assets() -> None:
    TEXTURES.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)
    PROFILES.parent.mkdir(parents=True, exist_ok=True)
    VALUES.parent.mkdir(parents=True, exist_ok=True)

    sheets = {name: Image.open(SPRITES / name).convert("RGBA") for name in {item[2] for item in ITEMS}}
    for item in ITEMS:
        item_id, _display, sheet_name, cols, rows, row, col, *_ = item
        if item_id in INACTIVE_ITEMS:
            continue
        if item_id in DIRECT_ASSET_ITEMS:
            continue
        image = crop_cell(sheets[sheet_name], cols, rows, row, col)
        image = centered_icon_texture(image)
        image.save(TEXTURES / f"{item_id}.png")
        (MODELS / f"{item_id}.json").write_text(
            json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"extractcraft:item/{item_id}"}}, indent=2) + "\n",
            encoding="utf-8",
        )

    lang = json.loads(LANG.read_text(encoding="utf-8"))
    lang["itemGroup.extractcraft"] = "ExtractCraft"
    for item_id, display, *_ in ITEMS:
        lang[f"item.extractcraft.{item_id}"] = display
    LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    profiles = []
    values = []
    for item in ITEMS:
        (
            item_id,
            _display,
            _sheet_name,
            _cols,
            _rows,
            _row,
            _col,
            category,
            weight,
            grid_width,
            grid_height,
            tier,
            equipment_slot,
            storage_width,
            storage_height,
            durability,
            repair_category,
            max_carry,
            heal,
            use_ticks,
            fixes_bleed,
            fixes_bone,
            allow_safe,
            value,
        ) = item
        if item_id in INACTIVE_ITEMS:
            continue
        profile = {
            "item": f"extractcraft:{item_id}",
            "category": category,
            "weight": weight,
            "slotCost": max(1, grid_width * grid_height),
            "gridWidth": grid_width,
            "gridHeight": grid_height,
            "canRotate": True,
            "allowInSafeBox": bool(allow_safe),
            "allowInVest": category == "medical",
            "tier": tier,
            "notes": ["ExtractCraft equipment/content profile; durability/use behavior is inert for now."],
        }
        if equipment_slot:
            profile["equipmentSlot"] = equipment_slot
        if storage_width and storage_height:
            profile["storageGridDefinition"] = f"{storage_width}x{storage_height}"
            profile["storageGridWidth"] = storage_width
            profile["storageGridHeight"] = storage_height
        if max_carry:
            profile["maxCarryWeight"] = max_carry
        if durability:
            profile["durabilityEnabled"] = True
            profile["maxDurability"] = durability
            profile["repairCategory"] = repair_category
        if repair_category in ("Helmet", "Armor") and item_id not in REPAIR_TARGETS:
            profile["armorRating"] = tier
        if heal:
            profile["healAmount"] = heal
        if use_ticks:
            profile["useTimeTicks"] = use_ticks
        if item_id in REPAIR_TARGETS:
            target, amount = REPAIR_TARGETS[item_id]
            profile["repairTargetCategory"] = target
            profile["repairAmount"] = amount
            profile["consumable"] = True
        if fixes_bleed:
            profile["fixesBleed"] = True
        if fixes_bone:
            profile["fixesBrokenBone"] = True
        if heal or use_ticks or item_id in REPAIR_TARGETS:
            profile["consumable"] = True
        profiles.append(profile)

        rarity = "common" if tier <= 1 else "uncommon" if tier == 2 else "rare" if tier == 3 else "epic"
        values.append({
            "item": f"extractcraft:{item_id}",
            "category": category,
            "rarity": rarity,
            "value": value,
            "sellable": True,
            "questItem": False,
            "lootTier": tier,
        })

    PROFILES.write_text(json.dumps({"profiles": profiles}, indent=2) + "\n", encoding="utf-8")
    VALUES.write_text(json.dumps({"values": values}, indent=2) + "\n", encoding="utf-8")
    active_items = len([item for item in ITEMS if item[0] not in INACTIVE_ITEMS])
    print(f"Generated {active_items} active ExtractCraft item assets/profiles/values.")


if __name__ == "__main__":
    generate_assets()
