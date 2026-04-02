package com.infowave.sheharsetu;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * Custom Glide module that uses an OkHttp client which trusts all SSL
 * certificates.
 * This is a workaround for Hostinger's SSL certificate chain issues.
 * 
 * WARNING: This bypasses SSL verification and should only be used for
 * development/testing.
 * For production, fix the SSL certificate on the server side.
 */
@GlideModule
public final class UnsafeGlideModule extends AppGlideModule {

    private static final String TAG = "UnsafeGlideModule";

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // Default options
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        OkHttpClient unsafeClient = getUnsafeOkHttpClient();

        // Replace default HTTP loader with our unsafe OkHttp client
        registry.replace(GlideUrl.class, InputStream.class,
                new OkHttpUrlLoader.Factory(unsafeClient));
    }

    /**
     * Creates an OkHttpClient that trusts all SSL certificates.
     * WARNING: Only use for development/testing!
     */
    private OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType)
                                throws CertificateException {
                            // Trust all client certs
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType)
                                throws CertificateException {
                            // Trust all server certs
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[] {};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true); // Trust all hostnames

            builder.connectTimeout(30, TimeUnit.SECONDS);
            builder.readTimeout(30, TimeUnit.SECONDS);
            builder.writeTimeout(30, TimeUnit.SECONDS);
            return builder.build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating unsafe OkHttp client", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false; // Disable manifest parsing for faster startup
    }
}
