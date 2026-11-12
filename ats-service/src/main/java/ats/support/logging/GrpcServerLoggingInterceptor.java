package ats.support.logging;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * Граница входящего gRPC: берёт request_id из metadata (его ставит клиентский interceptor),
 * логирует приём запроса и отправку ответа. Тела сообщений не логируются.
 */
@Slf4j
@Component
@GlobalServerInterceptor
public class GrpcServerLoggingInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> REQUEST_ID =
            Metadata.Key.of(RequestIds.GRPC_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        // Health/reflection — технический шум, в логи не пишем.
        if (method.startsWith("grpc.health") || method.startsWith("grpc.reflection")) {
            return next.startCall(call, headers);
        }
        String requestId = RequestIds.sanitize(headers.get(REQUEST_ID));
        RequestIds.put(requestId);
        long startedAt = System.nanoTime();
        log.info("inbound_request_received transport=grpc method={}", method);

        ServerCall<ReqT, RespT> loggingCall =
                new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        log.info(
                                "inbound_response_sent transport=grpc method={} status={}"
                                        + " elapsed_ms={}",
                                method,
                                status.getCode(),
                                (System.nanoTime() - startedAt) / 1_000_000);
                        RequestIds.clear();
                        super.close(status, trailers);
                    }
                };
        try {
            return next.startCall(loggingCall, headers);
        } catch (RuntimeException e) {
            log.error(
                    "inbound_request_failed transport=grpc method={} error={}",
                    method,
                    e.getClass().getSimpleName());
            RequestIds.clear();
            throw e;
        }
    }
}
