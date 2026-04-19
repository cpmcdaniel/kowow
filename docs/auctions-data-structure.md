# Auction Snapshot JSON — Data Structure

This document describes the shape of the Blizzard "connected-realm auctions" snapshot files produced in `output/auctions/` and explains what each field means and how to look up referenced IDs.

Example file: `output/auctions/auctions-elune-67.json`

## Top-level structure

- `_links` — standard HAL links. `_links.self.href` is the API URL for this auctions snapshot.
- `connected_realm` — an object with `href` pointing to the connected-realm resource (use this to fetch realm metadata).
- `auctions` — an array of auction objects (one element per active auction entry at snapshot time).

## Auction object (one element of `auctions`)

Common fields seen in the snapshot:

- `id` (integer): Unique auction instance id assigned by the server. Useful for deduping and referencing a single auction entry.

- `item` (object): Describes the item being sold. Typical subfields:
  - `id` (integer): The item id. Use the Game Data API `/data/wow/item/{itemId}` to fetch full item metadata (name, tooltip data, class, subclass, quality, vendor price, etc.).
  - `context` (integer, optional): Context code describing how the item was obtained (e.g. raid, dungeon, crafted variant). Not always present. [Clarification: enumerate the context values and what each means, if there is a definitive source]
  - `bonus_lists` (array of integers, optional): One or more bonus list ids describing special bonuses applied to the item (e.g. dungeon/raid or item-level bonuses). These affect the exact item variant and properties.
  - `modifiers` (array of {`type`, `value`} objects, optional): Extra numeric modifiers. `type` is a modifier kind code and `value` is the associated value. Modifier types are defined by Blizzard — common uses include item-level adjustments or secondary-stat encoding.
  - Pet fields (for battle pets): `pet_species_id`, `pet_breed_id`, `pet_level`, `pet_quality_id` — present when the item is a battle pet.

- `buyout` (integer): Buyout price in copper (the API reports prices as integer copper values). Convert to gold: 1 gold = 10000 copper (or 100 silver * 100 copper depending on your conversion preference). Example: `3336600` = 333.66 gold.

- `quantity` (integer): Number of units being sold in this auction stack.

- `time_left` (string): One of Blizzard's snapshot time-left categories, typically `SHORT`, `MEDIUM`, `LONG`, `VERY_LONG`. This is a coarse indicator of how much time remains on the auction.

You may also encounter other fields depending on item type and API version (e.g., `bid`, seller info, `owner` may be present in other endpoints or with profile scope).

## Example auction entry (annotated)

```json
{
  "id": 987215840,
  "item": {
    "id": 45927,
    "context": 5,
    "bonus_lists": [9042],
    "modifiers": [{"type":28, "value":1020}]
  },
  "buyout": 3336600,
  "quantity": 1,
  "time_left": "SHORT"
}
```

Notes on the example:
- `item.id = 45927` → use the Item endpoint to get name/description.
- `bonus_lists = [9042]` and `modifiers` indicate this is a variant with bonuses (e.g., different ilvl or upgrade state).

## How to look up referenced IDs (practical examples)

All API calls below require a valid OAuth access token (client credentials flow). In this repo use `kowow.blizzard/get-access-token` to obtain a token and `kowow.blizzard/wow-api-get` to make authenticated requests.

1. Item metadata

- URL pattern (example):

  https://us.api.blizzard.com/data/wow/item/{itemId}?namespace=static-us&locale=en_US

  Replace `{itemId}` with the `item.id`. Use `namespace=static-us` for item definitions (static game data). The response contains the item name, description, required level, class/subclass, and more.

  Example (Clojure):
  ```clojure
  (let [token (kowow.blizzard/get-access-token)
        item (kowow.blizzard/wow-api-get token (str "/data/wow/item/" item-id) {:namespace "static-us" :locale "en_US"})]
    item)
  ```

2. Connected-realm metadata (to get human-readable realm names)

- URL pattern:

  https://us.api.blizzard.com/data/wow/connected-realm/{connectedRealmId}?namespace=dynamic-us&locale=en_US

  The returned object includes `realms` (array) where each realm has `name` and `slug`. The snapshot filename in this project uses the first realm's `slug`.

3. Bonus lists and modifiers

- Blizzard documents bonus-list and modifier behavior in the Game Data API docs; there is not always a single API endpoint that fully decodes every modifier code into a human-friendly string.
- Practical approach:
  - Use the `item` endpoint with the same `bonus_lists` and `modifiers` to compare how the item appears in the API tooltip or `item-media` endpoints.
  - The `bonus_lists` values often map to specific raid/dungeon or item-variant definitions. Searching the Blizzard docs and community resources for a bonus-list id can help decode it.

4. Battle pet lookups

- When `pet_species_id` is present, look up species details:

  https://us.api.blizzard.com/data/wow/pet-species/{speciesId}?namespace=static-us&locale=en_US

  This returns the pet name and family.

## Useful tips and commands

- Convert buyout to gold: `gold = buyout / 10000.0`.
- If you want to enrich snapshots programmatically, write a small process that reads `auctions`, fetches `/data/wow/item/{id}` for each distinct item id, and caches responses.

Example curl to fetch one item (replace token):

```bash
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  "https://us.api.blizzard.com/data/wow/item/45927?namespace=static-us&locale=en_US"
```

Example Clojure enrichment snippet (using existing repo helper):

```clojure
(let [token (kowow.blizzard/get-access-token)
      snapshot (json/parse-string (slurp "output/auctions/auctions-elune-67.json") true)
      item-ids (->> snapshot :auctions (map #(get-in % [:item :id])) set)]
  (into {}
        (for [id item-ids]
          [id (kowow.blizzard/wow-api-get token (str "/data/wow/item/" id) {:namespace "static-us" :locale "en_US"})])))
```

## Links / References

- Blizzard Game Data APIs: https://develop.battle.net/documentation/world-of-warcraft
- Item endpoint docs: use the "Item" entry under Game Data APIs (search for `/data/wow/item/{itemId}`).
- Connected-realm endpoint docs: search for `/data/wow/connected-realm/{id}` in the Game Data API docs.

---

If you'd like, I can:
- Add an enrichment script that augments each `output/auctions/*.json` with resolved item names and pet names (cached),
- Or create a small CSV export summarizing item id, name, avg price in the snapshot.

Which would you prefer next?