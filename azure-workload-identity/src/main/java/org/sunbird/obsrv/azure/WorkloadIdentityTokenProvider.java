package org.sunbird.obsrv.azure;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.azurebfs.extensions.CustomTokenProviderAdaptee;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ABFS token provider for AKS Workload Identity.
 *
 * Hadoop only ships a Workload Identity provider from 3.4.0; Flink 1.20's azure-fs-hadoop plugin
 * bundles 3.3.4. This plugs into the {@link CustomTokenProviderAdaptee} hook that 3.3.x has and
 * exchanges the projected service-account token for an Entra ID token for Azure Storage.
 *
 * Enable with:
 *   fs.azure.account.auth.type = Custom
 *   fs.azure.account.oauth.provider.type = org.sunbird.obsrv.azure.WorkloadIdentityTokenProvider
 *
 * Client id, tenant and token file come from the env vars the AKS webhook injects
 * (AZURE_CLIENT_ID, AZURE_TENANT_ID, AZURE_FEDERATED_TOKEN_FILE, AZURE_AUTHORITY_HOST).
 * The Hadoop 3.4 config keys below override them, so switching to Hadoop's own provider later
 * is a config change only.
 */
public class WorkloadIdentityTokenProvider implements CustomTokenProviderAdaptee {

    static final String CLIENT_ID_KEY = "fs.azure.account.oauth2.client.id";
    static final String TENANT_KEY = "fs.azure.account.oauth2.msi.tenant";
    static final String TOKEN_FILE_KEY = "fs.azure.account.oauth2.token.file";
    static final String AUTHORITY_KEY = "fs.azure.account.oauth2.msi.authority";

    private static final String DEFAULT_AUTHORITY = "https://login.microsoftonline.com/";
    private static final String SCOPE = "https://storage.azure.com/.default";
    private static final String ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES_IN = Pattern.compile("\"expires_in\"\\s*:\\s*\"?(\\d+)\"?");
    private static final int TIMEOUT_MS = 30_000;

    private String clientId;
    private String tokenFile;
    private String tokenEndpoint;
    private volatile Date expiryTime = new Date(0);

    @Override
    public void initialize(Configuration conf, String accountName) throws IOException {
        clientId = setting(conf, CLIENT_ID_KEY, accountName, "AZURE_CLIENT_ID");
        String tenant = setting(conf, TENANT_KEY, accountName, "AZURE_TENANT_ID");
        tokenFile = setting(conf, TOKEN_FILE_KEY, accountName, "AZURE_FEDERATED_TOKEN_FILE");
        String authority = setting(conf, AUTHORITY_KEY, accountName, "AZURE_AUTHORITY_HOST");

        if (isBlank(clientId) || isBlank(tenant) || isBlank(tokenFile)) {
            throw new IOException("Workload Identity is not configured: AZURE_CLIENT_ID, AZURE_TENANT_ID and "
                    + "AZURE_FEDERATED_TOKEN_FILE must be set. Check the pod has the label "
                    + "azure.workload.identity/use=true and its service account has the "
                    + "azure.workload.identity/client-id annotation.");
        }
        if (isBlank(authority)) {
            authority = DEFAULT_AUTHORITY;
        }
        if (!authority.endsWith("/")) {
            authority = authority + "/";
        }
        tokenEndpoint = authority + tenant + "/oauth2/v2.0/token";
    }

    @Override
    public String getAccessToken() throws IOException {
        // Read on every call: the kubelet rotates the projected token file.
        String assertion = new String(Files.readAllBytes(Paths.get(tokenFile)), StandardCharsets.UTF_8).trim();
        String body = "grant_type=client_credentials"
                + "&client_id=" + encode(clientId)
                + "&scope=" + encode(SCOPE)
                + "&client_assertion_type=" + encode(ASSERTION_TYPE)
                + "&client_assertion=" + encode(assertion);

        HttpURLConnection conn = (HttpURLConnection) new URL(tokenEndpoint).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            String response = read(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (status != 200) {
                throw new IOException("Token request to " + tokenEndpoint + " failed with HTTP " + status + ": " + response);
            }

            Matcher token = ACCESS_TOKEN.matcher(response);
            Matcher expires = EXPIRES_IN.matcher(response);
            if (!token.find() || !expires.find()) {
                throw new IOException("Token response from " + tokenEndpoint + " has no access_token/expires_in");
            }
            expiryTime = new Date(System.currentTimeMillis() + Long.parseLong(expires.group(1)) * 1000L);
            return token.group(1);
        } finally {
            conn.disconnect();
        }
    }

    @Override
    public Date getExpiryTime() {
        return expiryTime;
    }

    // Account-specific key (key.<account>) wins over the global key, which wins over the env var.
    private static String setting(Configuration conf, String key, String accountName, String envVar) {
        String value = conf.get(key + "." + accountName);
        if (isBlank(value)) {
            value = conf.get(key);
        }
        if (isBlank(value)) {
            value = System.getenv(envVar);
        }
        return value == null ? null : value.trim();
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
