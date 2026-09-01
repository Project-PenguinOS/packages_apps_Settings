/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.settings.custom.spoofing;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpoofingUtils {
    private static final String TAG = "SpoofingUtils";

    private static volatile String sPixelVersionsHtml;
    private static volatile String sPixelLatestHtml;
    private static volatile String sPixelFlashHtml;
    private static volatile String sPixelFactoryImageHtml;
    private static volatile String sFlashKey;
    private static volatile String sLatestBuildUrl;

    private static final String ANDROID_FLASH_URL = "https://flash.android.com/";
    private static final String ANDROID_DEV_URL = "https://developer.android.com/";
    private static final String ANDROID_DEV_VERSIONS_URL = ANDROID_DEV_URL + "about/versions";
    private static final String PIXEL_SEC_BULLETIN_URL =
            "https://source.android.com/docs/security/bulletin/pixel";
    private static final String PIXEL_STATION_URL =
            "https://content-flashstation-pa.googleapis.com/v1/builds";

    private static Map<String, String> sBetaDevices;

    private static class Regex {
        private static final Pattern DEVICE_ROW = Pattern.compile(
                "<tr\\s+id=\"([^\"]+)\"[^>]*>\\s*<td>([^<]+)</td>",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern DATA_CLIENT_CONFIG_PATTERN = Pattern.compile(
                "data-client-config\\s*=\\s*\"(?:[^,]*,){2}\\s*&quot;([^&]+)&quot;",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern RC_NAME =
                Pattern.compile("\"releaseCandidateName\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern BUILD_ID =
                Pattern.compile("\"buildId\"\\s*:\\s*\"([^\"]+)\"");
        private static final String SECURITY_PATCH_TEMPLATE = "<td>%s</td>\\s*<td>([^<]+)</td>";
        private static final Pattern CANARY_ID = Pattern.compile("\"id\"\\s*:\\s*\"canary-([^\"]+)\"");
        private static final Pattern IS_CANARY_PATTERN = Pattern.compile(
                "\\{[^}]*\"canary\"\\s*:\\s*true[^}]*\\}",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
    }

    private static String getHtml(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static String unescapeHtml(String s) {
        if (s == null) return null;
        return s.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static List<String> extractLinks(String html, Pattern filter) {
        List<String> links = new ArrayList<>();
        Matcher m = filter.matcher(html);
        while (m.find()) {
            links.add(m.group());
        }
        return links;
    }

    private static String pickLastSorted(List<String> links) {
        if (links == null || links.isEmpty()) return null;
        links.sort(String::compareTo);
        return links.get(links.size() - 1);
    }

    private static void getLatestCanary() throws IOException {
        sPixelVersionsHtml = getHtml(ANDROID_DEV_VERSIONS_URL, null);
        Pattern p = Pattern.compile(Pattern.quote(ANDROID_DEV_VERSIONS_URL) + "/.*[0-9]");
        List<String> links = extractLinks(sPixelVersionsHtml, p);
        sLatestBuildUrl = pickLastSorted(links);
        if (sLatestBuildUrl == null) {
            throw new IOException("Failed to get latest build URL");
        }
    }

    private static void getFactoryImageInformation() throws IOException {
        sPixelLatestHtml = getHtml(sLatestBuildUrl, null);
        Matcher m = Pattern.compile("href=\"(.*download.*)\"").matcher(sPixelLatestHtml);
        String factoryImageUrl = m.find() ? m.group(1) : null;
        if (factoryImageUrl == null || factoryImageUrl.isEmpty()) {
            throw new IOException("Failed to find factory image URL");
        }
        sPixelFactoryImageHtml = getHtml(ANDROID_DEV_URL + factoryImageUrl, null);
    }

    private static void parsePixelFiDevices() {
        Map<String, String> devices = new LinkedHashMap<>();
        Matcher m = Regex.DEVICE_ROW.matcher(sPixelFactoryImageHtml);
        while (m.find()) {
            String product = Objects.requireNonNull(m.group(1)).trim() + "_beta";
            String name = Objects.requireNonNull(m.group(2)).trim();
            devices.put(name, product);
        }
        sBetaDevices = devices;
    }

    private static void getBuildInfo(String deviceName, Map<String, String> out) throws Exception {
        sPixelFlashHtml = getHtml(ANDROID_FLASH_URL, null);
        String product = sBetaDevices.get(deviceName);
        if (product == null) throw new IllegalStateException("Unknown device: " + deviceName);
        String device = product.replace("_beta", "");

        Matcher m = Regex.DATA_CLIENT_CONFIG_PATTERN.matcher(sPixelFlashHtml);
        if (!m.find()) {
            Matcher mKey = Pattern.compile("AIza[0-9A-Za-z_-]{35}").matcher(sPixelFlashHtml);
            if (!mKey.find()) {
                throw new IllegalStateException("Failed to extract flash key");
            }
            sFlashKey = mKey.group();
        } else {
            sFlashKey = unescapeHtml(m.group(1));
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", ANDROID_FLASH_URL);

        String stationJson = getHtml(
                PIXEL_STATION_URL + "?product=" + product + "&key=" + sFlashKey,
                headers
        );

        String buildId = null;
        String buildIncremental = null;
        Matcher rc = Regex.RC_NAME.matcher(stationJson);
        Matcher bi = Regex.BUILD_ID.matcher(stationJson);
        while (rc.find() && bi.find()) {
            buildId = rc.group(1);
            buildIncremental = bi.group(1);
        }
        if (buildId == null || buildIncremental == null) {
            throw new IllegalStateException("Failed to parse build info");
        }

        String securityPatch = null;
        String canaryId = null;
        try {
            Matcher canaryMatcher = Regex.IS_CANARY_PATTERN.matcher(stationJson);
            String lastCanaryObject = null;
            while (canaryMatcher.find()) {
                lastCanaryObject = canaryMatcher.group();
            }
            if (lastCanaryObject != null) {
                Matcher idMatcher = Regex.CANARY_ID.matcher(lastCanaryObject);
                if (idMatcher.find()) {
                    canaryId = idMatcher.group(1);
                }
            }
            if (canaryId != null && canaryId.length() >= 4) {
                canaryId = canaryId.substring(0, 4) + "-" + canaryId.substring(4);
            }
            if (canaryId != null) {
                String secHtml = getHtml(PIXEL_SEC_BULLETIN_URL, null);
                Pattern sp = Pattern.compile(
                        String.format(Regex.SECURITY_PATCH_TEMPLATE, Pattern.quote(canaryId)),
                        Pattern.CASE_INSENSITIVE
                );
                Matcher sm = sp.matcher(secHtml);
                securityPatch = sm.find() ? sm.group(1) : null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not resolve security patch from bulletin", e);
        }
        if (securityPatch == null && canaryId != null) {
            securityPatch = canaryId + "-05";
        }

        String buildFingerprint = "google/" + product + "/" + device
                + ":CANARY/" + buildId + "/" + buildIncremental + ":user/release-keys";

        out.clear();
        out.put("MANUFACTURER", "Google");
        out.put("MODEL", deviceName);
        out.put("FINGERPRINT", buildFingerprint);
        out.put("BRAND", "google");
        out.put("PRODUCT", product);
        out.put("DEVICE", device);
        out.put("VERSION.RELEASE", "16");
        out.put("ID", buildId);
        out.put("VERSION.INCREMENTAL", buildIncremental);
        out.put("TYPE", "user");
        out.put("TAGS", "release-keys");
        if (securityPatch != null) {
            out.put("VERSION.SECURITY_PATCH", securityPatch);
        }
        out.put("VERSION.DEVICE_INITIAL_SDK_INT", "32");
    }

    private static List<String> getDevices() throws Exception {
        getLatestCanary();
        getFactoryImageInformation();
        parsePixelFiDevices();
        return new ArrayList<>(sBetaDevices.keySet());
    }

    private static <T> T randomFromList(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List is empty");
        }
        int index = ThreadLocalRandom.current().nextInt(list.size());
        return list.get(index);
    }

    private static <T> T pickRandomExcluding(List<T> list, T exclude) {
        if (list == null || list.size() < 2) return null;
        T chosen;
        do {
            chosen = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        } while (Objects.equals(chosen, exclude));
        return chosen;
    }

    public static Map<String, String> getRandomFingerprint(String exclude) throws Exception {
        List<String> devices = getDevices();
        String selected = pickRandomExcluding(devices, exclude);
        if (selected == null) {
            selected = randomFromList(devices);
        }
        Map<String, String> buildMeta = new LinkedHashMap<>();
        getBuildInfo(selected, buildMeta);
        return buildMeta;
    }

    public static Map<String, String> getRandomFingerprint() throws Exception {
        List<String> devices = getDevices();
        Map<String, String> buildMeta = new LinkedHashMap<>();
        getBuildInfo(randomFromList(devices), buildMeta);
        return buildMeta;
    }
}
