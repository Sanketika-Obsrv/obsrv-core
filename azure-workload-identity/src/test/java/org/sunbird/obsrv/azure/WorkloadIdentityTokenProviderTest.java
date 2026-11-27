package org.sunbird.obsrv.azure;

import com.sun.net.httpserver.HttpServer;
import org.apache.hadoop.conf.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WorkloadIdentityTokenProviderTest {

    private static final String ACCOUNT = "obsrvtest.dfs.core.windows.net";

    private HttpServer server;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response = "{\"token_type\":\"Bearer\",\"expires_in\":3599,\"access_token\":\"tok-123\"}";
    private Path tokenFile;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        tokenFile = Files.createTempFile("sa-token", ".jwt");
        Files.write(tokenFile, "projected-sa-jwt\n".getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() throws IOException {
        server.stop(0);
        Files.deleteIfExists(tokenFile);
    }

    private Configuration conf() {
        Configuration conf = new Configuration(false);
        conf.set(WorkloadIdentityTokenProvider.CLIENT_ID_KEY, "client-1");
        conf.set(WorkloadIdentityTokenProvider.TENANT_KEY, "tenant-1");
        conf.set(WorkloadIdentityTokenProvider.TOKEN_FILE_KEY, tokenFile.toString());
        conf.set(WorkloadIdentityTokenProvider.AUTHORITY_KEY, "http://127.0.0.1:" + server.getAddress().getPort());
        return conf;
    }

    @Test
    public void exchangesProjectedTokenForStorageToken() throws IOException {
        WorkloadIdentityTokenProvider provider = new WorkloadIdentityTokenProvider();
        provider.initialize(conf(), ACCOUNT);

        long before = System.currentTimeMillis();
        assertEquals("tok-123", provider.getAccessToken());
        long expiry = provider.getExpiryTime().getTime();
        assertTrue(expiry >= before + 3599_000L && expiry <= System.currentTimeMillis() + 3599_000L);

        assertEquals("/tenant-1/oauth2/v2.0/token", lastPath.get());
        String body = URLDecoder.decode(lastBody.get(), StandardCharsets.UTF_8);
        assertTrue(body.contains("grant_type=client_credentials"));
        assertTrue(body.contains("client_id=client-1"));
        assertTrue(body.contains("scope=https://storage.azure.com/.default"));
        assertTrue(body.contains("client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
        assertTrue(body.contains("client_assertion=projected-sa-jwt"));
    }

    @Test
    public void rereadsRotatedTokenFile() throws IOException {
        WorkloadIdentityTokenProvider provider = new WorkloadIdentityTokenProvider();
        provider.initialize(conf(), ACCOUNT);
        provider.getAccessToken();

        Files.write(tokenFile, "rotated-jwt".getBytes(StandardCharsets.UTF_8));
        provider.getAccessToken();
        assertTrue(URLDecoder.decode(lastBody.get(), StandardCharsets.UTF_8).contains("client_assertion=rotated-jwt"));
    }

    @Test
    public void accountSpecificKeyWins() throws IOException {
        Configuration conf = conf();
        conf.set(WorkloadIdentityTokenProvider.TENANT_KEY + "." + ACCOUNT, "tenant-acct");
        WorkloadIdentityTokenProvider provider = new WorkloadIdentityTokenProvider();
        provider.initialize(conf, ACCOUNT);
        provider.getAccessToken();
        assertEquals("/tenant-acct/oauth2/v2.0/token", lastPath.get());
    }

    @Test
    public void surfacesTokenEndpointError() throws IOException {
        status = 401;
        response = "{\"error\":\"invalid_client\",\"error_description\":\"AADSTS70021: No matching federated identity record found\"}";
        WorkloadIdentityTokenProvider provider = new WorkloadIdentityTokenProvider();
        provider.initialize(conf(), ACCOUNT);
        try {
            provider.getAccessToken();
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("HTTP 401"));
            assertTrue(e.getMessage().contains("AADSTS70021"));
        }
    }

    @Test
    public void failsClearlyWhenNotConfigured() {
        Configuration conf = new Configuration(false);
        WorkloadIdentityTokenProvider provider = new WorkloadIdentityTokenProvider();
        try {
            provider.initialize(conf, ACCOUNT);
            // Only reachable if the test JVM itself has the webhook env vars set.
            assertTrue(System.getenv("AZURE_CLIENT_ID") != null);
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("azure.workload.identity/use=true"));
        }
    }
}
