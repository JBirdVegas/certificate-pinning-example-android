package dev.jons.example.pinning.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;

public class DomainCertificatePinningFragment extends Fragment {
    private final ArrayList<String> localHashes;
    private final ArrayList<String> certistHashes;
    private ArrayAdapter<String> localSocketResultsAdapter;
    private ArrayAdapter<String> certIstResultsAdapter;
    private View rootView;
    private EditText editText;
    private TextView doHashesMatch;
    private IntentFilter localResultsFilter = new IntentFilter(
            GatherCertificateHashes.ACTION_RESULT_LOCAL_SOCKET_HASHES);
    private IntentFilter certIstResultsFilter = new IntentFilter(
            GatherCertificateHashes.ACTION_RESULT_CERTIST_HASHES);


    public DomainCertificatePinningFragment() {
        this.localHashes = new ArrayList<>();
        this.certistHashes = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_certificate_pinning, container, false);

        localSocketResultsAdapter = new ArrayAdapter<>(inflater.getContext(),
                R.layout.expected_hash, R.id.list_view_item_hash, localHashes);
        certIstResultsAdapter = new ArrayAdapter<>(inflater.getContext(),
                R.layout.expected_hash, R.id.list_view_item_hash, certistHashes);

        editText = rootView.findViewById(R.id.edit_text_first);
        editText.setOnKeyListener((View v, int keyCode, KeyEvent event) -> {
            if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                gatherCertificates();
                return true;
            }
            return false;
        });
        doHashesMatch = rootView.findViewById(R.id.do_certificates_match_label);

        ((ListView) rootView.findViewById(R.id.local_socket_results)).setAdapter(localSocketResultsAdapter);
        ((ListView) rootView.findViewById(R.id.certist_results)).setAdapter(certIstResultsAdapter);
        rootView.findViewById(R.id.button_first).setOnClickListener((v) -> gatherCertificates());
        return rootView;
    }

    private void gatherCertificates() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(rootView.getContext());
        manager.registerReceiver(getResultsReceiver(manager,
                localSocketResultsAdapter, R.id.local_socket_results_label), localResultsFilter);
        manager.registerReceiver(getResultsReceiver(manager,
                certIstResultsAdapter, R.id.certist_results_label), certIstResultsFilter);
        String domain = editText.getText().toString();
        GatherCertificateHashes.getCertistApiHashesForDomain(rootView.getContext(), domain);
        GatherCertificateHashes.getLocalSocketHashesForDomain(rootView.getContext(), domain);
    }

    private BroadcastReceiver getResultsReceiver(
            final LocalBroadcastManager manager,
            final ArrayAdapter<String> adapter,
            final int label) {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                manager.unregisterReceiver(this);
                adapter.clear();
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    adapter.addAll(extras.getStringArray(GatherCertificateHashes.RESULT_SHA256_HASHES));
                }
                adapter.notifyDataSetChanged();
                allFound();
                rootView.findViewById(label).setVisibility(View.VISIBLE);
            }
        };
    }

    private List<String> grabHashesFromAdapter(ArrayAdapter<String> adapter) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < adapter.getCount(); i++) {
            l.add(adapter.getItem(i));
        }
        return l;
    }

    private void allFound() {
        doHashesMatch.setVisibility(View.VISIBLE);

        List<String> certist = grabHashesFromAdapter(certIstResultsAdapter);
        List<String> local = grabHashesFromAdapter(localSocketResultsAdapter);
        if (certist.equals(local)) {
            doHashesMatch.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            doHashesMatch.setText(R.string.certificate_hashes_match_label);
        } else {
            doHashesMatch.setTextColor(Color.RED);
            doHashesMatch.setText(R.string.certificate_hashes_donot_match_label);
        }
    }
}