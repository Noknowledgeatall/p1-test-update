package nl.local.energyoptimizer;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final long POLL_INTERVAL_MS = 5_000L;
    private static final String DEFAULT_HOMEWIZARD_IP = "192.168.2.40";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final HomeWizardClient homeWizardClient = new HomeWizardClient();
    private final UpdateClient updateClient = new UpdateClient();
    private final PhaseLogger logger = new PhaseLogger();

    private HomeWizardDiscovery discovery;
    private TextView statusText;
    private TextView powerText;
    private TextView directionText;
    private TextView logText;
    private EditText ipInput;
    private Button startButton;
    private Button stopButton;
    private boolean polling;
    private long updateDownloadId = -1L;
    private BroadcastReceiver downloadReceiver;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!polling) {
                return;
            }
            readOnce();
            mainHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        powerText = findViewById(R.id.powerText);
        directionText = findViewById(R.id.directionText);
        logText = findViewById(R.id.logText);
        ipInput = findViewById(R.id.ipInput);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        Button discoverButton = findViewById(R.id.discoverButton);
        Button updateButton = findViewById(R.id.updateButton);
        ipInput.setText(DEFAULT_HOMEWIZARD_IP);

        discovery = new HomeWizardDiscovery(this, mainHandler);
        registerDownloadReceiver();

        discoverButton.setOnClickListener(view -> discoverP1());
        updateButton.setOnClickListener(view -> checkForUpdate());
        startButton.setOnClickListener(view -> startPolling());
        stopButton.setOnClickListener(view -> stopPolling("Logging gestopt"));

        stopButton.setEnabled(false);
        log("Klaar voor Fase 1. Versie " + BuildConfig.VERSION_NAME
                + ". HomeWizard P1 staat vast op " + DEFAULT_HOMEWIZARD_IP + ".");
    }

    @Override
    protected void onDestroy() {
        stopPolling(null);
        discovery.stop();
        if (downloadReceiver != null) {
            unregisterReceiver(downloadReceiver);
            downloadReceiver = null;
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private void discoverP1() {
        statusText.setText("Test HomeWizard P1 op " + DEFAULT_HOMEWIZARD_IP + "...");
        log("Test vast P1-adres: " + DEFAULT_HOMEWIZARD_IP);
        executor.execute(() -> {
            try {
                HomeWizardClient.P1Measurement measurement =
                        homeWizardClient.readMeasurement(DEFAULT_HOMEWIZARD_IP);
                mainHandler.post(() -> {
                    ipInput.setText(DEFAULT_HOMEWIZARD_IP);
                    statusText.setText("P1 gevonden op " + DEFAULT_HOMEWIZARD_IP);
                    showMeasurement(DEFAULT_HOMEWIZARD_IP, measurement);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    log("Vast P1-adres reageert niet: " + ex.getMessage());
                    startMdnsDiscovery();
                });
            }
        });
    }

    private void startMdnsDiscovery() {
        statusText.setText("Zoekt HomeWizard P1 via mDNS...");
        discovery.discover(new HomeWizardDiscovery.Callback() {
            @Override
            public void onFound(String host, String label) {
                mainHandler.post(() -> {
                    ipInput.setText(host);
                    statusText.setText("P1 gevonden: " + label + " op " + host);
                    log("P1 gevonden: " + label + " op " + host);
                });
            }

            @Override
            public void onFailure(String message) {
                mainHandler.post(() -> {
                    statusText.setText(message);
                    log("Fout: " + message);
                });
            }

            @Override
            public void onMessage(String message) {
                mainHandler.post(() -> log(message));
            }
        });
    }

    private void startPolling() {
        String host = ipInput.getText().toString().trim();
        if (host.isEmpty()) {
            log("Vul eerst het HomeWizard P1 IP-adres in of gebruik Zoek P1.");
            statusText.setText("Geen IP-adres ingesteld");
            return;
        }
        polling = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusText.setText("Verbonden met " + host + " - leest elke 5 seconden");
        log("Logging gestart voor " + host);
        mainHandler.removeCallbacks(pollRunnable);
        mainHandler.post(pollRunnable);
    }

    private void checkForUpdate() {
        statusText.setText("Controleert GitHub op updates...");
        log("Updatecontrole gestart via GitHub Releases");
        executor.execute(() -> {
            try {
                UpdateClient.UpdateInfo updateInfo = updateClient.checkLatest();
                mainHandler.post(() -> showUpdateResult(updateInfo));
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    statusText.setText("Updatecontrole mislukt");
                    log("Fout bij updatecontrole: " + ex.getMessage());
                });
            }
        });
    }

    private void showUpdateResult(UpdateClient.UpdateInfo updateInfo) {
        statusText.setText(updateInfo.message);
        log(updateInfo.message);
        if (updateInfo.downloadUrl == null || updateInfo.downloadUrl.isEmpty()) {
            return;
        }
        downloadUpdate(updateInfo);
    }

    private void downloadUpdate(UpdateClient.UpdateInfo updateInfo) {
        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            log("DownloadManager niet beschikbaar op dit apparaat");
            return;
        }

        String tag = updateInfo.tagName == null || updateInfo.tagName.isEmpty()
                ? "latest"
                : updateInfo.tagName.replaceAll("[^A-Za-z0-9._-]", "_");
        String fileName = "energy-optimizer-" + tag + ".apk";
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
                .setTitle("Energy Optimizer update")
                .setDescription("Downloadt " + fileName)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true);

        updateDownloadId = downloadManager.enqueue(request);
        statusText.setText("Update wordt gedownload...");
        log("Update-download gestart: " + fileName);
    }

    private void registerDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (completedId != updateDownloadId) {
                    return;
                }
                openDownloadedUpdate(completedId);
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, filter);
        }
    }

    private void openDownloadedUpdate(long downloadId) {
        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            log("Download voltooid, maar DownloadManager is niet beschikbaar");
            return;
        }

        Uri uri = downloadManager.getUriForDownloadedFile(downloadId);
        if (uri == null) {
            statusText.setText("Update-download mislukt");
            log("Update-download mislukt of bestand niet gevonden");
            return;
        }

        statusText.setText("Update gedownload");
        log("Update gedownload. Opent Android installer.");
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(installIntent);
    }

    private void stopPolling(String message) {
        polling = false;
        mainHandler.removeCallbacks(pollRunnable);
        if (startButton != null) {
            startButton.setEnabled(true);
        }
        if (stopButton != null) {
            stopButton.setEnabled(false);
        }
        if (message != null) {
            log(message);
            statusText.setText(message);
        }
    }

    private void readOnce() {
        String host = ipInput.getText().toString().trim();
        executor.execute(() -> {
            try {
                HomeWizardClient.P1Measurement measurement = homeWizardClient.readMeasurement(host);
                mainHandler.post(() -> showMeasurement(host, measurement));
            } catch (Exception ex) {
                mainHandler.post(() -> showReadError(host, ex));
            }
        });
    }

    private void showMeasurement(String host, HomeWizardClient.P1Measurement measurement) {
        powerText.setText(measurement.activePowerW + " W");
        directionText.setText(measurement.directionLabel() + " - positief = import, negatief = export");
        statusText.setText("Laatste meting van " + host);
        log("P1 " + measurement.formatSummary());
    }

    private void showReadError(String host, Exception ex) {
        statusText.setText("Geen verbinding met HomeWizard P1");
        log("Fout bij lezen " + host + ": " + ex.getMessage());
    }

    private void log(String message) {
        logger.add(message);
        logText.setText(logger.render());
    }
}
