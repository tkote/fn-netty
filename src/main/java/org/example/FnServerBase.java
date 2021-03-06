package org.example;


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


public abstract class FnServerBase {
    private static final Logger logger = Logger.getLogger(FnServerBase.class.getName());

    private final String listener;

    static{
        try (InputStream is = FnServerBase.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        }catch(Exception e){
            e.printStackTrace();
        }
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE);
    }

	public FnServerBase() {
        listener = Optional.ofNullable(System.getenv("FN_LISTENER")).orElse("/tmp/fnlsnr.sock").replace("unix:", "");
        logger.info("Listener path: " + listener);
	}

    private void deleteFile(Path path){
        try{ 
            Files.delete(path);
        }catch(Exception e){}
    }

    private void createSymbolicLink(Path link, Path target){
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

    private void clean(){
        deleteFile(Paths.get(listener));
    }

    private void addShutdownHook(Runnable runnable){
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown...");
            runnable.run();
            clean();
            Arrays.stream(logger.getHandlers()).forEach(handler -> handler.flush());
        }));
    }

    public void serve() throws Exception{

        final Path actual = Paths.get(listener);
        final String prefix = RandomStringUtils.randomAlphanumeric(8);
        final Path phony = Paths.get(actual.getParent().toString(), prefix + "_" + actual.getFileName().toString());

        deleteFile(actual);
        deleteFile(phony);
        try{
            logger.info("Starting...");
            final Runnable runnable = prepare(phony);
            addShutdownHook(runnable);
            createSymbolicLink(actual, phony);
            logger.info("Server started.");
            run();
            logger.info("Server stopped.");
        }finally{
            clean();
        }
    }

    public abstract Runnable prepare(Path sock);
    public abstract void run();

}
