# Armor Mitigation HUD Notes

HUD display is active-raid only and shows the rounded combined mitigation percentage.

Formula:
combined = 1 - ((1 - armorMitigation) * (1 - helmetMitigation))

Examples:
- no armor/no helmet: 0%
- tier 3 armor only: 35%
- tier 4 helmet only: 15%
- tier 3 armor + tier 4 helmet: 45% (44.75% rounded)
- tier 4 armor + tier 4 helmet: 53% (53.25% rounded)

Gear at 0 current durability contributes 0%.
