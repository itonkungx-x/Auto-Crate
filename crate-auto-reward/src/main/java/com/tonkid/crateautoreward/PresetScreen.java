package com.tonkid.crateautoreward;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class PresetScreen extends Screen {
    private final Screen parent;
    private ClientPresets store;
    private EditBox name;
    private int page=0;
    private String message="คัดลอกของจากกระเป๋ามาบันทึกเป็นชุด แล้วเลือกใช้กับ Crate Rewards";
    private boolean error=false;
    private String confirmation="";

    public PresetScreen(Screen parent){
        super(Component.literal("Crate Auto Reward | กล่อง Preset"));
        this.parent=parent;
        this.store=ClientPresets.load();
    }
    private int widthBox(){return Math.min(480,width-22);}
    private int left(){return (width-widthBox())/2;}
    private int top(){return Math.max(8,(height-360)/2);}
    private Button add(String label,int x,int y,int w,int h,Button.OnPress callback){
        return addRenderableWidget(Button.builder(Component.literal(label),callback).bounds(x,y,w,h).build());
    }
    @Override protected void init(){
        int x=left()+15,w=widthBox()-30,t=top();
        add(ServerProfiles.instance().selected().equals("MMO")?"[ MMO ]":"MMO",x+w-132,t+9,62,18,b->changeProfile("MMO"));
        add(ServerProfiles.instance().selected().equals("SMP")?"[ SMP ]":"SMP",x+w-65,t+9,62,18,b->changeProfile("SMP"));
        if(!ServerProfiles.instance().hasSelection()){
            add("เลือก MMO หรือ SMP ก่อนเริ่ม",x,t+100,w,24,b->changeProfile("SMP"));
            add("กลับ",x,t+313,w,22,b->onClose());
            return;
        }
        name=addRenderableWidget(new EditBox(font,x,t+69,w*55/100,20,Component.literal("ชื่อกล่อง")));
        name.setMaxLength(42);
        name.setValue("สีเหลือง");
        add("บันทึกจากกระเป๋า",x+w*56/100,t+69,w*44/100,20,b->capture());
        add("ใช้ไอเทมทั้งหมด (ไม่ใช้ Preset)",x,t+95,w,20,b->{
            store.select("");confirmation="";error=false;
            message="เลือกไอเทมทั้งหมดในกระเป๋า เมื่อเข้า Crate Rewards กดปุ่มเริ่มได้เลย";
            rebuildWidgets();
        });
        List<ClientPresets.SavedPreset> saved=store.list();
        int start=page*5;
        for(int row=0;row<5;row++){
            int idx=start+row;
            if(idx>=saved.size())break;
            ClientPresets.SavedPreset selected=saved.get(idx);
            boolean active=store.selected()!=null && store.selected().name.equals(selected.name);
            String label=(active?"✓ ":"")+" "+selected.name+"   ("+selected.itemData.size()+" รายการ)";
            if(label.length()>45)label=label.substring(0,44)+"…";
            add(label,x,t+123+row*26,w*66/100,23,b->select(selected.name));
            add("เซฟทับ",x+w*67/100,t+123+row*26,w*17/100-2,23,b->overwrite(selected.name));
            add("ลบ",x+w*85/100,t+123+row*26,w*15/100,23,b->delete(selected.name));
        }
        add("◀ ก่อนหน้า",x,t+260,w*30/100,19,b->{page=Math.max(0,page-1);rebuildWidgets();});
        add("ถัดไป ▶",x+w*31/100,t+260,w*30/100,19,b->{
            page=Math.min(Math.max(0,(store.list().size()-1)/5),page+1);rebuildWidgets();
        });
        add("ตรวจของ",x+w*62/100,t+260,w*38/100,19,b->check());
        add("กลับหน้าตั้งค่า",x,t+315,w*49/100,23,b->onClose());
        add("เสร็จสิ้น",x+w*50/100,t+315,w*50/100,23,b->{
            Minecraft.getInstance().gui.setScreen(null);
        });
    }
    private void changeProfile(String profile){
        ServerProfiles.instance().select(profile);
        store=ClientPresets.load();
        page=0;confirmation="";error=false;
        message="เปลี่ยนเป็น "+profile+" | ข้อมูลและเกณฑ์แยกจากอีกเซิร์ฟเวอร์";
        rebuildWidgets();
    }
    private void capture(){
        String chosen=name.getValue().trim();
        if(store.find(chosen)!=null){
            error=true;message="ชื่อซ้ำ! เลือกปุ่ม 'เซฟทับ' ของชุดนั้นแทน";return;
        }
        try{
            int total=store.capture(Minecraft.getInstance(),chosen);
            error=false;message="บันทึก "+chosen+" แล้ว "+total+" ไอเทม พร้อมข้อมูล Custom";
            page=Math.max(0,(store.list().size()-1)/5);
            rebuildWidgets();
        }catch(Exception e){error=true;message=e.getMessage();}
    }
    private void overwrite(String chosen){
        if(!confirmation.equals("overwrite:"+chosen)){
            confirmation="overwrite:"+chosen;
            error=true;message="กด 'เซฟทับ' อีกครั้งเพื่อแทนที่ "+chosen+" ด้วยของในกระเป๋าปัจจุบัน";
            return;
        }
        confirmation="";
        try{
            int total=store.capture(Minecraft.getInstance(),chosen);
            error=false;message="อัปเดต "+chosen+" แล้ว "+total+" ชิ้น";
            rebuildWidgets();
        }catch(Exception e){error=true;message=e.getMessage();}
    }
    private void select(String chosen){
        store.select(chosen);
        confirmation="";
        check();
        rebuildWidgets();
    }
    private void check(){
        ClientPresets.SavedPreset preset=store.selected();
        if(preset==null){error=false;message="กำลังใช้ทุกไอเทมในกระเป๋า";return;}
        ClientPresets.ReplayCheck check=store.check(Minecraft.getInstance(),preset);
        error=!check.missing.isEmpty();
        if(error){
            message="ขาด "+check.missing.size()+" ชิ้น: "+check.missing.get(0)+" | เริ่มอัตโนมัติไม่ได้";
        }else{
            message="พร้อม! "+preset.name+" มี "+check.found.size()+" ไอเทมตรงกับที่บันทึกไว้";
        }
    }
    private void delete(String chosen){
        if(!confirmation.equals("delete:"+chosen)){
            confirmation="delete:"+chosen;
            error=true;message="กด 'ลบ' อีกครั้งเพื่อยืนยันการลบ "+chosen;
            return;
        }
        store.delete(chosen);
        confirmation="";error=false;message="ลบ "+chosen+" แล้ว";
        page=Math.min(page,Math.max(0,(store.list().size()-1)/5));
        rebuildWidgets();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partial){
        int x=left(),t=top(),w=widthBox();
        g.fillGradient(0,0,width,height,0xF5151921,0xF1080B12);
        g.fill(x-2,t-2,x+w+2,t+360,0xFFD88938);
        g.fill(x,t,x+w,t+358,0xFF161C28);
        g.fillGradient(x,t,x+w,t+44,0xFF324158,0xFF1E2838);
        g.fill(x,t+43,x+w,t+45,0xFFFFAA55);
        g.fill(x+10,t+118,x+w-10,t+257,0xFF101623);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        super.extractRenderState(g,mouseX,mouseY,delta);
        int x=left()+15,t=top();
        g.text(font,"CRATE AUTO REWARD  |  SAVED BOXES",x,t+10,0xFFFFB86B,true);
        g.text(font,"เซิร์ฟเวอร์: "+ServerProfiles.address(),x,t+33,0xFF9CAEC3,false);
        g.text(font,"โปรไฟล์: "+ServerProfiles.instance().selected(),x,t+47,0xFFE5EAF3,false);
        if(ServerProfiles.instance().hasSelection()){
            g.text(font,"ชื่อชุด Preset",x,t+58,0xFFD4DDE8,false);
            g.text(font,"ชุดที่เลือก: "+store.selectedName(),x,t+289,0xFFFFC781,false);
        }
        String display=message;
        if(display.length()>75)display=display.substring(0,74)+"…";
        g.text(font,display,x,t+305,error?0xFFFF8989:0xFFB1E1B9,false);
        g.text(font,"ต้องมีไอเทมต้นฉบับอยู่ใน Inventory จึงจะใช้ชุดที่เซฟได้",x,t+343,0xFF9EACC0,false);
    }
    @Override public void onClose(){
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
