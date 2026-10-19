package com.tonkid.crateautoreward;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipFile;

/**
 * Standalone Java process, extracted/copied from a trusted installed mod JAR.
 * Does not depend on Minecraft/Fabric or any third-party library.
 */
public final class ModSwapHelper {
    private ModSwapHelper(){}
    public static void main(String[] args)throws Exception {
        if(args.length!=7)throw new IllegalArgumentException("Expected pid, old, pending, sha, version, log: seven arguments");
        long pid=Long.parseLong(args[0]);
        Path old=Path.of(args[1]).toAbsolutePath().normalize();
        Path pending=Path.of(args[2]).toAbsolutePath().normalize();
        String sha=args[3].toLowerCase(Locale.ROOT);
        String version=args[4];
        Path log=Path.of(args[5]).toAbsolutePath().normalize();
        // Last extra argument is required as an explicit fixed safety token.
        if(!"AutoCrateVerifiedUpdate".equals(args[6]))throw new SecurityException("Missing safety token");
        if(!sha.matches("[0-9a-f]{64}") || !version.matches("[0-9]+(\\.[0-9]+){1,3}"))
            throw new IllegalArgumentException("Unexpected checksum or version");
        if(!old.getFileName().toString().endsWith(".jar") ||
            !old.getParent().getFileName().toString().equalsIgnoreCase("mods") ||
            !pending.getFileName().toString().endsWith(".jar.pending"))
            throw new IllegalArgumentException("Unsafe update paths");

        long deadline=System.currentTimeMillis()+600_000;
        while(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)){
            if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Minecraft still running after 10 minutes");
            Thread.sleep(400);
        }
        if(!Files.isRegularFile(pending) || !Files.isRegularFile(old))throw new IllegalStateException("Source JAR missing");
        String actual=hash(pending);
        if(!actual.equals(sha))throw new SecurityException("Downloaded JAR checksum mismatch");
        try(ZipFile zip=new ZipFile(pending.toFile())){
            var entry=zip.getEntry("fabric.mod.json");
            if(entry==null)throw new IllegalStateException("Not a Fabric mod");
            if(!new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8).contains("\"crateautoreward\""))
                throw new IllegalStateException("Not the expected mod");
        }
        Path backup=old.resolveSibling(old.getFileName().toString()+".backup");
        Path staged=old.resolveSibling(old.getFileName().toString()+".updating");
        Files.copy(old,backup,StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.copy(pending,staged,StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(staged,old,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(IOException notAtomic){Files.move(staged,old,StandardCopyOption.REPLACE_EXISTING);}
            Files.deleteIfExists(pending);
            append(log,"Update to v"+version+" successful. Previous JAR preserved as "+backup);
        }catch(Exception e){
            if(Files.exists(backup))Files.copy(backup,old,StandardCopyOption.REPLACE_EXISTING);
            append(log,"Update FAILED: "+e);
            throw e;
        }finally{Files.deleteIfExists(staged);}
    }
    private static String hash(Path input)throws Exception{
        byte[] data=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input));
        StringBuilder b=new StringBuilder();
        for(byte value:data)b.append(String.format("%02x",value&0xFF));
        return b.toString();
    }
    private static void append(Path file,String line){
        try {
            Files.writeString(file,line+System.lineSeparator(),StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
        }catch(IOException ignored){}
    }
}
