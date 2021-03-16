package org.example.reactor;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.FnServerBase;

import io.netty.channel.unix.DomainSocketAddress;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

public class FnServer extends FnServerBase{

    private static final Logger logger = Logger.getLogger(FnServer.class.getName());

    private DisposableServer server;

    @Override
    public Runnable prepare(Path sock){
        server =
        HttpServer.create()   // Prepares an HTTP server ready for configuration
        .bindAddress(() -> new DomainSocketAddress(sock.toString()))
        .route(routes ->
                routes.post("/call", new SnoopHandler())
        )
        .bindNow(); // Starts the server in a blocking fashion, and waits for it to finish its initialization
        return () -> server.disposeNow();
    }

    @Override
    public void run(){
        server.onDispose().block();
    }
    
    public static void main(String[] args){
        try{
            new FnServer().serve();
        }catch(Exception e){
            logger.log(Level.SEVERE, "Terminated abnormally: " + e.getMessage(), e);
        }
    }

}
