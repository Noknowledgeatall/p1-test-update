package nl.local.energyoptimizer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class UpdateClient {
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/Noknowledgeatall/p1-test-update/releases/latest";
    private static final String RELEASES_PAGE_URL =
            "https://github.com/Noknowledgeatall/p1-test-update/releases";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    public UpdateInfo checkLatest() throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "EnergyOptimizerAndroid/" + BuildConfig.VERSION_NAME);

        int code = connection.getResponseCode();
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            return new UpdateInfo(false, "", RELEASES_PAGE_URL,
                    "Nog geen GitHub Release gevonden. Maak een release met app-debug.apk als asset.");
        }
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("GitHub gaf HTTP " + code);
        }

        JSONObject json = new JSONObject(readBody(connection.getInputStream()));
        String tag = json.optString("tag_name", "");
        String releasePage = json.optString("html_url", RELEASES_PAGE_URL);
        String apkUrl = findApkAssetUrl(json.optJSONArray("assets"));
        if (apkUrl.isEmpty()) {
            return new UpdateInfo(false, tag, releasePage,
                    "Release " + tag + " gevonden, maar er staat geen APK asset bij.");
        }

        boolean newer = isNewerTag(tag, BuildConfig.VERSION_NAME);
        String message = newer
                ? "Update beschikbaar: " + tag
                : "Laatste release gevonden: " + tag;
        return new UpdateInfo(newer, tag, apkUrl, message);
    }

    private static String findApkAssetUrl(JSONArray assets) throws JSONException {
        if (assets == null) {
            return "";
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name", "");
            if (name.toLowerCase().endsWith(".apk")) {
                return asset.optString("browser_download_url", "");
            }
        }
        return "";
    }

    private static boolean isNewerTag(String tag, String currentVersion) {
        int[] latest = parseVersion(tag);
        int[] current = parseVersion(currentVersion);
        for (int i = 0; i < Math.max(latest.length, current.length); i++) {
            int left = i < latest.length ? latest[i] : 0;
            int right = i < current.length ? current[i] : 0;
            if (left != right) {
                return left > right;
            }
        }
        return false;
    }

    private static int[] parseVersion(String value) {
        String clean = value == null ? "" : value.replaceFirst("^[vV]", "");
        String[] parts = clean.split("[^0-9]+");
        int[] result = new int[Math.min(parts.length, 4)];
        int count = 0;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (count == result.length) {
                break;
            }
            try {
                result[count++] = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                result[count++] = 0;
            }
        }
        if (count == result.length) {
            return result;
        }
        int[] trimmed = new int[count];
        System.arraycopy(result, 0, trimmed, 0, count);
        return trimmed;
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

    public static final class UpdateInfo {
        public final boolean updateAvailable;
        public final String tagName;
        public final String downloadUrl;
        public final String message;

        public UpdateInfo(boolean updateAvailable, String tagName, String downloadUrl, String message) {
            this.updateAvailable = updateAvailable;
            this.tagName = tagName;
            this.downloadUrl = downloadUrl;
            this.message = message;
        }
    }
}
