package org.example;


import io.netty.channel.ChannelFuture;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.commons.lang3.RandomStringUtils;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.JdkLoggerFactory;
import reactor.netty.DisposableServer;


public abstract class FnServerBase {
    private static final Logger logger = Logger.getLogger(FnServerBase.class.getName());

    private final String path;

    static{
        try (InputStream is = FnServerBase.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        }catch(Exception e){
            e.printStackTrace();
        }
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE);
    }

	public FnServerBase() {
        path = Optional.ofNullable(System.getenv("FN_LISTENER")).orElse("/tmp/fnlsnr.sock").replace("unix:", "");
        logger.info("Listener path: " + path);
	}

    private void deleteFile(Path path){
        try{ 
            Files.delete(path);
        }catch(Exception e){}
    }

    protected void createSymbolicLink(Path link, Path target){
        try{
            final Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-rw-rw-");
            Files.setPosixFilePermissions(target, permissions);
            // symlinks must be relative within the unix socket directory
            Files.createSymbolicLink(link, Paths.get(target.getFileName().toString())); 
            Files.setPosixFilePermissions(target, permissions);
        }catch(Exception e){
            throw new RuntimeException("Couldn't link files", e);
        }
    }

    public void close(){
        deleteFile(Paths.get(path));
    }

    public void serve() throws Exception{

        final Path actual = Paths.get(path);

        final String prefix = RandomStringUtils.randomAlphanumeric(8);
        final Path phony = Paths.get(actual.getParent().toString(), prefix + "_" + actual.getFileName().toString());

        deleteFile(actual);
        deleteFile(phony);
        logger.info("Starting...");
        final Object obj = prepare(phony);
        addShutdownHook(obj);
        createSymbolicLink(actual, phony);
        logger.info("Server started.");
        run();
        logger.info("Server stopped.");
        close();
    }

    public void addShutdownHook(Object obj){
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try{
                logger.info("Shutdown...");
                if(obj instanceof ChannelFuture){
                    ((ChannelFuture)obj).channel().close().await();
                }else if(obj instanceof DisposableServer){
                    ((DisposableServer)obj).disposeNow();
                }
            }catch(Exception e){}
            close();
            Arrays.stream(logger.getHandlers()).forEach(handler -> handler.flush());
        }));
    }

    public abstract Object prepare(Path sock);
    public abstract void run();

}
