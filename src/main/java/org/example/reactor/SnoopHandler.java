package org.example.reactor;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import reactor.netty.NettyOutbound;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class SnoopHandler implements BiFunction<HttpServerRequest,HttpServerResponse,NettyOutbound> {

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(SnoopHandler.class.getName());

    public NettyOutbound apply(HttpServerRequest request, HttpServerResponse response){
        return response.sendString(request.receive().asString().map(b -> {
            //logger.info(String.format("/, body=[%s]", b));
            final StringBuilder buf = new StringBuilder();

            buf.append("FN-NETTY (REACTOR) SERVER\r\n");
            buf.append("===================================\r\n");

            buf.append("VERSION: ").append(request.version()).append("\r\n");
            buf.append("HOSTNAME: ").append(request.requestHeaders().get(HttpHeaderNames.HOST, "unknown")).append("\r\n");
            buf.append("REQUEST_URI: ").append(request.uri()).append("\r\n\r\n");

            HttpHeaders headers = request.requestHeaders();
            if (!headers.isEmpty()) {
                for (Map.Entry<String, String> h: headers) {
                    CharSequence key = h.getKey();
                    CharSequence value = h.getValue();
                    buf.append("HEADER: ").append(key).append(" = ").append(value).append("\r\n");
                }
                buf.append("\r\n");
            }
            buf.append("CONTENT: ");
            buf.append(b);
            buf.append("\r\n");
            buf.append("END OF CONTENT\r\n");
            return buf.toString();
        }));
    }
}
