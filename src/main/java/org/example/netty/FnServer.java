package org.example.netty;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;

import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.FnServerBase;

import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;


public class FnServer extends FnServerBase{

    private static final Logger logger = Logger.getLogger(FnServer.class.getName());

    private final EventLoopGroup bossGroup = new EpollEventLoopGroup(); 
    private final EventLoopGroup workerGroup = new EpollEventLoopGroup();
    private ChannelFuture channelFuture;

    @Override
    public Runnable prepare(Path sock){

        final ServerBootstrap b = new ServerBootstrap(); 
                                    
        b.group(bossGroup, workerGroup)
            //.option(ChannelOption.SO_BACKLOG, 4096)
            //    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .channel(EpollServerDomainSocketChannel.class)
            .handler(new LoggingHandler(LogLevel.DEBUG))
            .childHandler(new ChannelInitializer<Channel>() { 
                @Override
                public void initChannel(Channel ch) throws Exception {
                    logger.fine("initChannel()");
                    ch.pipeline()
                    .addLast("logger", new LoggingHandler(LogLevel.DEBUG))
                    .addLast(new HttpRequestDecoder())
                    // Uncomment the following line if you don't want to handle HttpChunks.
                    //.addLast(new HttpObjectAggregator(1048576));
                    .addLast(new HttpResponseEncoder())
                    // Remove the following line if you don't want automatic content compression.
                    //.addLast(new HttpContentCompressor());
                    .addLast(new HttpSnoopServerHandler());
                }
            })
            .option(ChannelOption.SO_BACKLOG, 4096)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Integer.MAX_VALUE);

        final SocketAddress s = new DomainSocketAddress(sock.toString());
        channelFuture = b.bind(s);
        channelFuture.awaitUninterruptibly();
        assert channelFuture.isDone();
        if (channelFuture.isCancelled()) {
            throw new RuntimeException("Bind was cancelled.");
        } else if (!channelFuture.isSuccess()) {
            final Throwable t = channelFuture.cause();
            throw new RuntimeException("Couldn't bind socket: " + t.getMessage(), t);
        }
        return () -> {
            try{
                channelFuture.channel().close().await();
            }catch(Exception e){}
        };
    }

    @Override
    public void run(){
        try{
            channelFuture.channel().closeFuture().sync();
        }catch(Exception e){
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args){
        try{
            new FnServer().serve();
        }catch(Exception e){
            logger.log(Level.SEVERE, "Terminated abnormally: " + e.getMessage(), e);
        }
    }

}
