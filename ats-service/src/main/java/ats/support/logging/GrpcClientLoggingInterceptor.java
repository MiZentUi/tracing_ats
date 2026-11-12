package ats.support.logging;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

/**
 * Граница исходящего gRPC: пробрасывает request_id в metadata и логирует отправку/ответ.
 * Используется только для наших внутренних сервисов (scoring-service), внешние API — отдельно.
 */
@Slf4j
public class GrpcClientLoggingInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> REQUEST_ID =
            Metadata.Key.of(RequestIds.GRPC_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    private final String target;

    public GrpcClientLoggingInterceptor(String target) {
        this.target = target;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        String requestId = RequestIds.currentOrGenerate();
        String methodName = method.getFullMethodName();
        log.info(
                "outbound_request_sent transport=grpc target={} method={} request_id={}",
                target,
                methodName,
                requestId);
        long startedAt = System.nanoTime();

        ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
        return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (!headers.containsKey(REQUEST_ID)) {
                    headers.put(REQUEST_ID, requestId);
                }
                Listener<RespT> listener =
                        new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(
                                responseListener) {
                            @Override
                            public void onClose(Status status, Metadata trailers) {
                                if (status.isOk()) {
                                    log.info(
                                            "outbound_response_received transport=grpc target={}"
                                                    + " method={} status={} elapsed_ms={}"
                                                    + " request_id={}",
                                            target,
                                            methodName,
                                            status.getCode(),
                                            (System.nanoTime() - startedAt) / 1_000_000,
                                            requestId);
                                } else {
                                    log.warn(
                                            "outbound_response_received transport=grpc target={}"
                                                    + " method={} status={} elapsed_ms={}"
                                                    + " request_id={}",
                                            target,
                                            methodName,
                                            status.getCode(),
                                            (System.nanoTime() - startedAt) / 1_000_000,
                                            requestId);
                                }
                                if (status.getCode() == Status.Code.UNAVAILABLE
                                        || status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
                                    log.warn(
                                            "outbound_request_failed transport=grpc target={}"
                                                    + " method={} status={} request_id={}",
                                            target,
                                            methodName,
                                            status.getCode(),
                                            requestId);
                                }
                                super.onClose(status, trailers);
                            }
                        };
                super.start(listener, headers);
            }
        };
    }
}
