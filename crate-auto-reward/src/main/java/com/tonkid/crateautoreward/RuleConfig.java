package com.tonkid.crateautoreward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RuleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file() { return ServerProfiles.instance().activePath("rules.json"); }

    public static final class Rule {
        public String source="NAME";
        public String contains="STAFF";
        public String rarity="mythical";
        public String weight="0.01";

        public Rule() {}
        public Rule(String source,String contains,String rarity,String weight) {
            this.source=source;this.contains=contains;this.rarity=rarity;this.weight=weight;
        }
        public Rule copy() { return new Rule(source,contains,rarity,weight); }
        public String info(){return source+" : "+contains+"  >  "+rarity+" / "+weight;}
    }
    public List<Rule> rules = new ArrayList<>();
    public boolean autoApplyRates = true;

    public static RuleConfig defaults() {
        RuleConfig config = new RuleConfig();
        // Specific weapon and armor subtypes first; generic categories last.
        config.rules.add(new Rule("NAME", "หมวกหลัก", "mythical", "8"));
        config.rules.add(new Rule("NAME", "MAIN HELMET", "mythical", "8"));
        config.rules.add(new Rule("NAME", "WINGS", "mythical", "7"));
        config.rules.add(new Rule("NAME", "ปีก", "mythical", "7"));

        config.rules.add(new Rule("NAME", "CROSSBOW", "epic", "10"));
        config.rules.add(new Rule("NAME", "หน้าไม้", "epic", "10"));
        config.rules.add(new Rule("NAME", "BOW", "epic", "10"));
        config.rules.add(new Rule("NAME", "ธนู", "epic", "10"));
        config.rules.add(new Rule("NAME", "SHIELD", "rare", "10"));
        config.rules.add(new Rule("NAME", "โล่", "rare", "10"));

        config.rules.add(new Rule("NAME", "PICKAXE", "legendary", "10"));
        config.rules.add(new Rule("NAME", "PICK AXE", "legendary", "10"));
        config.rules.add(new Rule("NAME", "SHOVEL", "legendary", "10"));
        config.rules.add(new Rule("NAME", "HOE", "legendary", "10"));
        config.rules.add(new Rule("NAME", "FISHING ROD", "legendary", "10"));
        config.rules.add(new Rule("NAME", "SHEARS", "legendary", "10"));
        config.rules.add(new Rule("NAME", "TOOL", "legendary", "10"));
        config.rules.add(new Rule("NAME", "อุปกรณ์", "legendary", "10"));

        config.rules.add(new Rule("NAME", "SWORD", "mythical", "8"));
        config.rules.add(new Rule("NAME", "ดาบ", "mythical", "8"));
        config.rules.add(new Rule("NAME", "STAFF", "mythical", "10"));
        config.rules.add(new Rule("NAME", "DAGGER", "mythical", "10"));
        config.rules.add(new Rule("NAME", "SPEAR", "mythical", "10"));
        config.rules.add(new Rule("NAME", "TRIDENT", "mythical", "10"));
        config.rules.add(new Rule("NAME", "MACE", "mythical", "10"));
        config.rules.add(new Rule("NAME", "HAMMER", "mythical", "10"));
        config.rules.add(new Rule("NAME", "BATTLE AXE", "mythical", "10"));
        config.rules.add(new Rule("NAME", "อาวุธ", "mythical", "10"));

        config.rules.add(new Rule("NAME", "HELMET", "legendary", "10"));
        config.rules.add(new Rule("NAME", "CHESTPLATE", "legendary", "10"));
        config.rules.add(new Rule("NAME", "LEGGINGS", "legendary", "10"));
        config.rules.add(new Rule("NAME", "BOOTS", "legendary", "10"));
        config.rules.add(new Rule("NAME", "ARMOR", "legendary", "10"));
        config.rules.add(new Rule("NAME", "เกราะ", "legendary", "10"));

        // Material fallbacks when a vanilla/custom display name has no category word.
        config.rules.add(new Rule("MATERIAL", "_crossbow", "epic", "10"));
        config.rules.add(new Rule("MATERIAL", "_bow", "epic", "10"));
        config.rules.add(new Rule("MATERIAL", "_shield", "rare", "10"));
        config.rules.add(new Rule("MATERIAL", "_sword", "mythical", "8"));
        config.rules.add(new Rule("MATERIAL", "_trident", "mythical", "10"));
        config.rules.add(new Rule("MATERIAL", "_mace", "mythical", "10"));
        config.rules.add(new Rule("MATERIAL", "_helmet", "legendary", "10"));
        config.rules.add(new Rule("MATERIAL", "_chestplate", "legendary", "10"));
        config.rules.add(new Rule("MATERIAL", "_leggings", "legendary", "10"));
        config.rules.add(new Rule("MATERIAL", "_boots", "legendary", "10"));
        config.rules.add(new Rule("MATERIAL", "_pickaxe", "legendary", "10"));
        config.rules.add(new Rule("MATERIAL", "_axe", "legendary", "10"));
        config.rules.add(new Rule("MATERIAL", "_shovel", "legendary", "10"));
        config.rules.add(new Rule("MATERIAL", "_hoe", "legendary", "10"));
        config.rules.add(new Rule("NAME", "*", "common", "10"));
        return config;
    }

    public static RuleConfig load() {
        if (!ServerProfiles.instance().hasSelection()) return defaults();
        Path file=file();
        if (!Files.exists(file)) {
            RuleConfig defaults=defaults();defaults.save();return defaults;
        }
        try {
            RuleConfig data=GSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),RuleConfig.class);
            if (data==null || data.rules==null) return defaults();
            data.rules.removeIf(rule->rule==null);
            return data;
        } catch (Exception e) {
            System.err.println("[CrateAutoReward] Invalid config: "+e.getMessage());
            return defaults();
        }
    }
    public void save(){
        if (!ServerProfiles.instance().hasSelection()) return;
        try {
            Path file=file();
            Files.createDirectories(file.getParent());
            Files.writeString(file,GSON.toJson(this),StandardCharsets.UTF_8);
        } catch(IOException e) {
            System.err.println("[CrateAutoReward] Failed to save rules: "+e);
        }
    }
    public Rule match(ItemStack stack) {
        if(stack.isEmpty()) return null;
        String name=normalize(stack.getHoverName().getString());
        String material=normalize(stack.getItem().toString());
        StringBuilder lore=new StringBuilder();
        ItemLore list=stack.get(DataComponents.LORE);
        if(list!=null) for(var component:list.lines()) lore.append(normalize(component.getString())).append(" ");
        Rule fallback=null;
        for(Rule rule:rules){
            if(rule.contains==null || rule.source==null || rule.rarity==null || rule.weight==null) continue;
            if(normalize(rule.contains).equals("*")){if(fallback==null)fallback=rule;continue;}
            String input=switch(rule.source.toUpperCase(Locale.ROOT)){
                case "LORE" -> lore.toString();
                case "MATERIAL" -> material;
                default -> name;
            };
            String term=normalize(rule.contains);
            if(!term.isEmpty() && input.contains(term)) return rule.copy();
        }
        return fallback==null?null:fallback.copy();
    }
    // Small-caps in custom item names are not ordinary ASCII uppercase/lowercase.
    // Unicode NFKC alone does not fold all of these glyphs.
    public static String normalize(String value){
        if(value==null) return "";
        String from="ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        String to="abcdefghijklmnopqrstuvwxyz";
        String lower=value.toLowerCase(Locale.ROOT).replaceAll("§[0-9a-fk-or]","");
        StringBuilder output=new StringBuilder();
        for(int i=0;i<lower.length();i++){
            char c=lower.charAt(i);
            int idx=from.indexOf(c);
            if(idx>=0 && idx<to.length()) output.append(to.charAt(idx));
            else output.append(c);
        }
        return output.toString().trim();
    }
    public static boolean validWeight(String text){
        try {
            double weight=Double.parseDouble(text);
            return Double.isFinite(weight) && weight>=0 && text.length()<=6;
        } catch(Exception e){return false;}
    }
}
