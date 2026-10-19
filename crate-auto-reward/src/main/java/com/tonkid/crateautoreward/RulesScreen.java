package com.tonkid.crateautoreward;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import java.util.Collections;

public final class RulesScreen extends Screen {
    private static final int ORANGE=0xFFFFA84E, GOLD=0xFFFFCA78, WHITE=0xFFF3F4F6;
    private final Screen parent;
    private final RuleConfig config;
    private EditBox pattern,rarity,weight;
    private int page=0,selected=-1;
    private String source="NAME", message="เลือกกฎเพื่อแก้ไข หรือกรอกข้อมูลเพื่อเพิ่มกฎใหม่";
    private boolean error=false;

    public RulesScreen(Screen parent,RuleConfig config){
        super(Component.literal("Crate Auto Reward | ตั้งค่ากฎ"));
        this.parent=parent;this.config=config;
    }
    private int left(){return (this.width-Math.min(480,this.width-22))/2;}
    private int wide(){return Math.min(480,this.width-22);}
    private int top(){return Math.max(8,(this.height-378)/2);}
    private int contentWidth(){return wide()-32;}
    private static Component txt(String s){return Component.literal(s);}
    private Button btn(String s,int x,int y,int w,int h,Button.OnPress handler){
        return this.addRenderableWidget(Button.builder(txt(s),handler).bounds(x,y,w,h).build());
    }
    @Override protected void init(){
        int x=left()+16,t=top(),w=contentWidth();
        btn(ServerProfiles.instance().selected().equals("MMO")?"[ MMO ]":"MMO",
            x+w-128,t+8,60,20,b->setProfile("MMO"));
        btn(ServerProfiles.instance().selected().equals("SMP")?"[ SMP ]":"SMP",
            x+w-64,t+8,60,20,b->setProfile("SMP"));
        pattern=this.addRenderableWidget(new EditBox(this.font,x,t+79,w*47/100-6,20,txt("คำที่ต้องการค้นหา")));
        rarity=this.addRenderableWidget(new EditBox(this.font,x+w*47/100,t+79,w*29/100-6,20,txt("Rarity")));
        weight=this.addRenderableWidget(new EditBox(this.font,x+w*76/100,t+79,w*24/100,20,txt("Weight")));
        pattern.setMaxLength(96);
        rarity.setMaxLength(40);
        weight.setMaxLength(6);
        pattern.setValue("STAFF");rarity.setValue("MYTHICAL");weight.setValue("0.01");
        String sourceLabel=source.equals("NAME")?"ชื่อไอเทม":source.equals("LORE")?"คำอธิบาย":"ประเภทวัตถุ";
        btn("ค้นจาก: "+sourceLabel,x,t+105,w*37/100,20,b->{
            String p=pattern.getValue(),r=rarity.getValue(),n=weight.getValue();
            source=source.equals("NAME")?"LORE":source.equals("LORE")?"MATERIAL":"NAME";
            rebuildWidgets();pattern.setValue(p);rarity.setValue(r);weight.setValue(n);
        });
        btn(selected<0?"+ เพิ่มกฎ":"บันทึกการแก้ไข",x+w*37/100+5,t+105,w*36/100-5,20,b->save());
        btn("ลบกฎ",x+w*74/100+5,t+105,w*26/100-5,20,b->{
            if(selected>=0 && selected<config.rules.size()){
                config.rules.remove(selected);selected=-1;
                page=Math.min(page,Math.max(0,(config.rules.size()-1)/5));
                config.save();error=false;message="ลบกฎแล้ว";rebuildWidgets();
            }else{message="กรุณาเลือกกฎที่ต้องการลบ";error=true;}
        });
        btn("↑",x,t+132,33,20,b->shift(-1));
        btn("↓",x+38,t+132,33,20,b->shift(1));
        btn("ยกเลิกการเลือก",x+76,t+132,w-76,20,b->{
            selected=-1;source="NAME";error=false;message="พร้อมเพิ่มกฎใหม่";rebuildWidgets();
        });
        int offset=page*5;
        for(int row=0;row<5;row++){
            int idx=offset+row;
            if(idx>=config.rules.size()) break;
            RuleConfig.Rule rule=config.rules.get(idx);
            String label=String.format("%02d  %s  →  %s  /  %s",idx+1,rule.contains,rule.rarity,rule.weight);
            if(label.length()>59)label=label.substring(0,58)+"…";
            final int chosen=idx;
            btn((selected==idx?"▶ ":"   ")+label,x,t+160+row*23,w,21,b->{
                selected=chosen;source=rule.source;
                error=false;message="แก้ไขกฎลำดับ "+(chosen+1)+" • กฎที่อยู่บนสุดสำคัญกว่า";
                rebuildWidgets();
                pattern.setValue(rule.contains);
                rarity.setValue(rule.rarity);
                weight.setValue(rule.weight);
            });
        }
        btn("◀ ย้อนหน้า",x,t+280,w*27/100,20,b->{page=Math.max(0,page-1);selected=-1;rebuildWidgets();});
        btn("หน้าถัดไป ▶",x+w*28/100,t+280,w*27/100,20,b->{
            page=Math.min(Math.max(0,(config.rules.size()-1)/5),page+1);
            selected=-1;rebuildWidgets();
        });
        btn("คืนค่าเริ่มต้น",x+w*56/100,t+280,w*44/100,20,b->{
            config.rules=RuleConfig.defaults().rules;config.save();
            selected=-1;page=0;error=false;message="คืนค่าเริ่มต้นแล้ว";rebuildWidgets();
        });
        btn((config.autoApplyRates?"✓ ":"✕ ")+"เรท: "+(config.autoApplyRates?"เปิด":"ปิด"),
            x,t+311,w*24/100,22,b->{config.autoApplyRates=!config.autoApplyRates;config.save();rebuildWidgets();});
        btn("กล่องที่เซฟ",x+w*25/100,t+311,w*25/100-4,22,b->{
            config.save();
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null){
                error=true;message="ต้องเข้าเซิร์ฟเวอร์ก่อนเปิดกล่อง Preset";return;
            }
            mc.gui.setScreen(new PresetScreen(this));
        });
        btn("ตั้งปุ่มลัด",x+w*50/100,t+311,w*25/100-4,22,b->{
            config.save();
            Minecraft mc=Minecraft.getInstance();
            mc.gui.setScreen(new KeyBindsScreen(this,mc.options));
        });
        btn("อัปเดตม็อด",x+w*75/100,t+311,w*25/100,22,b->
            Minecraft.getInstance().gui.setScreen(new UpdaterScreen(this)));
        btn("เสร็จสิ้น",x,t+337,w,22,b->onClose());
    }
    private void setProfile(String mode){
        ServerProfiles.instance().select(mode);
        Minecraft.getInstance().gui.setScreen(new RulesScreen(parent,RuleConfig.load()));
    }
    private void shift(int direction){
        if(selected<0 || selected>=config.rules.size()) {error=true;message="เลือกกฎก่อนจัดลำดับ";return;}
        int target=selected+direction;
        if(target<0 || target>=config.rules.size())return;
        Collections.swap(config.rules,selected,target);
        selected=target;page=target/5;config.save();
        error=false;message="จัดลำดับแล้ว บรรทัดบนสุดจะถูกตรวจสอบก่อน";
        rebuildWidgets();
    }
    private void save(){
        if(!ServerProfiles.instance().hasSelection()){
            error=true;message="กรุณาเลือก MMO หรือ SMP ก่อนบันทึกกฎ";return;
        }
        String p=pattern.getValue().trim(),r=rarity.getValue().trim(),v=weight.getValue().trim();
        if(p.isEmpty() || r.isEmpty() || !RuleConfig.validWeight(v)){
            error=true;message="กรอกคำค้นหา Rarity และ Weight เป็นตัวเลข (เช่น 0.01)";return;
        }
        RuleConfig.Rule rule=new RuleConfig.Rule(source,p,r,v);
        if(selected>=0 && selected<config.rules.size())config.rules.set(selected,rule);
        else{config.rules.add(rule);selected=config.rules.size()-1;page=selected/5;}
        config.save();
        error=false;message="บันทึกแล้ว: "+rule.contains+" → "+rule.rarity+" / "+rule.weight;
        rebuildWidgets();
        pattern.setValue(p);rarity.setValue(r);weight.setValue(v);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt){
        int x=left(),t=top(),w=wide();
        g.fillGradient(0,0,width,height,0xF2151925,0xF2080B12);
        g.fill(x-2,t-2,x+w+2,t+378,0xFFED9843);
        g.fill(x,t,x+w,t+376,0xFF151A24);
        g.fillGradient(x,t,x+w,t+47,0xFF303A4C,0xFF222A37);
        g.fill(x,t+46,x+w,t+48,0xFFFFA84E);
        g.fill(x+10,t+154,x+w-10,t+278,0xFF0D121B);
        g.fill(x+10,t+309,x+w-10,t+310,0xFF38414E);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        super.extractRenderState(g,mx,my,dt);
        int x=left()+16,t=top(),w=contentWidth();
        g.text(font,"CRATE AUTO REWARD  •  26.3",x,t+11,ORANGE,true);
        g.text(font,"โปรไฟล์: "+ServerProfiles.instance().selected()+" | "+ServerProfiles.address(),x,t+30,WHITE,false);
        g.text(font,"ชื่อ / Lore / Material",x,t+65,0xFFCDD5E0,false);
        g.text(font,"Rarity ID",x+w*47/100,t+65,0xFFCDD5E0,false);
        g.text(font,"Weight",x+w*76/100,t+65,0xFFCDD5E0,false);
        g.text(font,"รายการกฎ — เรียงจากสำคัญมากไปน้อย",x,t+144,GOLD,false);
        g.text(font,"หน้า "+(page+1)+" / "+Math.max(1,(config.rules.size()+4)/5),x,t+302,0xFF92A2B6,false);
        g.text(font,message,x,t+363,error?0xFFFF7878:0xFFABD2A2,false);
    }
    @Override public void onClose(){
        config.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
