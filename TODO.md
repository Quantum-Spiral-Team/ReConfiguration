# Config System Rework — TODO

Cleanroom-native rework of the Forge 1.12.2 config system, designed to later ship as a standalone Forge-compatible mod.  
Goal: legacy mods using `Configuration`/`@Config` keep working untouched through a compatibility facade, while the engine underneath is faster, modular, and open to new types/lifecycles. New abstractions are opt-in for devs who want more than the legacy surface offers.

---

## 0. Open Decisions
Resolve these before/while implementing the relevant section — they change the shape of the work.

- [ ] Depth of partial/lazy file I/O: full indexed/lazy-parse design vs in-memory tree + dirty-tracking with selective rewrite
- [ ] Final namespace/placement for the new annotation set (own package vs nested under the existing `@Config`)
- [ ] Compatibility guarantee boundary: public/protected API only — explicitly not chasing mods that reflect into legacy private fields

---

## 1. Core Data Model & Type System

- [ ] **ConfigProperty** — open property system (replacing a closed `Property` model)
  - [x] Primitives (`byte`, `short`, `int`, `long`, `float`, `double`, `boolean`)
  - [x] Primitive arrays (`byte[]`, `short[]`, `int[]`, `long[]`, `float[]`, `double[]`, `boolean[]`)
  - [x] Objects (WIP see `ObjectConfigProperty` and `PropertyUtils#serialize`/`PropertyUtils#deserialize`)
  - [ ] Object arrays
  - [ ] Category (nested config objects)
  - [ ] Collections framework / Maps
  - [ ] Native FastUtil collection/map support
- [ ] Design a `TypeAdapter`/codec registry for custom type support (external interface — third-party mods can register their own types)
- [ ] Document boxing boundaries explicitly: boxing is acceptable on cold paths (load/save/GUI), never on hot runtime field access

## 2. File Format & I/O

- [ ] Design a new human-readable file format (successor to the `S:`/`B:`/`I:`/`D:`-prefixed Forge syntax)
- [ ] Per-file header block containing: schema/system format version, plus `modId`, `modName`, `modVersion` of the owning mod
- [ ] Legacy → new format migration
  - [ ] Detect legacy-format files (missing/old header)
  - [ ] On first encounter: parse the legacy section, write the new-format section separately, leave the old block in place but superseded
  - [ ] Rewrite legacy section in-place into the new format, then stamp the header with the current schema version
  - [ ] After migration, only ever parse the new-format section on subsequent loads
  - [ ] Defensive fallback for a malformed/partial header: re-treat the file as legacy and re-run migration (or warn + prompt regeneration) rather than failing silently
  - [ ] Tie each file's migration to its owning config's lifecycle trigger (§3) instead of migrating everything eagerly at boot, to avoid one long startup stall
- [ ] Partial/lazy read & write
  - [ ] Avoid a full-file reparse on every read of a single value
  - [ ] Avoid a full-file rewrite on every write of a single value (dirty-tracking + selective patch, at minimum)
- [ ] Error handling
  - [ ] Whole-file parse failure → rename the file (e.g. `*.cfg.illegal`), load defaults for the owning config, emit an in-game warning
  - [ ] Single-field parse failure → load the default for that field only, emit an in-game warning, mark the field in the written file (e.g. inline comment) so it's spottable
- [ ] "Deprecated" block
  - [ ] Fields/categories no longer referenced by the owning mod move into a dedicated block instead of being silently ignored
  - [ ] Block is written at the bottom of the file, human-readable, but never parsed/loaded by the system
  - [ ] Entries persist until a human removes them or deletes the file
- [ ] "Find unused `.cfg` files" tooling
  - [ ] Cross-reference each config file's header `modId` against the currently installed mod list
  - [ ] Report files whose owning mod is no longer present, for modpack devs cleaning up before a release
  - [ ] Depends on the header `modId` tagging from this section being in place

## 3. Loading Lifecycle

- [ ] Replace the rigid `Config.Type.INSTANCE`-only enum with an extensible trigger abstraction (interface/strategy), not a growing enum
- [ ] Port the three load triggers already scoped by `ReConfig.Type`:
  - [ ] `INSTANCE` — loaded once right after mod construction, before pre-init (static fields)
  - [x] class-touch trigger — loaded on first access to the config class (`<clinit>`), equivalent to `ReConfig.Type.LAZY` / the old ConfigAnytime pattern, but without requiring the developer to manually write a static-block load call
  - [ ] world-join trigger — loaded once on world load (`ReConfig.Type.PER_WORLD`), matching newer-MC-version behavior
- [ ] Leave the abstraction open for future trigger types without breaking existing ones

## 4. Backward-Compatible Facade

- [ ] Keep `Configuration` / `Config` public signatures byte-for-byte unchanged
- [ ] Reimplement their internals as thin views over the new unified in-memory core
- [ ] Redirect `@Config` + `ConfigManager` annotation processing through the same core
- [ ] Verify `IConfigElement`/GUI generation keeps working unmodified by reading off the facade — no separate GUI maintenance path
- [ ] Explicitly scope the compatibility guarantee to the public/protected API (see §0)

## 5. New Annotation Set (ReConfig-inspired)

- [ ] Add sides support to `ReConfig` annotation (`Side.CLIENT` / `Side.SERVER`)
- [ ] Port type-level options from `ReConfig`: `modid`, `name` (defaults to modid), `dir`, `type` (lifecycle trigger from §3), `category` (root category name; empty string disables the root category)
- [ ] Port field-level annotations: `@LangKey`, `@Comment`, `@Ignore`, `@RangeInt`, `@RangeDouble`, `@Range(String)`, `@Name`, `@RequiresMcRestart`, `@RequiresWorldRestart`, `@SlidingOption`
- [ ] Resolve namespace/placement relative to the existing `@Config` annotations (§0)
- [ ] Document this set as the opt-in, lower-boilerplate path for devs who want more than the legacy facade offers

## 6. ConditionParser

- [x] Create the numeric `ConditionParser` largely as: intervals (`(a..b)`, `[a..b]`, half-open variants, unbounded sides), set literals (`{n1, n2, ...}`), `&`/`|`/`!` operators, parenthesized grouping, recursive-descent compile-to-predicate
- [ ] Wire it up to the `@Range(String)` annotation (currently a stub/TODO in `ReConfig`) so range strings actually validate values
- [x] Build a String-flavored ConditionParser variant
  - [x] Grammar: whitelist / blacklist entries + `!` negation (no interval syntax — strings have no `..` notion)
  - [x] Reuse the same tokenizer / recursive-descent / compiled-predicate architecture as the numeric parser instead of writing a second parser from scratch
- [ ] Reuse the String condition engine to drive validation for `Enum` and `UUID` fields (whitelist/blacklist of allowed values) instead of writing bespoke validators per type

## 7. GUI

- [ ] Confirm full GUI support is preserved through the refactor via the §4 facade — no regressions for existing `IConfigElement`-based mods
- [ ] (Later, lowest priority) Rework GUI rendering for a more user-friendly experience, scoped as a separate effort once the core and compat layers are stable
