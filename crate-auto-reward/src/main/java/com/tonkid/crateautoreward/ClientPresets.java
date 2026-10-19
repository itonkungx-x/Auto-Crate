package com.tonkid.crateautoreward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public final class ClientPresets {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static class SavedPreset {
        public String name="";
        public List<String> itemData=new ArrayList<>();
        public List<String> labels=new ArrayList<>();
        public long savedTime=0;
        public List<RuleConfig.Rule> rateRules=new ArrayList<>();
        public boolean autoApplyRates=true;
    }
    private List<SavedPreset> presets=new ArrayList<>();
    private String activePreset="";
    private ClientPresets(){}

    private static Path file(){return ServerProfiles.instance().activePath("presets.json");}

    public static ClientPresets load(){
        if(!ServerProfiles.instance().hasSelection())return new ClientPresets();
        Path file=file();
        if(Files.isRegularFile(file)){
            try{
                ClientPresets result=GSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),ClientPresets.class);
                if(result!=null && result.presets!=null){
                    result.presets.removeIf(p->p==null || p.name==null || p.itemData==null);
                    if(result.activePreset==null)result.activePreset="";
                    return result;
                }
            }catch(Exception e){System.err.println("[CrateAutoReward] Cannot parse saved presets: "+e);}
        }
        return new ClientPresets();
    }
    public List<SavedPreset> list(){return new ArrayList<>(presets);}
    public SavedPreset selected(){
        for(SavedPreset p:presets)if(p.name.equals(activePreset))return p;
        return null;
    }
    public String selectedName(){return selected()==null?"ใช้ของในกระเป๋าทั้งหมด":activePreset;}
    public void select(String name){
        if(name.isEmpty()){activePreset="";save();return;}
        if(find(name)==null)throw new IllegalArgumentException("Preset not found");
        activePreset=find(name).name;
        save();
    }
    public SavedPreset find(String name){
        for(SavedPreset p:presets)if(p.name.equalsIgnoreCase(name))return p;
        return null;
    }
    public void delete(String name){
        presets.removeIf(p->p.name.equalsIgnoreCase(name));
        if(activePreset.equalsIgnoreCase(name))activePreset="";
        save();
    }
    public int capture(Minecraft mc,String name){
        if(mc.player==null || mc.level==null)throw new IllegalStateException("ต้องเข้าเกมก่อนบันทึก");
        if(name.isBlank() || name.length()>42 || name.contains("/") || name.contains("\\"))
            throw new IllegalArgumentException("กรุณาตั้งชื่อกล่อง 1–42 ตัวอักษร");
        SavedPreset target=find(name);
        if(target==null){
            target=new SavedPreset();target.name=name.trim();
            presets.add(target);
        }
        List<String> items=new ArrayList<>();
        List<String> labels=new ArrayList<>();
        RegistryOps<JsonElement> ops=RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
        List<ItemStack> unique=new ArrayList<>();
        for(int slot=0;slot<36;slot++){
            ItemStack stack=mc.player.getInventory().getItem(slot);
            if(stack.isEmpty())continue;
            if(unique.stream().anyMatch(i->ItemStack.isSameItemSameComponents(i,stack)))continue;
            JsonElement tree=ItemStack.CODEC.encodeStart(ops,stack).getOrThrow();
            items.add(GSON.toJson(tree));
            labels.add(stack.getHoverName().getString());
            unique.add(stack.copy());
        }
        if(items.isEmpty())throw new IllegalArgumentException("ไม่พบไอเทมในกระเป๋า");
        target.itemData=items;
        target.labels=labels;
        target.savedTime=System.currentTimeMillis();
        RuleConfig now=RuleConfig.load();
        target.rateRules=new ArrayList<>();
        for(RuleConfig.Rule rule:now.rules)target.rateRules.add(rule.copy());
        target.autoApplyRates=now.autoApplyRates;
        save();
        return items.size();
    }
    public static final class ReplayCheck{
        public final List<ItemStack> found=new ArrayList<>();
        public final List<String> missing=new ArrayList<>();
    }
    public ReplayCheck check(Minecraft mc,SavedPreset preset){
        ReplayCheck result=new ReplayCheck();
        if(mc.player==null || mc.level==null){result.missing.add("ไม่ได้อยู่ในโลก");return result;}
        RegistryOps<JsonElement> ops=RegistryOps.create(JsonOps.INSTANCE,mc.level.registryAccess());
        for(int i=0;i<preset.itemData.size();i++){
            ItemStack encoded;
            try{
                encoded=ItemStack.CODEC.parse(ops,JsonParser.parseString(preset.itemData.get(i))).getOrThrow();
            }catch(Exception e){
                result.missing.add("ข้อมูลไอเทมเก่าอ่านไม่ได้: "+label(preset,i));
                continue;
            }
            if(encoded.isEmpty())continue;
            boolean matches=false;
            for(int s=0;s<36;s++){
                ItemStack held=mc.player.getInventory().getItem(s);
                if(!held.isEmpty() && held.getCount()==encoded.getCount() &&
                    ItemStack.isSameItemSameComponents(held,encoded)){
                    matches=true;break;
                }
            }
            if(matches)result.found.add(encoded.copy());
            else result.missing.add(label(preset,i));
        }
        return result;
    }
    private static String label(SavedPreset p,int i){
        return p.labels!=null && i<p.labels.size() ? p.labels.get(i) : "ช่อง "+(i+1);
    }
    public void save(){
        if(!ServerProfiles.instance().hasSelection())return;
        try{
            Path file=file();
            Files.createDirectories(file.getParent());
            Path temp=file.resolveSibling("presets.json.tmp");
            Files.writeString(temp,GSON.toJson(this),StandardCharsets.UTF_8);
            if(Files.exists(file))Files.copy(file,file.resolveSibling("presets.json.bak"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){System.err.println("[CrateAutoReward] Could not save presets: "+e);}
    }
}
