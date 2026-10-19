package com.tonkid.crateautoreward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ServerProfiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("crateautoreward");
    private static final Path PREFERENCES = ROOT.resolve("servers.json");
    private Map<String,String> selection = new HashMap<>();
    private static ServerProfiles instance;

    private ServerProfiles() {}

    public static synchronized ServerProfiles instance() {
        if (instance != null) return instance;
        instance = new ServerProfiles();
        if (Files.isRegularFile(PREFERENCES)) {
            try {
                ServerProfiles saved = GSON.fromJson(Files.readString(PREFERENCES,StandardCharsets.UTF_8),ServerProfiles.class);
                if (saved != null && saved.selection != null) instance.selection = new HashMap<>(saved.selection);
            } catch (Exception e) {
                System.err.println("[CrateAutoReward] Failed reading profile preferences: "+e);
            }
        }
        return instance;
    }
    public static String address() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) {
            return mc.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
        }
        return mc.hasSingleplayerServer() ? "singleplayer" : "unknown";
    }
    public String selected() {
        return selection.getOrDefault(address(),"ยังไม่เลือก");
    }
    public boolean hasSelection() {
        String chosen = selected();
        return chosen.equals("MMO") || chosen.equals("SMP");
    }
    public void select(String name) {
        if (!name.equals("MMO") && !name.equals("SMP")) throw new IllegalArgumentException("Only MMO and SMP are supported");
        selection.put(address(),name);
        save();
    }
    public String key() {
        return address()+" | "+selected();
    }
    public String profileLabel() {
        return selected()+" @ "+address();
    }
    public static Path root() {return ROOT;}
    public Path activePath(String suffix) {
        if (!hasSelection()) throw new IllegalStateException("Choose MMO or SMP before saving");
        return ROOT.resolve("profiles").resolve(digest(key())).resolve(suffix);
    }
    private static String digest(String input) {
        try {
            byte[] raw=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder();
            for(int i=0;i<12;i++) b.append(String.format("%02x",raw[i] & 0xFF));
            return b.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private void save() {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(PREFERENCES,GSON.toJson(this),StandardCharsets.UTF_8);
        }catch(IOException e){System.err.println("[CrateAutoReward] Cannot save server profile: "+e);}
    }
}
