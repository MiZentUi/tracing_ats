package ats.vacancy.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsOversizedUploadToPayloadTooLarge() {
        var response =
                handler.handleUploadTooLarge(new MaxUploadSizeExceededException(10_000_000L));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("FILE_TOO_LARGE");
        assertThat(response.getBody().getMessage()).contains("larger");
    }
}
