package nl.local.energyoptimizer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class HomeWizardClient {
    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int READ_TIMEOUT_MS = 2500;

    public P1Measurement readMeasurement(String host) throws IOException, JSONException {
        String normalizedHost = normalizeHost(host);
        URL url = new URL("http://" + normalizedHost + "/api/v1/data");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("HomeWizard gaf HTTP " + code);
        }

        String body = readBody(connection.getInputStream());
        JSONObject json = new JSONObject(body);
        if (!json.has("active_power_w") || json.isNull("active_power_w")) {
            throw new JSONException("active_power_w ontbreekt in /api/v1/data");
        }

        double watts = json.getDouble("active_power_w");
        String meterModel = json.optString("meter_model", "");
        int wifiStrength = json.has("wifi_strength") && !json.isNull("wifi_strength")
                ? json.optInt("wifi_strength")
                : -1;
        return new P1Measurement((int) Math.round(watts), meterModel, wifiStrength, body);
    }

    private static String normalizeHost(String host) {
        String trimmed = host == null ? "" : host.trim();
        if (trimmed.startsWith("http://")) {
            trimmed = trimmed.substring("http://".length());
        } else if (trimmed.startsWith("https://")) {
            trimmed = trimmed.substring("https://".length());
        }
        int slashIndex = trimmed.indexOf('/');
        return slashIndex >= 0 ? trimmed.substring(0, slashIndex) : trimmed;
    }

    private static String readBody(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    public static final class P1Measurement {
        public final int activePowerW;
        public final String meterModel;
        public final int wifiStrength;
        public final String rawJson;

        public P1Measurement(int activePowerW, String meterModel, int wifiStrength, String rawJson) {
            this.activePowerW = activePowerW;
            this.meterModel = meterModel;
            this.wifiStrength = wifiStrength;
            this.rawJson = rawJson;
        }

        public String directionLabel() {
            if (activePowerW > 0) {
                return "Import uit net";
            }
            if (activePowerW < 0) {
                return "Export naar net";
            }
            return "Netto 0 W";
        }

        public String formatSummary() {
            String wifi = wifiStrength >= 0
                    ? String.format(Locale.US, ", wifi %d%%", wifiStrength)
                    : "";
            String model = meterModel == null || meterModel.isEmpty() ? "" : ", " + meterModel;
            return activePowerW + " W - " + directionLabel() + model + wifi;
        }
    }
}
