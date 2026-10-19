package com.tonkid.crateautoreward;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class UpdaterScreen extends Screen {
    private final Screen parent;
    private final ModUpdater updater=ModUpdater.INSTANCE;
    public UpdaterScreen(Screen parent){
        super(Component.literal("Auto-Crate | ระบบอัปเดต"));
        this.parent=parent;
    }
    private int widthBox(){return Math.min(440,width-22);}
    private int left(){return (width-widthBox())/2;}
    private int top(){return Math.max(8,(height-264)/2);}
    private void add(String label,int x,int y,int w,int h,Button.OnPress listener){
        addRenderableWidget(Button.builder(Component.literal(label),listener).bounds(x,y,w,h).build());
    }
    @Override protected void init(){
        int x=left()+14,w=widthBox()-28,t=top();
        add("ตรวจสอบเวอร์ชันล่าสุด",x,t+118,w,23,b->updater.check(false));
        add("ดาวน์โหลดเพื่ออัปเดตเมื่อปิดเกม",x,t+146,w,23,b->updater.check(true));
        add("ติดตั้งอัตโนมัติ: "+(updater.automatic()?"เปิด":"ปิด"),
            x,t+175,w,23,b->{updater.automatic(!updater.automatic());rebuildWidgets();});
        add("ย้อนกลับ",x,t+210,w,23,b->onClose());
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partial){
        int x=left(),t=top(),w=widthBox();
        g.fillGradient(0,0,width,height,0xE9121520,0xF1090E18);
        g.fill(x-2,t-2,x+w+2,t+262,0xFFFFA95C);
        g.fill(x,t,x+w,t+260,0xFF1A2230);
        g.fillGradient(x,t,x+w,t+40,0xFF34465C,0xFF222F42);
        g.fill(x,t+39,x+w,t+41,0xFFFFA95C);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        super.extractRenderState(g,mx,my,dt);
        int x=left()+14,t=top();
        g.text(font,"AUTO-CRATE  |  UPDATER",x,t+12,0xFFFFC176,true);
        g.text(font,"ติดตั้ง: v"+updater.current(),x,t+53,0xFFF4F7FA,false);
        g.text(font,"ล่าสุด: "+(updater.latest().isEmpty()?"ยังไม่ทราบ":"v"+updater.latest()),
            x,t+69,0xFFCFD8E8,false);
        String message=updater.status();
        if(message.length()>58) message=message.substring(0,57)+"...";
        g.text(font,message,x,t+94,updater.isReady()?0xFF9DE9B0:0xFFFFC176,false);
        if(updater.isReady()){
            g.text(font,"ไฟล์พร้อมแล้ว ปิด Minecraft เพื่ออัปเดต",x,t+106,0xFF9DE9B0,false);
        }else{
            g.text(font,"แหล่งข้อมูล: GitHub Releases / ตรวจ SHA-256",x,t+106,0xFFA8B6CA,false);
        }
        g.text(font,"ข้อมูล MMO/SMP และ Preset จะไม่ถูกลบ",x,t+242,0xFF9EAEC3,false);
    }
    @Override public void onClose(){
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
