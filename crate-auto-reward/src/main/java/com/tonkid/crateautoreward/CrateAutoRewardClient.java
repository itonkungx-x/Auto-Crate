package com.tonkid.crateautoreward;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;


public final class CrateAutoRewardClient implements ClientModInitializer {
    private static final int ANVIL_SLOT = 42;
    private enum Phase { OFF, NEXT, PICKUP, TYPE, WAIT_CREATE, RETURN, LOCATE_PAGE, WAIT_PAGE, OPTIONS, OPEN_WEIGHT, SET_WEIGHT, RETURN_OPTIONS, RETURN_LIST }
    private final ArrayDeque<ItemStack> pending = new ArrayDeque<>();
    private final List<ItemStack> seen = new ArrayList<>();
    private Phase phase = Phase.OFF;
    private int phaseTicks = 0, done = 0, skipped = 0, sourceSlot = -1;
    private ItemStack active = ItemStack.EMPTY;
    private KeyMapping toggle, emergency, settingsKey, presetKey;
    private RuleConfig config = RuleConfig.load();
    private RuleConfig.Rule currentRule;
    private boolean[] prior = new boolean[36];
    private boolean edited = false;
    private int cycles = 0;
    private int examinedPages = 0;
    private long pageFingerprint = 0;
    private String expectedRewardName = "";
    private String activeServerProfile = "";
    private boolean usingPreset = false;
    private int creationAttempts = 0;
    private Screen creationScreen = null;
    private long updaterTicks = 0;

