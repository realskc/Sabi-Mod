# Sabi Machine Price Rule Report

This file is a short snapshot of how the Sabi machine price tables are organized. It is not used by the mod at runtime.

## Current Counts

- Allowed items: 1363
- Base price items: 316
- Virtual symbols: 6
- Derived price items: 1047
- Derived recipe entries: 1492
- Fortune III loot formulas: 21

## How Prices Are Split

- `base_prices.json` holds manually priced items and virtual symbols such as `sabi:generic_dye`, `sabi:lava`, and `sabi:honey`.
- `derived_prices.json` holds calculated prices from recipes, manual equivalences, loot expectations, and symbol-based formulas.
- If multiple formulas exist for an item, the resolver uses the cheapest resolvable formula.

## Important Manual Rules

- Dyed items use `sabi:generic_dye`; several bucket contents and honey are virtual symbols.
- Several equivalences intentionally override vanilla recipes, such as concrete from concrete powder, dead coral from live coral, filled maps from maps, clocks and templates from gems, copper golem statues from copper blocks plus pumpkins, and honey items from `sabi:honey`.
- Ore-like drops use Fortune III loot formulas only for selected allowlisted blocks.
- Some raw resources and fuels remain manually base-priced instead of being derived from reverse or burn-time recipes.
