package nl.hauntedmc.serverfeatures.api.util;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.tag.TagKey;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.bukkit.block.banner.PatternType;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.map.MapCursor;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;


public class BukkitRegistry {

    public static <T extends Keyed> Registry<@NotNull T> registry(RegistryKey<@NotNull T> key) {
        return RegistryAccess.registryAccess().getRegistry(key);
    }

    public static <T extends Keyed> @Nullable T get(RegistryKey<@NotNull T> key, NamespacedKey id) {
        return registry(key).get(id);
    }

    public static <T extends Keyed> @Nullable T get(RegistryKey<@NotNull T> key, String id) {
        NamespacedKey nk = NamespacedKey.fromString(id);
        if (nk == null) nk = NamespacedKey.fromString("minecraft:" + id);
        return nk == null ? null : registry(key).get(nk);
    }

    public static <T extends Keyed> TagKey<@NotNull T> tag(RegistryKey<@NotNull T> key, String tagId) {
        return key.tagKey(tagId);
    }

    public static <T extends Keyed> TypedKey<@NotNull T> typed(RegistryKey<@NotNull T> key, String id) {
        return key.typedKey(id);
    }

    /**
     * Deserialize a key. Accepts:
     *  - namespaced id (e.g., minecraft:entity.player.levelup)
     *  - simple dot path (entity.player.levelup) -> minecraft:entity.player.levelup
     *  - legacy enum name (ENTITY_PLAYER_LEVELUP) -> minecraft:entity.player.levelup
     */
    public static NamespacedKey deserializeNamespacedKey(String input) {
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        NamespacedKey direct = NamespacedKey.fromString(trimmed);
        if (direct != null) return direct;

        String legacyDot = trimmed.replace('_', '.');
        return NamespacedKey.fromString("minecraft:" + legacyDot);
    }

    public static Registry<@NotNull Sound> soundRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT);
    }

    public static Registry<@NotNull Particle> particleRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.PARTICLE_TYPE);
    }

    public static Registry<@NotNull PotionEffectType> mobEffectRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
    }

    public static Registry<@NotNull Attribute> attributeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE);
    }

    public static Registry<@NotNull PatternType> bannerPatternRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN);
    }

    public static Registry<@NotNull Biome> biomeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
    }

    public static Registry<@NotNull BlockType> blockRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK);
    }

    public static Registry<Cat.@NotNull Type> catVariantRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.CAT_VARIANT);
    }

    public static Registry<Chicken.@NotNull Variant> chickenVariantRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.CHICKEN_VARIANT);
    }

    public static Registry<Cow.@NotNull Variant> cowVariantRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.COW_VARIANT);
    }

    public static Registry<@NotNull DamageType> damageTypeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE);
    }

    public static Registry<@NotNull DataComponentType> dataComponentTypeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.DATA_COMPONENT_TYPE);
    }

    public static Registry<@NotNull Dialog> dialogRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG);
    }

    public static Registry<@NotNull Enchantment> enchantmentRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    }

    public static Registry<@NotNull EntityType> entityTypeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENTITY_TYPE);
    }

    public static Registry<@NotNull Fluid> fluidRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.FLUID);
    }

    public static Registry<Frog.@NotNull Variant> frogVariantRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.FROG_VARIANT);
    }

    public static Registry<@NotNull GameEvent> gameEventRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.GAME_EVENT);
    }

    public static Registry<@NotNull MusicInstrument> instrumentRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.INSTRUMENT);
    }

    public static Registry<@NotNull ItemType> itemRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM);
    }

    public static Registry<@NotNull JukeboxSong> jukeboxSongRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.JUKEBOX_SONG);
    }

    public static Registry<MapCursor.@NotNull Type> mapDecorationTypeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.MAP_DECORATION_TYPE);
    }

    public static Registry<@NotNull MemoryKey<?>> memoryModuleTypeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.MEMORY_MODULE_TYPE);
    }

    public static Registry<@NotNull MenuType> menuTypeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.MENU);
    }

    public static Registry<@NotNull Art> paintingVariantRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.PAINTING_VARIANT);
    }

    public static Registry<Pig.@NotNull Variant> pigVariantRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.PIG_VARIANT);
    }

    public static Registry<@NotNull PotionType> potionRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.POTION);
    }

    public static Registry<@NotNull Structure> structureRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
    }

    public static Registry<@NotNull StructureType> structureTypeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE_TYPE);
    }

    public static Registry<@NotNull TrimMaterial> trimMaterialRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
    }

    public static Registry<@NotNull TrimPattern> trimPatternRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
    }

    public static Registry<Villager.@NotNull Profession> villagerProfessionRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.VILLAGER_PROFESSION);
    }

    public static Registry<Villager.@NotNull Type> villagerTypeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.VILLAGER_TYPE);
    }

    public static Registry<Wolf.@NotNull SoundVariant> wolfSoundVariantRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.WOLF_SOUND_VARIANT);
    }

    public static Registry<Wolf.@NotNull Variant> wolfVariantRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.WOLF_VARIANT);
    }
}