    @Override public void onInitializeClient() {
        toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.crateautoreward.toggle", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F6, KeyMapping.Category.MISC));
        emergency = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.crateautoreward.stop", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F8, KeyMapping.Category.MISC));
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.crateautoreward.settings", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F7, KeyMapping.Category.MISC));
        presetKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.crateautoreward.presets", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_K, KeyMapping.Category.MISC));
        ScreenEvents.BEFORE_INIT.register((mc, screen, width, height) ->
            ScreenKeyboardEvents.beforeKeyPress(screen).register((s, key) -> {
                if (presetKey.matches(key)) openPresets(mc);
                else if (settingsKey.matches(key)) openSettings(mc);
                else if (emergency.matches(key)) stop(mc, "หยุดการทำงานแล้ว");
                else if (toggle.matches(key)) {
                    if (phase == Phase.OFF) start(mc);
                    else stop(mc, "หยุดด้วยปุ่มเริ่ม/หยุด");
                }
            })
        );
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while(presetKey.consumeClick()) openPresets(mc);
            while(settingsKey.consumeClick()) openSettings(mc);
            while(emergency.consumeClick()) if(phase!=Phase.OFF) stop(mc,"หยุดการทำงานแล้ว");
            while(toggle.consumeClick()) {
                if(phase==Phase.OFF) start(mc); else stop(mc,"หยุดการทำงานแล้ว");
            }
            ModUpdater.INSTANCE.tick(++updaterTicks);
            tick(mc);
        });
    }

    private void openSettings(Minecraft mc) {
        if(phase!=Phase.OFF){say(mc,"กรุณาหยุดการทำงานก่อนเปิดตั้งค่า");return;}
        config=RuleConfig.load();
        mc.gui.setScreen(new RulesScreen(mc.gui.screen(),config));
    }

    private void openPresets(Minecraft mc){
        if(phase!=Phase.OFF){
            say(mc,"กรุณาหยุดการทำงานก่อนเปิดกล่องที่เซฟไว้");return;
        }
        if(mc.player==null){return;}
        mc.gui.setScreen(new PresetScreen(mc.gui.screen()));
    }

    private void start(Minecraft mc) {
        if (!ServerProfiles.instance().hasSelection()) {
            say(mc,"ยังไม่ได้เลือกเซิร์ฟเวอร์ MMO/SMP โปรดเปิดหน้าตั้งค่าและเลือกก่อน");
            return;
        }
        config=RuleConfig.load();
        AbstractContainerMenu menu = menu(mc);
        if (mc.player == null || mc.gameMode == null || !rewards(mc.gui.screen()) || menu == null) {
            say(mc, "เปิด Crate Rewards ก่อน แล้วกดปุ่มเริ่ม");
            return;
        }
        if (menu.slots.size() <= ANVIL_SLOT || !menu.getSlot(ANVIL_SLOT).getItem().is(Items.ANVIL) ||
            !menu.getCarried().isEmpty()) {
            say(mc, "ไม่พบ Anvil ช่อง 42 หรือมีไอเทมติดเมาส์");
            return;
        }
        pending.clear();seen.clear();done=0;skipped=0;sourceSlot=-1;active=ItemStack.EMPTY;
        ClientPresets saves=ClientPresets.load();
        ClientPresets.SavedPreset preset=saves.selected();
        usingPreset=preset!=null;
        if(preset!=null){
            // Freeze the rules captured with this box, independent of later edits.
            if(preset.rateRules!=null && !preset.rateRules.isEmpty()){
                config.rules=new ArrayList<>();
                for(RuleConfig.Rule rule:preset.rateRules)config.rules.add(rule.copy());
                config.autoApplyRates=preset.autoApplyRates;
            }
            ClientPresets.ReplayCheck check=saves.check(mc,preset);
            if(!check.missing.isEmpty()){
                say(mc,"กล่อง '"+preset.name+"' ยังมีของไม่ครบ ขาด "+check.missing.size()+
                    " รายการ (เช่น "+check.missing.get(0)+") จึงไม่เริ่มอัตโนมัติ");
                return;
            }
            for(ItemStack stack:check.found)pending.addLast(stack.copy());
            say(mc,"เลือก Preset: "+preset.name+" | "+pending.size()+" ไอเทม");
        }else{
            for(Slot slot:menu.slots){
                ItemStack item=slot.getItem();
                if(slot.container!=mc.player.getInventory()||item.isEmpty())continue;
                if(seen.stream().anyMatch(old->ItemStack.isSameItemSameComponents(old,item)))skipped++;
                else{seen.add(item.copy());pending.addLast(item.copy());}
            }
        }
        if(pending.isEmpty()){say(mc,"ไม่มีไอเทมให้เพิ่มรางวัล");return;}
        activeServerProfile=ServerProfiles.instance().key();
        phase = Phase.NEXT; phaseTicks = 0;
        say(mc,"เริ่มดำเนินการ "+pending.size()+" ชนิด ("+skipped+" รายการซ้ำถูกข้าม) กดปุ่มหยุดเมื่อจำเป็น");
    }

    private void tick(Minecraft mc) {
        if (phase == Phase.OFF) return;
        if (!activeServerProfile.equals(ServerProfiles.instance().key())) {
            stop(mc,"ตรวจพบการเปลี่ยนเซิร์ฟเวอร์หรือโปรไฟล์ จึงหยุดเพื่อป้องกันข้อมูลคละกัน");
            return;
        }
        phaseTicks++;
        if (mc.player == null || mc.gameMode == null) { stop(mc,"ขาดการเชื่อมต่อ"); return; }
        switch(phase) {
            case NEXT -> next(mc);
            case PICKUP -> pickup(mc);
            case TYPE -> clickItemReward(mc);
            case WAIT_CREATE -> awaitCreation(mc);
            case RETURN -> returned(mc);
            case LOCATE_PAGE -> locatePage(mc);
            case WAIT_PAGE -> waitPage(mc);
            case OPTIONS -> options(mc);
            case OPEN_WEIGHT -> openWeight(mc);
            case SET_WEIGHT -> setWeight(mc);
            case RETURN_OPTIONS -> backOptions(mc);
            case RETURN_LIST -> backList(mc);
            default -> {}
        }
    }

    private void next(Minecraft mc) {
        if (phaseTicks < 12) return;
        AbstractContainerMenu menu = menu(mc);
        if (!rewards(mc.gui.screen()) || menu==null || !menu.getCarried().isEmpty()) {
            stop(mc,"เมนูเปลี่ยนหรือมีไอเทมติดเมาส์"); return;
        }
        if (menu.slots.size() <= ANVIL_SLOT || !menu.getSlot(ANVIL_SLOT).getItem().is(Items.ANVIL)) {
            stop(mc,"Anvil has changed. หยุดการทำงานแล้ว"); return;
        }
        if (pending.isEmpty()) { stop(mc,"เสร็จสิ้น "+done+" รางวัล | ข้าม "+skipped+" รายการซ้ำ"); return; }
        active=pending.peekFirst(); sourceSlot=-1;
        expectedRewardName=RuleConfig.normalize(active.getHoverName().getString());
        examinedPages=0;
        currentRule=config.match(active);
        for(int i=0;i<36;i++) prior[i]=!menu.getSlot(i).getItem().isEmpty();
        for (int i=0;i<menu.slots.size();i++) {
            Slot slot=menu.slots.get(i);
            if (slot.container==mc.player.getInventory() &&
                ItemStack.isSameItemSameComponents(slot.getItem(), active) &&
                (!usingPreset || slot.getItem().getCount()==active.getCount())) { sourceSlot=i; break; }
        }
        if (sourceSlot<0) {
            pending.removeFirst();skipped++;say(mc,"ไม่พบไอเทมต้นทาง จึงข้ามรายการนี้");phaseTicks=0;return;
        }
        clickSlot(mc,menu,sourceSlot);
        phase=Phase.PICKUP;phaseTicks=0;
    }

    private void pickup(Minecraft mc) {
        AbstractContainerMenu menu = menu(mc);
        if (!rewards(mc.gui.screen()) || menu==null) {
            stop(mc,"เมนูเปลี่ยนขณะหยิบไอเทม");return;
        }
        if (!menu.getCarried().isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(menu.getCarried(), active)) {
                stop(mc,"ไอเทมบนเมาส์ไม่ตรงกับรายการปัจจุบัน");return;
            }
            // Deliberately click ON the ANVIL, not an empty reward slot.
            clickSlot(mc,menu,ANVIL_SLOT);
            phase=Phase.TYPE;phaseTicks=0;creationAttempts=0;creationScreen=null;
        } else if (phaseTicks > 60) stop(mc,"หมดเวลารอการหยิบไอเทม");
    }

    private static boolean isCreationDialog(Screen screen) {
        if(screen==null)return false;
        String title=RuleConfig.normalize(screen.getTitle().getString());
        return title.contains("reward") && title.contains("creation");
    }

    private static List<AbstractWidget> availableWidgets(Screen screen){
        LinkedHashSet<AbstractWidget> all=new LinkedHashSet<>();
        for(var listener:screen.children()){
            if(listener instanceof AbstractWidget widget && widget.visible && widget.active)all.add(widget);
        }
        // Some versions of Minecraft DialogScreen register renderable controls
        // separately from input listeners. Read that list as a second source.
        try{
            Field field=Screen.class.getDeclaredField("renderables");
            field.setAccessible(true);
            Object renderables=field.get(screen);
            if(renderables instanceof Iterable<?> values){
                for(Object value:values){
                    if(value instanceof AbstractWidget widget && widget.visible && widget.active)all.add(widget);
                }
            }
        }catch(ReflectiveOperationException | SecurityException ignored){}
        return new ArrayList<>(all);
    }

    private static AbstractWidget findItemButton(Screen screen,List<AbstractWidget> controls){
        AbstractWidget exact=null;
        for(AbstractWidget w:controls){
            if(!(w instanceof Button))continue;
            String label=RuleConfig.normalize(w.getMessage().getString());
            if(label.contains("item reward")){
                if(exact!=null)return null; // Ambiguous buttons: do not guess.
                exact=w;
            }
        }
        if(exact!=null)return exact;

        // The two centered primary buttons are on the same row. Match geometry
        // rather than the text component (which may be part of a dialog wrapper).
        List<Button> buttons=new ArrayList<>();
        for(AbstractWidget w:controls){
            if(!(w instanceof Button b))continue;
            String label=RuleConfig.normalize(b.getMessage().getString());
            if(label.contains("back")||label.contains("กลับ")||label.contains("command reward"))continue;
            if(b.getX()<0||b.getX()+b.getWidth()>screen.width||
               b.getY()<screen.height/6||b.getY()>screen.height*3/4||b.getWidth()<70)continue;
            buttons.add(b);
        }
        // Only choose a left/right pair when the layout clearly provides it.
        for(Button a:buttons)for(Button b:buttons){
            if(a==b || Math.abs(a.getY()-b.getY())>6)continue;
            if(a.getX()+a.getWidth()<=b.getX()+12 && a.getX()<screen.width/2){
                return a;
            }
        }
        return null;
    }

    // Clicks through Minecraft's Screen API: no real mouse cursor is moved.
    private static boolean clickAt(Minecraft mc,Screen screen,double x,double y){
        MouseButtonInfo mouse=new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0);
        MouseButtonEvent event=new MouseButtonEvent(x,y,mouse);
        boolean accepted=screen.mouseClicked(event,false);
        if(mc.gui.screen()==screen)screen.mouseReleased(event);
        return accepted;
    }

    private static final double[][] CREATION_COORDINATES={
        // Client renderings of the same ExcellentCrates dialog place the left
        // action at either ~39% or ~28% of the GUI height.
        {0.38,0.39},{0.42,0.285},{0.38,0.36},{0.38,0.415},
        {0.42,0.30},{0.38,0.335},{0.42,0.265},{0.38,0.44}
    };

    private void clickItemReward(Minecraft mc){
        Screen screen=mc.gui.screen();
        if(screen==null){if(phaseTicks>60)stop(mc,"ไม่พบหน้าต่างสร้าง Reward");return;}
        if(rewards(screen)){ // Already returned by the server.
            phase=Phase.RETURN;phaseTicks=0;return;
        }
        if(!isCreationDialog(screen)){
            if(phaseTicks>110)stop(mc,"ไม่พบหน้า REWARD » Creation: "+screen.getClass().getSimpleName());
            return;
        }
        if(phaseTicks<8)return;
        creationScreen=screen;
        List<AbstractWidget> widgets=availableWidgets(screen);
        AbstractWidget target=findItemButton(screen,widgets);
        boolean accepted;
        if(target!=null && creationAttempts==0){
            accepted=clickAt(mc,screen,target.getX()+target.getWidth()/2.0,
                target.getY()+target.getHeight()/2.0);
            say(mc,"ทดลองกด Item Reward ตามตำแหน่งปุ่ม GUI");
        }else{
            int index=target!=null?creationAttempts-1:creationAttempts;
            if(index>=CREATION_COORDINATES.length){
                stop(mc,"ไม่พบปุ่ม Item Reward | ลองคลิกด้วยตนเองและแจ้งภาพหน้าจอ");return;
            }
            double[] xy=CREATION_COORDINATES[index];
            accepted=clickAt(mc,screen,screen.width*xy[0],screen.height*xy[1]);
            say(mc,"ทดลองกดปุ่ม Item Reward ("+(creationAttempts+1)+")");
        }
        creationAttempts++;
        // IMPORTANT: accepted from mouseClicked DOES NOT prove reward creation.
        // It may only mean a dialog container consumed the mouse event.
        phase=Phase.WAIT_CREATE;
        phaseTicks=0;
    }

    private void awaitCreation(Minecraft mc){
        Screen screen=mc.gui.screen();
        if(rewards(screen)){
            say(mc,"สร้าง Item Reward แล้ว หน้า Crate Rewards กลับมา");
            phase=Phase.RETURN;phaseTicks=0;return;
        }
        if(screen!=null && !isCreationDialog(screen) && phaseTicks>75){
            stop(mc,"หน้าจอเปลี่ยนแล้วแต่ไม่ใช่ Crate Rewards: "+screen.getClass().getSimpleName());
            return;
        }
        if(phaseTicks>36 && isCreationDialog(screen)){
            // Never claim success while the creation dialog is still visible.
            if(creationAttempts>=CREATION_COORDINATES.length+1){
                stop(mc,"ปุ่ม Item Reward ไม่ตอบสนอง กรุณากดด้วยตนเอง");return;
            }
            phase=Phase.TYPE;phaseTicks=8;
        }
        if(phaseTicks>160)stop(mc,"ส่งการคลิกแล้ว แต่ไม่พบหน้า Crate Rewards ภายในเวลาที่กำหนด");
    }

    private void returned(Minecraft mc) {
        if(phaseTicks>250){stop(mc,"เมนูรางวัลไม่กลับมา กรุณาตรวจสอบ");return;}
        if(!rewards(mc.gui.screen()) || phaseTicks<12)return;
        AbstractContainerMenu m=menu(mc);
        if(m==null)return;
        if(!m.getCarried().isEmpty()){
            if(!ItemStack.isSameItemSameComponents(m.getCarried(),active) ||
                sourceSlot<0 || sourceSlot>=m.slots.size() || !m.getSlot(sourceSlot).getItem().isEmpty()){
                stop(mc,"ไอเทมยังอยู่บนเมาส์ กรุณาคืนไอเทมก่อน");return;
            }
            clickSlot(mc,m,sourceSlot);phaseTicks=0;return;
        }
        if(config.autoApplyRates && currentRule!=null){
            phase=Phase.LOCATE_PAGE;phaseTicks=0;examinedPages=0;
            say(mc,"กำลังค้นหา Reward: "+active.getHoverName().getString());
            return;
        }
        complete(mc);
    }
    // ExcellentCrates keeps newly created rewards at the end of its paged list.
    private void locatePage(Minecraft mc){
        AbstractContainerMenu m=menu(mc);
        if(!rewards(mc.gui.screen()) || m==null || m.slots.size()<45){stop(mc,"เมนู Reward เปลี่ยนขณะกำลังค้นหา");return;}
        if(phaseTicks<12)return;
        if(examinedPages>200){stop(mc,"จำนวนหน้ารางวัลเกินขีดจำกัด");return;}
        if(m.getSlot(41).getItem().isEmpty()){selectNewest(mc,m);return;}
        pageFingerprint=fingerprint(m);
        clickSlot(mc,m,41);
        phase=Phase.WAIT_PAGE;phaseTicks=0;
    }
    private void waitPage(Minecraft mc){
        AbstractContainerMenu m=menu(mc);
        if(!rewards(mc.gui.screen()) || m==null){stop(mc,"เมนูเปลี่ยนขณะเลื่อนหน้า");return;}
        if(phaseTicks<14)return;
        if(fingerprint(m)!=pageFingerprint){
            examinedPages++;phase=Phase.LOCATE_PAGE;phaseTicks=0;return;
        }
        if(phaseTicks>=36)selectNewest(mc,m);
    }
    private static long fingerprint(AbstractContainerMenu m){
        long result=1469598103934665603L;
        for(int i=0;i<36;i++){
            ItemStack item=m.getSlot(i).getItem();
            result=31*result+(item.isEmpty()?0:item.getHoverName().getString().hashCode());
            result=31*result+(item.isEmpty()?0:item.getItem().toString().hashCode());
        }
        return result;
    }
    private void selectNewest(Minecraft mc,AbstractContainerMenu m){
        int slot=-1;
        for(int i=0;i<36;i++)if(!m.getSlot(i).getItem().isEmpty())slot=i;
        if(slot<0){stop(mc,"ไม่พบรางวัลหน้าสุดท้าย");return;}
        String last=RuleConfig.normalize(m.getSlot(slot).getItem().getHoverName().getString());
        if(!matchesName(last,expectedRewardName)){
            int found=-1;
            for(int i=0;i<36;i++){
                ItemStack item=m.getSlot(i).getItem();if(item.isEmpty())continue;
                String name=RuleConfig.normalize(item.getHoverName().getString());
                if(matchesName(name,expectedRewardName)){
                    if(found!=-1){stop(mc,"ชื่อรางวัลซ้ำ ไม่สามารถเลือกได้อย่างปลอดภัย");return;}
                    found=i;
                }
            }
            if(found<0){stop(mc,"ชื่อรางวัลใหม่ไม่ตรง: "+last+" / "+expectedRewardName);return;}
            slot=found;
        }
        say(mc,"พบ Reward หน้าที่ "+(examinedPages+1)+" ช่อง "+slot+" | "+currentRule.rarity+" / "+currentRule.weight);
        clickSlot(mc,m,slot);phase=Phase.OPTIONS;phaseTicks=0;
    }
    private static boolean matchesName(String shown,String expected){
        if(shown.isEmpty()||expected.isEmpty())return false;
        return shown.equals(expected)||shown.endsWith(expected)||expected.endsWith(shown);
    }
    private void options(Minecraft mc){
        if(phaseTicks>140){stop(mc,"ไม่พบ Reward Settings กรุณาตั้งเรทด้วยตนเอง");return;}
        AbstractContainerMenu m=menu(mc);
        if(m==null || m.slots.size()<54 || phaseTicks<7)return;
        if(!m.getSlot(12).getItem().is(Items.GLISTERING_MELON_SLICE)){
            if(phaseTicks>35)stop(mc,"ช่อง 12 ไม่ตรงกับเมนู Rarity & Weight");
            return;
        }
        clickSlot(mc,m,12);
        phase=Phase.OPEN_WEIGHT;phaseTicks=0;
    }
    private void openWeight(Minecraft mc){
        Screen screen=mc.gui.screen();
        if(phaseTicks>140){stop(mc,"ไม่พบหน้าตั้งค่า Rarity & Weight");return;}
        if(screen==null || screen instanceof AbstractContainerScreen<?> || phaseTicks<7)return;
        String title=RuleConfig.normalize(screen.getTitle().getString());
        if(!(title.contains("weight") || title.contains("rari")))return;
        edited=false;cycles=0;phase=Phase.SET_WEIGHT;phaseTicks=0;
    }
    // ExcellentCrates 7 / 26.3 renders the single-option selector as an ordinary
    // clickable button labelled "Rarity: COMMON", not necessarily CycleButton.
    // On each click the server dialog cycles to the next configured rarity.
    private void setWeight(Minecraft mc) {
        Screen screen=mc.gui.screen();
        if(screen==null){stop(mc,"หน้าตั้งค่าเรทปิดโดยไม่คาดคิด");return;}
        if(phaseTicks>220){stop(mc,"ตั้งเรทไม่สำเร็จ: หมดเวลารอหน้า Rarity & Weight");return;}
        String title=RuleConfig.normalize(screen.getTitle().getString());
        if(!title.contains("weight") && !title.contains("rari")){
            if(phaseTicks>25)stop(mc,"หน้าจอไม่ใช่ Rarity & Weight");
            return;
        }
        List<AbstractWidget> widgets=new ArrayList<>();
        for(var child:screen.children())
            if(child instanceof AbstractWidget w && w.visible && w.active)widgets.add(w);

        // First set exactly one numeric weight field.
        List<EditBox> fields=new ArrayList<>();
        for(AbstractWidget w:widgets)if(w instanceof EditBox e)fields.add(e);
        if(fields.size()!=1){
            if(phaseTicks>50)stop(mc,"ไม่พบช่อง Weight เพียงช่องเดียว จึงหยุดเพื่อความปลอดภัย");
            return;
        }
        EditBox weightInput=fields.get(0);
        if(!edited){
            weightInput.setValue(currentRule.weight);
            edited=true;phaseTicks=0;
            say(mc,"กรอก Weight: "+currentRule.weight);
            return;
        }
        if(!weightInput.getValue().equals(currentRule.weight)){
            stop(mc,"Weight ในหน้าต่างไม่ตรงกับกฎ กรุณาตรวจสอบ");return;
        }

        // Locate the label directly, e.g. "Rarity: COMMON" or "Rarity: ᴍʏᴛʜɪᴄ".
        // Only click an unambiguous selector; never blindly click a dialog area.
        AbstractWidget rarityButton=null;
        for(AbstractWidget w:widgets){
            if(w instanceof EditBox)continue;
            String label=RuleConfig.normalize(w.getMessage().getString());
            if(label.startsWith("rarity:") || label.startsWith("rarity ") ||
               label.startsWith("rarity：")){
                if(rarityButton!=null){stop(mc,"พบปุ่ม Rarity หลายปุ่ม กรุณาตรวจสอบ");return;}
                rarityButton=w;
            }
        }
        if(rarityButton==null){
            if(phaseTicks>45){
                StringBuilder labels=new StringBuilder();
                for(AbstractWidget w:widgets){
                    if(labels.length()>140)break;
                    labels.append(w.getClass().getSimpleName()).append(" [")
                        .append(w.getMessage().getString()).append("] ");
                }
                stop(mc,"ไม่พบปุ่ม Rarity: "+labels);
            }
            return;
        }
        String shown=RuleConfig.normalize(rarityButton.getMessage().getString());
        String current=rarityName(shown.substring(Math.min(shown.length(),shown.indexOf(":")>=0?shown.indexOf(":")+1:7)).trim());
        String desired=rarityName(currentRule.rarity);
        if(!current.equals(desired)){
            if(phaseTicks<9)return; // allow server/client to update the button label
            if(cycles>=24){
                stop(mc,"ไม่พบระดับ "+currentRule.rarity+" ในรายการ Rarity ของเซิร์ฟเวอร์");return;
            }
            String beforeClick=rarityButton.getMessage().getString();
            if(!clickWidget(mc,screen,rarityButton)){
                stop(mc,"คลิกปุ่ม Rarity ไม่สำเร็จ");return;
            }
            cycles++;
            phaseTicks=0;
            say(mc,"เปลี่ยน Rarity: "+beforeClick+" → เป้าหมาย "+currentRule.rarity+" ("+cycles+"/24)");
            return;
        }

        // A delay separates final selector input from dialog submission.
        if(phaseTicks<10)return;
        AbstractWidget confirm=null;
        for(AbstractWidget w:widgets){
            if(!(w instanceof Button) || w==rarityButton)continue;
            String label=RuleConfig.normalize(w.getMessage().getString()).trim();
            // The screenshot says "✔ Ok" rather than plain "Ok".
            boolean ok=label.equals("ok") || label.endsWith(" ok") ||
                label.equals("done") || label.endsWith(" done") ||
                label.contains("confirm") || label.contains("save") ||
                label.equals("ยืนยัน") || label.endsWith(" ยืนยัน");
            if(ok){
                if(confirm!=null){stop(mc,"พบปุ่มยืนยันซ้ำ กรุณาตรวจสอบ");return;}
                confirm=w;
            }
        }
        if(confirm==null){
            if(phaseTicks>55)stop(mc,"ไม่พบปุ่ม OK สำหรับบันทึกเรท");
            return;
        }
        if(!clickWidget(mc,screen,confirm)){
            stop(mc,"คลิกปุ่ม OK ไม่สำเร็จ กรุณาตรวจสอบ");return;
        }
        say(mc,"กด OK แล้ว | Rarity "+currentRule.rarity+" / Weight "+currentRule.weight);
        phase=Phase.RETURN_OPTIONS;phaseTicks=0;
    }

    private static String rarityName(String text){
        String value=RuleConfig.normalize(text).replaceAll("[^a-z0-9_]", "");
        // The display name MYTHIC in ExcellentCrates can correspond to a
        // user's category label MYTHICAL. Treat these as equivalent for matching.
        return value.equals("mythical") ? "mythic" : value;
    }

    private void backOptions(Minecraft mc){
        if(phaseTicks>140){stop(mc,"เมนู Reward Options ไม่กลับมา");return;}
        AbstractContainerMenu m=menu(mc);
        if(m==null || m.slots.size()<54 || phaseTicks<9)return;
        if(!m.getSlot(12).getItem().is(Items.GLISTERING_MELON_SLICE))return;
        // Verify both server-returned rate values before reporting success.
        ItemLore lore=m.getSlot(12).getItem().get(DataComponents.LORE);
        if(lore==null || lore.lines().isEmpty()){
            if(phaseTicks>60)stop(mc,"อ่านค่า Rarity & Weight ที่เซิร์ฟเวอร์บันทึกไม่ได้");
            return;
        }
        StringBuilder text=new StringBuilder();
        for(var line:lore.lines())text.append(RuleConfig.normalize(line.getString())).append(" ");
        String serverLore=text.toString();
        String wantedRarity=rarityName(currentRule.rarity);
        String formattedWeight=currentRule.weight;
        // Some users call the MYTHIC rarity MYTHICAL; the server label is MYTHIC.
        if(!serverLore.contains(wantedRarity) || !serverLore.contains(formattedWeight)){
            if(phaseTicks<40)return; // wait for the server refresh after clicking OK
            stop(mc,"เซิร์ฟเวอร์ยังไม่บันทึก Rarity/Weight ("+
                currentRule.rarity+" / "+currentRule.weight+"). ได้รับ: "+serverLore);
            return;
        }
        clickSlot(mc,m,49);phase=Phase.RETURN_LIST;phaseTicks=0;
    }
    private void backList(Minecraft mc){
        if(phaseTicks>140){stop(mc,"เมนู Crate Rewards ไม่กลับมาหลังตั้งค่า");return;}
        if(!rewards(mc.gui.screen()) || phaseTicks<12)return;
        complete(mc);
    }
    private void complete(Minecraft mc){
        pending.removeFirst();done++;
        active=ItemStack.EMPTY;sourceSlot=-1;currentRule=null;
        say(mc,"สร้างสำเร็จ "+done+" รายการ | เหลือ "+pending.size()+" รายการ");
        phase=Phase.NEXT;phaseTicks=0;
    }
    private static boolean clickWidget(Minecraft mc,Screen screen,AbstractWidget widget){
        MouseButtonInfo info=new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0);
        MouseButtonEvent event=new MouseButtonEvent(widget.getX()+widget.getWidth()/2.0,
            widget.getY()+widget.getHeight()/2.0,info);
        boolean handled=screen.mouseClicked(event,false);
        if(mc.gui.screen()==screen)screen.mouseReleased(event);
        if(!handled && widget instanceof Button b){
            b.onPress(info);
            handled=true;
        }
        return handled;
    }

    private static void clickSlot(Minecraft mc, AbstractContainerMenu menu, int slot) {
        mc.gameMode.handleContainerInput(menu.containerId,slot,0,ContainerInput.PICKUP,mc.player);
    }
    private static AbstractContainerMenu menu(Minecraft mc) {
        return mc.gui.screen() instanceof AbstractContainerScreen<?> screen?screen.getMenu():null;
    }
    private static boolean rewards(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> &&
            screen.getTitle().getString().toLowerCase(Locale.ROOT).contains("crate rewards");
    }
    private void stop(Minecraft mc,String reason) {
        phase=Phase.OFF;pending.clear();active=ItemStack.EMPTY;sourceSlot=-1;say(mc,reason);
    }
    private static void say(Minecraft mc,String msg) {
        if (mc.player!=null) mc.player.sendSystemMessage(Component.literal("[Crate Auto Reward / "+ServerProfiles.instance().selected()+"] "+msg));
    }
}
