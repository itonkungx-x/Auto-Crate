package com.tonkid.crateautoreward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Client-only, opt-in configurable release updater. Never replaces a JAR while Minecraft runs. */
public final class ModUpdater {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BASE = "https://api.github.com/repos/itonkungx-x/Auto-Crate/releases/latest";
    private static final String RELEASE_HOST = "https://github.com/itonkungx-x/Auto-Crate/releases/download/";
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("crateautoreward");
    private static final Path STAGE = ROOT.resolve("updates");
    private static final Path SETTINGS = ROOT.resolve("updater.json");
    private static final String ID = "crateautoreward";
    private static final String MC_VERSION = "26.3";

    public static final ModUpdater INSTANCE = new ModUpdater();
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(12)).build();
    private volatile String status = "ยังไม่ได้ตรวจสอบ";
    private volatile String latest = "";
    private volatile boolean checking = false;
    private volatile boolean ready = false;
    private volatile boolean updateExists = false;
    private volatile String preparedVersion = "";
    private volatile Path stageFile = null;
    private volatile String stageSha = "";
    private volatile boolean automatic = true;
    private volatile boolean startupChecked = false;
    private volatile boolean helperRegistered = false;
    private volatile String releaseUrl = "";
    private volatile String notes = "";
    private volatile String diagnostic = "";
    private ModUpdater(){load();}
    public String status(){return status;}
    public String current(){return FabricLoader.getInstance().getModContainer(ID)
        .map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("unknown");}
    public String latest(){return latest;}
    public boolean hasUpdate(){return updateExists;}
    public boolean isChecking(){return checking;}
    public boolean isReady(){return ready;}
    public boolean automatic(){return automatic;}
    public String releaseUrl(){return releaseUrl;}
    public String notes(){return notes;}
    public String diagnostic(){return diagnostic;}
    public void automatic(boolean value) {
        automatic=value;
        save();
        if(!value){
            ready=false;
            stageFile=null;
            status="ปิดการติดตั้งอัตโนมัติแล้ว (ยกเลิกการติดตั้งที่รออยู่)";
        }
        else if(updateExists && !ready && !checking){ check(true); }
    }
    public void tick(long ticks) {
        if(!startupChecked && ticks>160) {
            startupChecked=true;
            check(automatic);
        }
    }

    private void load(){
        try{
            if(Files.isRegularFile(SETTINGS)){
                JsonObject o=JsonParser.parseString(Files.readString(SETTINGS,StandardCharsets.UTF_8)).getAsJsonObject();
                if(o.has("autoInstall"))automatic=o.get("autoInstall").getAsBoolean();
            }
        }catch(Exception e){System.err.println("[AutoCrate Updater] Invalid updater config: "+e);}
    }
    private void save(){
        try{
            Files.createDirectories(ROOT);
            JsonObject o=new JsonObject();
            o.addProperty("autoInstall",automatic);
            Files.writeString(SETTINGS,GSON.toJson(o),StandardCharsets.UTF_8);
        }catch(Exception e){status="บันทึกการตั้งค่าไม่ได้: "+e.getMessage();}
    }
    public synchronized void check(boolean download){
        if(checking) return;
        checking=true;
        status="กำลังตรวจสอบ GitHub Releases...";
        CompletableFuture.runAsync(()->fetchRelease(download));
    }
    private void fetchRelease(boolean download) {
        try {
            String releaseJson=requestText(BASE,"application/vnd.github+json",256_000);
            JsonObject release=JsonParser.parseString(releaseJson).getAsJsonObject();
            String tag=release.get("tag_name").getAsString();
            String targetVersion=tag.startsWith("v")?tag.substring(1):tag;
            latest=targetVersion;
            releaseUrl=release.has("html_url")?release.get("html_url").getAsString():"";
            notes=release.has("body")&&!release.get("body").isJsonNull()?
                release.get("body").getAsString():"";
            if(compareVersions(targetVersion,current())<=0) {
                updateExists=false;ready=false;
                status="เป็นเวอร์ชันล่าสุดแล้ว ("+current()+")";
                return;
            }
            JsonArray assets=release.getAsJsonArray("assets");
            String expectedName="crate-auto-reward-26.3-"+targetVersion+".jar";
            String checksumName=expectedName+".sha256";
            String jarUrl=null,checksumUrl=null;
            for(JsonElement e:assets){
                JsonObject asset=e.getAsJsonObject();
                String name=asset.get("name").getAsString();
                String url=asset.get("browser_download_url").getAsString();
                if(!url.startsWith(RELEASE_HOST)) continue;
                if(name.equals(expectedName))jarUrl=url;
                else if(name.equals(checksumName))checksumUrl=url;
            }
            updateExists=true;
            if(jarUrl==null||checksumUrl==null) {
                status="พบ v"+targetVersion+" แต่ Release ไม่มีไฟล์ JAR/SHA256 ที่ตรงกัน";
                return;
            }
            if(ready && preparedVersion.equals(targetVersion)){
                status="ดาวน์โหลด v"+targetVersion+" แล้ว | ปิดเกมเพื่อติดตั้ง";
                return;
            }
            status="พบเวอร์ชันใหม่ v"+targetVersion;
            if(download) download(targetVersion,jarUrl,checksumUrl);
        }catch(Exception e){
            String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            diagnostic=message;
            if(message.contains("404")){
                status="GitHub ไม่อนุญาตให้เข้าถึง Release (รีโป Private หรือยังไม่มี Release)";
            }else{
                status="ตรวจอัปเดตไม่สำเร็จ: "+message;
            }
        }finally{checking=false;}
    }
    private String requestText(String link,String accept,int maxBytes)throws Exception {
        if(!link.startsWith("https://"))throw new IllegalArgumentException("Only HTTPS allowed");
        HttpRequest request=HttpRequest.newBuilder(URI.create(link))
            .header("Accept",accept).header("User-Agent","AutoCrate-Minecraft-Updater")
            .timeout(Duration.ofSeconds(35)).GET().build();
        HttpResponse<byte[]> response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());
        if(response.statusCode()!=200)throw new IllegalStateException("GitHub HTTP "+response.statusCode());
        if(response.body().length>maxBytes)throw new IllegalArgumentException("Response too large");
        return new String(response.body(),StandardCharsets.UTF_8);
    }
    private void download(String version,String jarUrl,String checksumUrl)throws Exception {
        String hashText=requestText(checksumUrl,"text/plain",1024).trim();
        String expectedHash=hashText.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if(!expectedHash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid SHA256 checksum");
        Files.createDirectories(STAGE);
        Path temp=STAGE.resolve("auto-crate-download.part");
        Path readyFile=STAGE.resolve("auto-crate-"+version+".jar.pending");
        try {
            HttpRequest request=HttpRequest.newBuilder(URI.create(jarUrl))
                .timeout(Duration.ofMinutes(2)).header("User-Agent","AutoCrate-Minecraft-Updater").GET().build();
            HttpResponse<InputStream> response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
            if(response.statusCode()!=200)throw new IllegalStateException("Download HTTP "+response.statusCode());
            try(InputStream in=response.body();var out=Files.newOutputStream(temp)){
                byte[] buf=new byte[8192];
                int n;long sum=0;
                while((n=in.read(buf))!=-1){
                    sum+=n;
                    if(sum>20_000_000L)throw new IllegalArgumentException("Mod larger than 20 MB");
                    out.write(buf,0,n);
                }
            }
            if(!hexHash(temp).equals(expectedHash))throw new IllegalStateException("SHA256 mismatch; update rejected");
            verifyJar(temp,version);
            Path currentJar=installedJar();
            Files.move(temp,readyFile,StandardCopyOption.REPLACE_EXISTING);
            stageSha=expectedHash;stageFile=readyFile;preparedVersion=version;ready=true;
            if(!helperRegistered){
                helperRegistered=true;
                Runtime.getRuntime().addShutdownHook(new Thread(this::startHelperOnExit,"auto-crate-update-shutdown"));
            }
            status="ดาวน์โหลดและตรวจ SHA256 สำเร็จ (v"+version+") | ปิดเกมเพื่อติดตั้ง";
        }finally{Files.deleteIfExists(temp);}
    }
    private static Path installedJar(){
        Path mods=FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
        Path jar=FabricLoader.getInstance().getModContainer(ID).orElseThrow()
            .getOrigin().getPaths().get(0).toAbsolutePath().normalize();
        if(!jar.getParent().equals(mods)||!Files.isRegularFile(jar)||!jar.getFileName().toString().endsWith(".jar"))
            throw new IllegalStateException("Place the installed mod JAR in the main mods folder");
        return jar;
    }
    static void verifyJar(Path jar,String version)throws Exception {
        try(ZipFile zip=new ZipFile(jar.toFile())){
            ZipEntry entry=zip.getEntry("fabric.mod.json");
            if(entry==null || entry.getSize()>100_000)throw new IllegalStateException("Missing fabric.mod.json");
            try(InputStream in=zip.getInputStream(entry)){
                JsonObject metadata=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                if(!ID.equals(metadata.get("id").getAsString())||
                    !version.equals(metadata.get("version").getAsString())||
                    !MC_VERSION.equals(metadata.getAsJsonObject("depends").get("minecraft").getAsString()))
                    throw new IllegalStateException("Unexpected Fabric mod identity/version");
            }
            if(zip.getEntry("com/tonkid/crateautoreward/CrateAutoRewardClient.class")==null)
                throw new IllegalStateException("Client entrypoint class missing");
        }
    }
    static String hexHash(Path input)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input));
        StringBuilder b=new StringBuilder();
        for(byte x:digest)b.append(String.format("%02x",x&0xFF));
        return b.toString();
    }
    private void startHelperOnExit(){
        try{
            if(!ready || stageFile==null || !Files.isRegularFile(stageFile))return;
            Path oldJar=installedJar();
            if(!hexHash(stageFile).equals(stageSha))return;
            Path helper=STAGE.resolve("auto-crate-helper.jar");
            Files.copy(oldJar,helper,StandardCopyOption.REPLACE_EXISTING);
            Path log=STAGE.resolve("updater-result.log");
            Path java=Path.of(System.getProperty("java.home"),"bin",
                System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")?"javaw.exe":"java");
            long gamePid=ProcessHandle.current().pid();
            new ProcessBuilder(java.toString(),"-cp",helper.toString(),
                "com.tonkid.crateautoreward.ModSwapHelper",String.valueOf(gamePid),
                oldJar.toString(),stageFile.toString(),stageSha,preparedVersion,log.toString(),
                "AutoCrateVerifiedUpdate")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
        }catch(Exception e){System.err.println("[AutoCrate Updater] Could not start helper: "+e);}
    }
    static int compareVersions(String left,String right){
        String[] a=left.split("[.\\-+]"),b=right.split("[.\\-+]");
        for(int i=0;i<Math.max(a.length,b.length);i++){
            int x=parse(a,i),y=parse(b,i);
            if(x!=y)return Integer.compare(x,y);
        }
        return 0;
    }
    private static int parse(String[] components,int i){
        if(i>=components.length)return 0;
        try{return Integer.parseInt(components[i]);}catch(NumberFormatException e){return 0;}
    }
}
