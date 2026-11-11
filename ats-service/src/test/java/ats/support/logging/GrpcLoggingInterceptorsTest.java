package ats.support.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ats.scoring.proto.ScoringServiceGrpc;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import scoring_service.v1.models.Messages;

class GrpcLoggingInterceptorsTest {

    private final Server server = buildServer("grpc-logging-test");
    private final ManagedChannel channel =
            InProcessChannelBuilder.forName("grpc-logging-test")
                    .directExecutor()
                    .intercept(new GrpcClientLoggingInterceptor("scoring-service"))
                    .build();

    private final Logger clientLogger =
            (Logger) LoggerFactory.getLogger(GrpcClientLoggingInterceptor.class);
    private final Logger serverLogger =
            (Logger) LoggerFactory.getLogger(GrpcServerLoggingInterceptor.class);
    private final ListAppender<ILoggingEvent> clientAppender = new ListAppender<>();
    private final ListAppender<ILoggingEvent> serverAppender = new ListAppender<>();

    @BeforeEach
    void setUp() throws Exception {
        clientAppender.start();
        serverAppender.start();
        clientLogger.addAppender(clientAppender);
        serverLogger.addAppender(serverAppender);
        server.start();
        MDC.put(RequestIds.MDC_KEY, "req-grpc");
    }

    @AfterEach
    void tearDown() throws Exception {
        clientLogger.detachAppender(clientAppender);
        serverLogger.detachAppender(serverAppender);
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        MDC.clear();
    }

    @Test
    void callerAndCalleeLogPairedEventsWithSameRequestId() {
        ScoringServiceGrpc.newBlockingStub(channel)
                .scoreResume(Messages.ScoreResumeRequest.getDefaultInstance());

        List<String> clientMessages =
                clientAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        List<String> serverMessages =
                serverAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

        assertThat(clientMessages)
                .anyMatch(
                        m ->
                                m.startsWith(
                                                "outbound_request_sent transport=grpc"
                                                        + " target=scoring-service")
                                        && m.contains("request_id=req-grpc"))
                .anyMatch(
                        m ->
                                m.startsWith("outbound_response_received transport=grpc")
                                        && m.contains("status=OK")
                                        && m.contains("request_id=req-grpc"));
        assertThat(serverMessages)
                .anyMatch(m -> m.startsWith("inbound_request_received transport=grpc"))
                .anyMatch(
                        m ->
                                m.startsWith("inbound_response_sent transport=grpc")
                                        && m.contains("status=OK"));
    }

    private static Server buildServer(String name) {
        ScoringServiceGrpc.ScoringServiceImplBase service =
                new ScoringServiceGrpc.ScoringServiceImplBase() {
                    @Override
                    public void scoreResume(
                            Messages.ScoreResumeRequest request,
                            StreamObserver<Messages.ScoreResumeResponse> responseObserver) {
                        responseObserver.onNext(Messages.ScoreResumeResponse.getDefaultInstance());
                        responseObserver.onCompleted();
                    }
                };
        return InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(
                        ServerInterceptors.intercept(service, new GrpcServerLoggingInterceptor()))
                .build();
    }
}
