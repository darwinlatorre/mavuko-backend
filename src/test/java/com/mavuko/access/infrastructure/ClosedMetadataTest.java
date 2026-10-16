package com.mavuko.access.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.mavuko.MavukoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ClosedMetadataTest {
    @Test
    void configuredBoundaryDoesNotExposeResourceMetadata() throws Exception {
        try (var context = new SpringApplicationBuilder(MavukoApplication.class)
                .run("--server.port=0", "--server.address=127.0.0.1",
                        "--mavuko.auth0.issuer=https://probe.invalid/",
                        "--mavuko.auth0.audience=probe-api")) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                    + "/.well-known/oauth-protected-resource")).build();
            try (var client = HttpClient.newHttpClient()) {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.headers().firstValue("set-cookie")).isEmpty();
                assertThat(response.headers().firstValue("location")).isEmpty();
                assertThat(response.body()).isEmpty();
            }
        }
    }
}
