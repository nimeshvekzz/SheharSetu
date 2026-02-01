package com.infowave.sheharsetu.net;

import com.android.volley.toolbox.HurlStack;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;

public class HostOnlyUnsafeHurlStack extends HurlStack {

    private final String allowedHost;

    public HostOnlyUnsafeHurlStack(String allowedHost) {
        this.allowedHost = allowedHost;
    }

    @Override
    protected HttpURLConnection createConnection(URL url) throws IOException {
        HttpURLConnection conn = super.createConnection(url);

        if (conn instanceof HttpsURLConnection) {
            String host = url.getHost();
            if (host != null && host.equalsIgnoreCase(allowedHost)) {
                HttpsURLConnection https = (HttpsURLConnection) conn;

                https.setSSLSocketFactory(UnsafeSsl.trustAllSocketFactory());

                HostnameVerifier hv = (hostname, session) ->
                        hostname != null && hostname.equalsIgnoreCase(allowedHost);
                https.setHostnameVerifier(hv);
            }
        }
        return conn;
    }
}
