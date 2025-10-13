package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.Base64;
import java.util.UUID;

public class HeadURL {
    public static final String experienceB64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWNlMGJkNWY3OWRkMWE3ZTg5MjA1YWQ3Y2I1ODMxZDIxNGM5NDQ1MjBiZGU5YTg1OWQ1NWYyODYwYmNlOCJ9fX0=";

    public static final String waterB64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWM3ZWNiZmQ2ZDMzZTg3M2ExY2Y5YTkyZjU3ZjE0NjE1MmI1MmQ5ZDczMTE2OTQ2MDI2NzExMTFhMzAyZiJ9fX0=";

    public static final String lavaB64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjY5NjVlNmE1ODY4NGMyNzdkMTg3MTdjZWM5NTlmMjgzM2E3MmRmYTk1NjYxMDE5ZGJjZGYzZGJmNjZiMDQ4In19fQ==";

    public static final String milkB64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTVhNzcwZTdlNDRiM2ExZTZjM2I4M2E5N2ZmNjk5N2IxZjViMjY1NTBlOWQ3YWE1ZDUwMjFhMGMyYjZlZSJ9fX0=";

    public static final String mushroomStewB64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzZmZDc0MjUyODJlOWZiMzgzYzdmZTE2NDllMTE5NzYzNTE2M2RhZmYxOGFkNTgyMmE0OTMwZjQzNDJkNDc3MiJ9fX0=";

    public static final String rabbitStewB64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDJkYThlMDk0ZDNkMDM4YjM4ZWU1NWEyZWNhMjlhZDY5ZjNlZDFhYzgzMjlkNTM0YmI3OWFiNjRjYzFkOTEyIn19fQ==";

    public static final String dragonBreathB64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmI2NjM5NTVmNDg3MzFhZTEzNTdhY2EzODdmNWQxOWRhZTQxNzZhZDFkYmQ1MWE0ODQxZjZlNWEyODIxODIifX19";

    public static final String beetrootB64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTNiZjM5NGQyZDZjZWZkNDdmNmIyNmU4NWEwZDU1OGFkN2MzYjRjOGRmZGVlNGFkZmEwYjkzY2UzNTEzZCJ9fX0=";

    public static final String honeyB64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWI3YjE0ZjNjNzg3ODVjMWZkMjQ0MjU1ZTA1ZDUzN2Q2YzU1MTIwNzI2MmE2MGMzODEyNWMwMWY2NjNhMDc1ZSJ9fX0=";


    public static String decodeBase64AndGetURL(String base64String) throws ParseException {
        // Decode Base64 string
        byte[] decodedBytes = Base64.getDecoder().decode(base64String);
        String decodedString = new String(decodedBytes);

        // Parse JSON and retrieve URL
        JSONParser parser = new JSONParser();
        JSONObject json = (JSONObject) parser.parse(decodedString);

        JSONObject textures = (JSONObject) json.get("textures");
        JSONObject skin = (JSONObject) textures.get("SKIN");

        return (String) skin.get("url");
    }

    public static ItemStack create(String paramString) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", paramString));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }
}
