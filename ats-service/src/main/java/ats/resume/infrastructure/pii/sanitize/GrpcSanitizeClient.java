package ats.resume.infrastructure.pii.sanitize;

import ats.resume.config.SanitizeProperties;
import ats.sanitize.proto.DetectPiiRequest;
import ats.sanitize.proto.DetectPiiResponse;
import ats.sanitize.proto.SanitizeServiceGrpc;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;

/** gRPC-вызов sanitize-service с дедлайном. Ошибки не глотает: их решает шлюз. */
@RequiredArgsConstructor
public class GrpcSanitizeClient implements SanitizeClient {

    private final SanitizeServiceGrpc.SanitizeServiceBlockingStub stub;
    private final SanitizeProperties properties;

    @Override
    public DetectPiiResponse detect(DetectPiiRequest request) {
        return stub.withDeadlineAfter(properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                .detectPii(request);
    }
}
