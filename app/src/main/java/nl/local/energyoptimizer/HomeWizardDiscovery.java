package nl.local.energyoptimizer;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

public final class HomeWizardDiscovery {
    private static final String SERVICE_TYPE = "_hwenergy._tcp.";
    private static final long DISCOVERY_TIMEOUT_MS = 12_000L;

    public interface Callback {
        void onFound(String host, String label);
        void onFailure(String message);
        void onMessage(String message);
    }

    private final NsdManager nsdManager;
    private final Handler handler;
    private final Queue<NsdServiceInfo> resolveQueue = new ArrayDeque<>();
    private NsdManager.DiscoveryListener activeListener;
    private boolean finished;
    private boolean resolving;

    public HomeWizardDiscovery(Context context, Handler handler) {
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.handler = handler;
    }

    public void discover(Callback callback) {
        stop();
        finished = false;
        callback.onMessage("mDNS discovery gestart voor " + SERVICE_TYPE);

        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                finishFailure(callback, "Discovery start mislukt: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                callback.onMessage("Discovery stop mislukt: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                callback.onMessage("Zoekt naar HomeWizard Energy-apparaten...");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                callback.onMessage("Discovery gestopt");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                callback.onMessage("HomeWizard-kandidaat gevonden: " + serviceInfo.getServiceName());
                enqueueResolve(serviceInfo, callback);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                callback.onMessage("Service verdwenen: " + serviceInfo.getServiceName());
            }
        };

        activeListener = listener;
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener);
            handler.postDelayed(() -> {
                if (!finished) {
                    finishFailure(callback, "Geen HomeWizard P1 gevonden. Vul het IP-adres handmatig in.");
                }
            }, DISCOVERY_TIMEOUT_MS);
        } catch (RuntimeException ex) {
            finishFailure(callback, "Discovery kon niet starten: " + ex.getMessage());
        }
    }

    public void stop() {
        resolveQueue.clear();
        resolving = false;
        if (activeListener != null) {
            try {
                nsdManager.stopServiceDiscovery(activeListener);
            } catch (RuntimeException ignored) {
                // Android kan gooien als discovery al gestopt is.
            }
            activeListener = null;
        }
    }

    private void enqueueResolve(NsdServiceInfo serviceInfo, Callback callback) {
        resolveQueue.add(serviceInfo);
        resolveNext(callback);
    }

    private void resolveNext(Callback callback) {
        if (finished || resolving) {
            return;
        }
        NsdServiceInfo serviceInfo = resolveQueue.poll();
        if (serviceInfo == null) {
            return;
        }
        resolving = true;
        try {
            nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                    resolving = false;
                    callback.onMessage("Resolve mislukt voor " + info.getServiceName() + ": " + errorCode);
                    resolveNext(callback);
                }

                @Override
                public void onServiceResolved(NsdServiceInfo info) {
                    resolving = false;
                    if (finished) {
                        return;
                    }
                    if (!isP1(info)) {
                        callback.onMessage("Geen P1-meter: " + info.getServiceName());
                        resolveNext(callback);
                        return;
                    }
                    InetAddress host = info.getHost();
                    if (host == null) {
                        callback.onMessage("P1 gevonden zonder IP-adres");
                        return;
                    }
                    finished = true;
                    String address = host.getHostAddress();
                    callback.onFound(address, info.getServiceName());
                    stop();
                }
            });
        } catch (RuntimeException ex) {
            resolving = false;
            callback.onMessage("Resolve kon niet starten: " + ex.getMessage());
            resolveNext(callback);
        }
    }

    private void finishFailure(Callback callback) {
        finishFailure(callback, "Discovery mislukt");
    }

    private void finishFailure(Callback callback, String message) {
        if (!finished) {
            finished = true;
            callback.onFailure(message);
        }
        stop();
    }

    private static boolean isP1(NsdServiceInfo info) {
        String name = info.getServiceName() == null ? "" : info.getServiceName().toLowerCase(Locale.ROOT);
        if (name.contains("p1")) {
            return true;
        }
        for (Map.Entry<String, byte[]> entry : info.getAttributes().entrySet()) {
            String key = entry.getKey();
            String value = new String(entry.getValue(), StandardCharsets.UTF_8);
            if ("product_type".equals(key) && "HWE-P1".equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
