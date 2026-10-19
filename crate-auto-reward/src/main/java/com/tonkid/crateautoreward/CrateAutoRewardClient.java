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
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.dialog.DialogControlSet;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.SimpleDialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.client.gui.screens.dialog.DialogControlSet;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.ActionButton;
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

    /**
     * The actual action buttons in Minecraft 26.3 MultiButtonDialogScreen are
     * generated from MultiActionDialog.actions() and may not be exposed through
     * Screen.children(). Access the model, not the GUI's Back/Warning buttons.
     */
    private static MultiActionDialog creationDialog(Screen screen) {
        if (!(screen instanceof DialogScreen<?>)) return null;
        try {
            Field model=DialogScreen.class.getDeclaredField("dialog");
            model.setAccessible(true);
            Object value=model.get(screen);
            return value instanceof MultiActionDialog multi ? multi : null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private static String actionLabels(MultiActionDialog dialog){
        if(dialog==null) return "ไม่มีข้อมูล MultiActionDialog";
        StringBuilder info=new StringBuilder();
        for(ActionButton option:dialog.actions()) {
            if(info.length()>150)break;
            info.append("[").append(option.button().label().getString()).append("] ");
        }
        return info.toString();
    }

    private static ActionButton itemAction(MultiActionDialog dialog){
        ActionButton chosen=null;
        for(ActionButton option:dialog.actions()){
            String label=RuleConfig.normalize(option.button().label().getString());
            if(!label.contains("item reward"))continue;
            if(chosen!=null)return null;
            chosen=option;
        }
        return chosen;
    }

    private void clickItemReward(Minecraft mc){
        Screen screen=mc.gui.screen();
        if(screen==null){
            if(phaseTicks>70)stop(mc,"ไม่พบ REWARD Creation");
            return;
        }
        if(rewards(screen)){
            phase=Phase.RETURN;phaseTicks=0;return;
        }
        if(!isCreationDialog(screen)){
            if(phaseTicks>80)stop(mc,"หน้าจอไม่ใช่ REWARD Creation: "+screen.getClass().getSimpleName());
            return;
        }
        if(phaseTicks<10)return;

        MultiActionDialog dialog=creationDialog(screen);
        ActionButton target=dialog==null?null:itemAction(dialog);
        if(target==null){
            if(phaseTicks>30){
                say(mc,"ตัวเลือกใน Dialog: "+actionLabels(dialog));
                stop(mc,"ไม่พบ Action Item Reward ในข้อมูลของเซิร์ฟเวอร์ จึงหยุด");
            }
            return;
        }
        if(creationAttempts!=0) {
            stop(mc,"ส่ง Item Reward ไปแล้ว 1 ครั้ง ยังไม่ได้รับผลตอบกลับ (ไม่ส่งซ้ำเพื่อป้องกันรางวัลซ้ำ)");
            return;
        }
        try {
            // Vanilla uses this factory internally: the returned Button has an
            // action callback bound to DialogScreen.runAction / the server.
            DialogControlSet controls=new DialogControlSet((DialogScreen<?>)screen);
            Button button=controls.createActionButton(target).build();
            creationAttempts=1;
            creationScreen=screen;
            button.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));
            say(mc,"ส่ง Item Reward จาก MultiActionDialog แล้ว รอเซิร์ฟเวอร์ตอบกลับ...");
            phase=Phase.WAIT_CREATE;phaseTicks=0;
        }catch(Exception error){
            stop(mc,"เรียก Item Reward Action ไม่สำเร็จ: "+error.getClass().getSimpleName()+
                " "+(error.getMessage()==null?"":error.getMessage()));
        }
    }

    private void awaitCreation(Minecraft mc){
        Screen screen=mc.gui.screen();
        if(rewards(screen)){
            say(mc,"สร้าง Item Reward สำเร็จและกลับสู่ Crate Rewards");
            phase=Phase.RETURN;phaseTicks=0;return;
        }
        if(screen==null){
            if(phaseTicks>110)stop(mc,"Dialog ปิดแล้ว แต่ Crate Rewards ไม่เปิด");
            return;
        }
        if(!isCreationDialog(screen)){
            if(phaseTicks>110)stop(mc,"หน้าจอเปลี่ยนแต่ไม่ใช่ Crate Rewards: "+screen.getClass().getSimpleName());
            return;
        }
        if(phaseTicks>120){
            say(mc,"Action ที่ส่ง: "+actionLabels(creationDialog(screen)));
            stop(mc,"เซิร์ฟเวอร์ยังไม่ปิดหน้า Creation; หยุดโดยไม่กดซ้ำ");
        }
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
    /**
     * Minecraft 26.3 creates text and single-option dialog controls through
     * DialogControlSet. They can be NESTED inside layout/scroll containers,
     * so Screen.children() alone is not sufficient.
     */
    private static Dialog readDialog(Screen screen){
        if(!(screen instanceof DialogScreen<?>))return null;
        try{
            Field field=DialogScreen.class.getDeclaredField("dialog");
            field.setAccessible(true);
            Object model=field.get(screen);
            return model instanceof Dialog d ? d : null;
        }catch(ReflectiveOperationException | RuntimeException error){
            return null;
        }
    }

    private static String describeInputModel(Dialog dialog){
        if(dialog==null)return "ไม่พบข้อมูล Dialog";
        StringBuilder result=new StringBuilder();
        for(Input input:dialog.common().inputs()){
            result.append(input.key()).append(" = ")
                .append(input.control().getClass().getSimpleName()).append("; ");
        }
        return result.toString();
    }

    private static boolean matchesRarity(SingleOptionInput.Entry entry,String wanted){
        return rarityName(entry.id()).equals(wanted) ||
            rarityName(entry.displayOrDefault().getString()).equals(wanted);
    }

    /**
     * Server dialogs expose their actual Input definitions in
     * dialog.common().inputs(). Widget instances are often absent from
     * Screen.children(). Build a coordinated DialogControlSet from that model:
     * the field getters then travel WITH the selected OK ActionButton.
     */
    private void setWeight(Minecraft mc){
        Screen current=mc.gui.screen();
        if(current==null){stop(mc,"หน้าตั้งค่า Rarity/Weight ปิดไปแล้ว");return;}
        if(phaseTicks<12)return;
        if(phaseTicks>160){stop(mc,"รอตั้งค่า Rarity/Weight นานเกินไป");return;}
        String title=RuleConfig.normalize(current.getTitle().getString());
        if(!title.contains("weight") && !title.contains("rari")){
            if(phaseTicks>30)stop(mc,"หน้าจอไม่ตรงกับ Rarity & Weight");
            return;
        }

        Dialog model=readDialog(current);
        // ExcellentCrates RewardWeightDialog uses DialogTypes.multiAction(OK)
        // even though its body contains inputs. It is NOT a SimpleDialog.
        if(!(model instanceof MultiActionDialog form) ||
           !(current instanceof DialogScreen<?> dialogScreen)){
            say(mc,"ชนิด Dialog: "+(model==null?"ไม่มี":model.getClass().getSimpleName()));
            stop(mc,"ไม่พบ MultiActionDialog Rarity & Weight ของ ExcellentCrates");
            return;
        }

        // Inspect the model before ANY interaction. Only support the specific
        // ExcellentCrates form with one Rarity selector and one Weight input.
        Input rarityInput=null,weightInput=null;
        for(Input input:model.common().inputs()){
            String label=input.control() instanceof TextInput t ? t.label().getString() :
                input.control() instanceof SingleOptionInput o ? o.label().getString() : "";
            String name=RuleConfig.normalize(input.key()+" "+label);
            if(input.control() instanceof SingleOptionInput &&
                (name.contains("rarity") || name.contains("rari"))){
                if(rarityInput!=null){stop(mc,"มีช่อง Rarity มากกว่า 1 ช่อง");return;}
                rarityInput=input;
            }else if(input.control() instanceof TextInput &&
                (name.contains("weight") || name.contains("น้ำหนัก"))){
                if(weightInput!=null){stop(mc,"มีช่อง Weight มากกว่า 1 ช่อง");return;}
                weightInput=input;
            }
        }
        if(rarityInput==null || weightInput==null || model.common().inputs().size()!=2){
            say(mc,"Input diagnostic: "+describeInputModel(model));
            stop(mc,"ไม่พบ Rarity+Weight อย่างละหนึ่งช่องในข้อมูล Dialog");
            return;
        }
        SingleOptionInput options=(SingleOptionInput)rarityInput.control();
        String desired=rarityName(currentRule.rarity);
        int target=-1;
        int initial=0;
        for(int i=0;i<options.entries().size();i++){
            SingleOptionInput.Entry entry=options.entries().get(i);
            if(entry.initial())initial=i;
            if(matchesRarity(entry,desired)){
                if(target!=-1){stop(mc,"พบตัวเลือก Rarity ซ้ำ: "+currentRule.rarity);return;}
                target=i;
            }
        }
        if(target<0){
            say(mc,"ระดับที่มี: "+options.entries().stream()
                .map(e->e.id()).toList());
            stop(mc,"เซิร์ฟเวอร์ไม่มี Rarity "+currentRule.rarity);
            return;
        }
        if(options.entries().size()>30){stop(mc,"จำนวนตัวเลือก Rarity มากผิดปกติ");return;}

        // IMPORTANT: use ONE DialogControlSet for both inputs AND the OK
        // action. Its valueGetters map contains the chosen rarity and weight.
        DialogControlSet formControls=new DialogControlSet(dialogScreen);
        java.util.Map<String,List<AbstractWidget>> builtWidgets=new java.util.LinkedHashMap<>();
        for(Input input:model.common().inputs()){
            List<AbstractWidget> built=new ArrayList<>();
            formControls.addInput(input,(LayoutElement element)->element.visitWidgets(built::add));
            builtWidgets.put(input.key(),built);
        }

        List<EditBox> textboxes=new ArrayList<>();
        for(AbstractWidget widget:builtWidgets.get(weightInput.key()))
            if(widget instanceof EditBox box)textboxes.add(box);
        if(textboxes.size()!=1){
            say(mc,"Input diagnostic: "+describeInputModel(model));
            stop(mc,"สร้างช่อง Weight จาก TextInput ไม่สำเร็จ");
            return;
        }
        EditBox field=textboxes.get(0);
        field.setValue(currentRule.weight);
        if(!field.getValue().equals(currentRule.weight)){
            stop(mc,"กรอก Weight ไม่สำเร็จ: "+currentRule.weight);
            return;
        }

        // SingleOption creates a native cycle button. It begins at the
        // server-declared initial entry; cycle only as many times as required.
        List<net.minecraft.client.gui.components.AbstractButton> chooser=new ArrayList<>();
        for(AbstractWidget widget:builtWidgets.get(rarityInput.key()))
            if(widget instanceof net.minecraft.client.gui.components.AbstractButton button)chooser.add(button);
        if(chooser.size()!=1){
            say(mc,"Input diagnostic: "+describeInputModel(model));
            stop(mc,"สร้างปุ่ม Rarity จาก SingleOption ไม่สำเร็จ");
            return;
        }
        net.minecraft.client.gui.components.AbstractButton selector=chooser.get(0);
        int steps=(target-initial+options.entries().size())%options.entries().size();
        for(int n=0;n<steps;n++)selector.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));

        // Confirm via ActionButton; it binds all registered input value getters.
        ActionButton submit=null;
        for(ActionButton choice:form.actions()){
            String label=RuleConfig.normalize(choice.button().label().getString()).trim();
            if(label.equals("ok") || label.endsWith(" ok") ||
                label.contains("confirm") || label.equals("done") ||
                label.contains("ยืนยัน") || label.equals("save")){
                if(submit!=null){stop(mc,"มีปุ่ม OK มากกว่า 1 ปุ่ม");return;}
                submit=choice;
            }
        }
        if(submit==null && form.actions().size()==1)submit=form.actions().get(0);
        if(submit==null){
            stop(mc,"หา ActionButton ยืนยัน Rarity/Weight ไม่พบ");
            return;
        }
        try{
            Button ok=formControls.createActionButton(submit).build();
            ok.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));
            say(mc,"ส่งข้อมูล Rarity="+options.entries().get(target).id()+
                " Weight="+currentRule.weight+" ผ่าน MultiActionDialog ปุ่ม OK");
            phase=Phase.RETURN_OPTIONS;
            phaseTicks=0;
        }catch(Exception error){
            stop(mc,"ส่ง Rarity/Weight ไม่สำเร็จ: "+error.getClass().getSimpleName());
        }
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
