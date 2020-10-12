package dev.jons.example.pinning.android;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

public class GatherCertificateHashes extends IntentService {
    public static final String TAG = GatherCertificateHashes.class.getCanonicalName();
    public static final String ACTION_GET_CERTIST_HASHES = "dev.jons.example.pinning.android.action.GET_CERTIST_HASHES";
    public static final String ACTION_GET_LOCAL_SOCKET_HASHES = "dev.jons.example.pinning.android.action.GET_LOCAL_SOCKET_HASHES";
    public static final String ACTION_RESULT_LOCAL_SOCKET_HASHES = "dev.jons.example.pinning.android.action.RESULT_LOCAL_SOCKET_HASHES";
    public static final String ACTION_RESULT_CERTIST_HASHES = "dev.jons.example.pinning.android.action.RESULT_CERTIST_HASHES";
    public static final String EXTRAS_HOST_NAME = "dev.jons.example.pinning.android.extra.HOST_NAME";
    public static final String RESULT_SHA256_HASHES = "dev.jons.example.pinning.android.result.RESULT_SHA256_HASHES";

    public GatherCertificateHashes() {
        super("RetrieveCertIstApiResults");
    }


    public static void getCertistApiHashesForDomain(Context context, String domain) {
        Intent intent = new Intent(context, GatherCertificateHashes.class);
        intent.setAction(ACTION_GET_CERTIST_HASHES);
        intent.putExtra(EXTRAS_HOST_NAME, domain);
        context.startService(intent);
    }

    public static void getLocalSocketHashesForDomain(Context context, String domain) {
        Intent intent = new Intent(context, GatherCertificateHashes.class);
        intent.setAction(ACTION_GET_LOCAL_SOCKET_HASHES);
        intent.putExtra(EXTRAS_HOST_NAME, domain);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            final String hostName = intent.getStringExtra(EXTRAS_HOST_NAME);
            if (hostName != null) {
                if (ACTION_GET_CERTIST_HASHES.equals(action)) {
                    getCertificateHashesFromCertIstApi(hostName);
                } else if (ACTION_GET_LOCAL_SOCKET_HASHES.equals(action)) {
                    getCertificateHashesFromLocalSocket(hostName);
                } else {
                    throw new UnsupportedOperationException(String.format("ERROR UNKNOWN ACTION: %s", action));
                }
            }
        }
    }

    private void getCertificateHashesFromCertIstApi(String hostName) {
        try {
            String format = String.format("https://api.cert.ist/%s", hostName);
            HttpsURLConnection urlConnection = (HttpsURLConnection) new URL(format).openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setReadTimeout(10 * 1000);
            urlConnection.setConnectTimeout(10 * 1000);
            urlConnection.setSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault());
            urlConnection.connect();
            StringBuilder total = new StringBuilder();
            try (InputStream stream = urlConnection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    total.append(line).append('\n');
                }
            }
            JSONObject root = new JSONObject(total.toString());
            JSONArray chain = root.getJSONArray("chain");
            String[] sha256Hashes = new String[chain.length()];
            for (int i = 0; i < chain.length(); i++) {
                JSONObject certificateInChain = chain.getJSONObject(i);
                JSONObject der = certificateInChain.getJSONObject("der");
                JSONObject hashes = der.getJSONObject("hashes");
                sha256Hashes[i] = hashes.getString("sha256");
            }
            Intent data = new Intent(ACTION_RESULT_CERTIST_HASHES);
            data.putExtra(RESULT_SHA256_HASHES, sha256Hashes);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(data);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void getCertificateHashesFromLocalSocket(String hostName) {
        try {
            final URL url = new URL(String.format("https://%s", hostName));
            final HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setReadTimeout(10 * 1000);
            urlConnection.setConnectTimeout(10 * 1000);
            urlConnection.setSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault());
            urlConnection.connect();
            final Certificate[] serverCertificates = urlConnection.getServerCertificates();
            final String[] hashes = new String[serverCertificates.length];
            for (int i = 0; i < serverCertificates.length; i++) {
                Certificate certificate = serverCertificates[i];
                final byte[] encoded = certificate.getEncoded();
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(encoded);
                StringBuilder hex = new StringBuilder(hash.length * 2);
                for (byte b : hash)
                    hex.append(String.format("%02x", b & 0xFF));
                hashes[i] = hex.toString();
            }
            Intent data = new Intent(ACTION_RESULT_LOCAL_SOCKET_HASHES);
            data.putExtra(RESULT_SHA256_HASHES, hashes);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(data);
        } catch (IOException | CertificateEncodingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
}
