package org.homewifimonitor.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RouterClient {
    private static final String DEFAULT_DEVICE_ID = "fallback-device-id";
    private static final String DEFAULT_KEY = "a2ffa5c9be07488bbb04a3a47d3c5f6a";
    private static final long TOKEN_TTL_MS = 20L * 60L * 1000L;
    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("var\\s+deviceId\\s*=\\s*'([^']+)'");
    private static final Pattern KEY_PATTERN = Pattern.compile("key:\\s*'([^']+)'");

    private final String host;
    private final String password;
    private final String baseUrl;
    private final DiagnosticsLog diagnostics;
    private String token = "";
    private long tokenCreatedAt;
    private JSONObject deviceListCache = new JSONObject();
    private long deviceListCacheAt;
    private JSONObject statusCache = new JSONObject();
    private long statusCacheAt;
    private JSONObject topoCache = new JSONObject();
    private long topoCacheAt;

    RouterClient(String host, String password) {
        this(host, password, new DiagnosticsLog(AppConfig.DIAGNOSTIC_LOG_LIMIT));
    }

    RouterClient(String host, String password, DiagnosticsLog diagnostics) {
        this.host = normalizeHost(host);
        this.password = password == null ? "" : password;
        this.baseUrl = "http://" + this.host;
        this.diagnostics = diagnostics == null ? new DiagnosticsLog(AppConfig.DIAGNOSTIC_LOG_LIMIT) : diagnostics;
    }

    Snapshot snapshot() throws IOException, JSONException {
        long startedAt = System.currentTimeMillis();
        ensureToken();
        JSONObject deviceList = cachedDeviceList();
        JSONObject status = statusAfterDeviceList();
        JSONObject topo = cachedTopo();

        List<Device> devices = normalizeDevices(deviceList);
        mergeStatusRates(devices, status);
        annotateConnections(devices, topo);
        devices.sort((a, b) -> Double.compare(b.downBps + b.upBps, a.downBps + a.upBps));

        Snapshot snapshot = new Snapshot();
        snapshot.routerHost = host;
        snapshot.routerName = routerName(topo);
        snapshot.sampledAtMillis = System.currentTimeMillis();
        snapshot.devices = devices;
        snapshot.totalCount = devices.size();
        snapshot.onlineCount = intOrFallback(status.opt("count"), devices.size());
        snapshot.activeCount = 0;
        snapshot.wifiCount = 0;
        snapshot.meshCount = 0;
        for (Device device : devices) {
            if (device.isActive) snapshot.activeCount++;
            if (device.isMeshNode) snapshot.meshCount++;
            else snapshot.wifiCount++;
        }
        snapshot.wan = summarizeWan(status, devices);
        diagnostics.add("snapshot ok devices=" + snapshot.totalCount + " active=" + snapshot.activeCount + " ms=" + elapsedSince(startedAt));
        return snapshot;
    }

    private void ensureToken() throws IOException, JSONException {
        if (token.isEmpty() || System.currentTimeMillis() - tokenCreatedAt > TOKEN_TTL_MS) {
            login();
        }
    }

    private void login() throws IOException, JSONException {
        if (password.trim().isEmpty()) {
            throw new IOException("请先输入小米路由器管理密码");
        }

        long startedAt = System.currentTimeMillis();
        diagnostics.add("login start host=" + host);
        try {
            String html = fetchText(baseUrl + "/cgi-bin/luci/web", "GET", null, AppConfig.ROUTER_LOGIN_TIMEOUT_MS);
            String deviceId = firstMatch(DEVICE_ID_PATTERN, html, DEFAULT_DEVICE_ID);
            String key = firstMatch(KEY_PATTERN, html, DEFAULT_KEY);
            long timestamp = System.currentTimeMillis() / 1000L;
            int random = (int) Math.floor(Math.random() * 10_000);
            String nonce = String.format(Locale.US, "0_%s_%d_%d", deviceId, timestamp, random);
            String passwordHash = sha1(nonce + sha1(password + key));

            Map<String, String> form = new HashMap<>();
            form.put("username", "admin");
            form.put("password", passwordHash);
            form.put("logtype", "2");
            form.put("nonce", nonce);

            JSONObject json = fetchJson(baseUrl + "/cgi-bin/luci/api/xqsystem/login", "POST", encodeForm(form), AppConfig.ROUTER_LOGIN_TIMEOUT_MS);
            if (json.optInt("code", -1) != 0 || json.optString("token", "").isEmpty()) {
                throw new IOException(json.optString("msg", "路由器登录失败，请检查密码"));
            }
            token = json.optString("token");
            tokenCreatedAt = System.currentTimeMillis();
            diagnostics.add("login ok ms=" + elapsedSince(startedAt));
        } catch (IOException | JSONException error) {
            diagnostics.add("login fail ms=" + elapsedSince(startedAt) + " " + shortError(error));
            throw error;
        }
    }

    private JSONObject api(String path, boolean retry) throws IOException, JSONException {
        ensureToken();
        long startedAt = System.currentTimeMillis();
        try {
            JSONObject json = fetchJson(baseUrl + "/cgi-bin/luci/;stok=" + token + "/" + path, "GET", null, timeoutForApi(path));
            diagnostics.add(apiLabel(path) + " ok ms=" + elapsedSince(startedAt));
            if (json.optInt("code", 0) == 401 && retry) {
                token = "";
                diagnostics.add(apiLabel(path) + " retry auth");
                return api(path, false);
            }
            return json;
        } catch (IOException | JSONException error) {
            diagnostics.add(apiLabel(path) + " fail ms=" + elapsedSince(startedAt) + " " + shortError(error));
            throw error;
        }
    }

    private JSONObject statusWithFallback() throws IOException, JSONException {
        long now = System.currentTimeMillis();
        try {
            statusCache = api("api/xqsystem/status", true);
            statusCacheAt = now;
            return statusCache;
        } catch (IOException | JSONException error) {
            if (statusCacheAt > 0 && now - statusCacheAt <= AppConfig.STATUS_CACHE_MAX_STALE_MS) {
                diagnostics.add("status cache age=" + (now - statusCacheAt) + "ms");
                return statusCache;
            }
            throw error;
        }
    }

    private JSONObject statusAfterDeviceList() throws IOException, JSONException {
        try {
            return statusWithFallback();
        } catch (IOException | JSONException error) {
            if (deviceListCacheAt > 0) {
                diagnostics.add("status empty fallback " + shortError(error));
                return new JSONObject();
            }
            throw error;
        }
    }

    private JSONObject cachedDeviceList() throws IOException, JSONException {
        long now = System.currentTimeMillis();
        if (deviceListCacheAt > 0 && now - deviceListCacheAt < AppConfig.DEVICE_LIST_REFRESH_MS) {
            diagnostics.add("devicelist cache age=" + (now - deviceListCacheAt) + "ms");
            return deviceListCache;
        }

        try {
            deviceListCache = api("api/misystem/devicelist", true);
            deviceListCacheAt = now;
        } catch (IOException | JSONException error) {
            if (deviceListCacheAt > 0) {
                diagnostics.add("devicelist cache fallback age=" + (now - deviceListCacheAt) + "ms");
                return deviceListCache;
            }
            throw error;
        }
        return deviceListCache;
    }

    private JSONObject cachedTopo() {
        long now = System.currentTimeMillis();
        if (topoCacheAt > 0 && now - topoCacheAt < AppConfig.TOPO_REFRESH_MS) {
            diagnostics.add("topo cache age=" + (now - topoCacheAt) + "ms");
            return topoCache;
        }

        try {
            topoCache = api("api/misystem/topo_graph", true);
            topoCacheAt = now;
        } catch (Exception ignored) {
            diagnostics.add("topo cache fallback " + shortError(ignored));
            if (topoCache == null) topoCache = new JSONObject();
            topoCacheAt = now;
        }
        return topoCache;
    }

    private static List<Device> normalizeDevices(JSONObject payload) {
        List<Device> devices = new ArrayList<>();
        JSONArray list = payload.optJSONArray("list");
        if (list == null) return devices;

        for (int i = 0; i < list.length(); i++) {
            JSONObject row = list.optJSONObject(i);
            if (row == null) continue;

            JSONObject stats = row.optJSONObject("statistics");
            JSONArray ips = row.optJSONArray("ip");
            double downRaw = stats != null && stats.has("downspeed") ? number(stats.opt("downspeed")) : sumIpRate(ips, "downspeed");
            double upRaw = stats != null && stats.has("upspeed") ? number(stats.opt("upspeed")) : sumIpRate(ips, "upspeed");

            Device device = new Device();
            device.mac = row.optString("mac", "").toUpperCase(Locale.US);
            device.displayName = pickDisplayName(row);
            device.originalName = row.optString("oname", "");
            device.ip = pickIp(ips);
            device.downBps = downRaw;
            device.upBps = upRaw;
            device.downMbps = bytesPerSecondToMbps(downRaw);
            device.upMbps = bytesPerSecondToMbps(upRaw);
            device.isMeshNode = number(row.opt("isap")) > 0;
            device.isActive = downRaw > 0 || upRaw > 0;
            String parent = row.optString("parent", "");
            device.connectionLabel = device.isMeshNode
                    ? "Mesh 节点"
                    : parent.isEmpty() ? "主路由直连" : "via " + parent.toUpperCase(Locale.US);
            devices.add(device);
        }
        return devices;
    }

    private static void mergeStatusRates(List<Device> devices, JSONObject status) {
        Map<String, JSONObject> byMac = new HashMap<>();
        JSONArray rows = status.optJSONArray("devStatistics");
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                byMac.put(row.optString("mac", "").toUpperCase(Locale.US), row);
            }
        }

        for (Device device : devices) {
            JSONObject row = byMac.get(device.mac);
            if (row == null) continue;
            device.downBps = number(row.opt("downspeed"));
            device.upBps = number(row.opt("upspeed"));
            device.downMbps = bytesPerSecondToMbps(device.downBps);
            device.upMbps = bytesPerSecondToMbps(device.upBps);
            device.maxDownMbps = bytesPerSecondToMbps(number(row.opt("maxdownloadspeed")));
            device.maxUpMbps = bytesPerSecondToMbps(number(row.opt("maxuploadspeed")));
            device.isActive = device.downBps > 0 || device.upBps > 0;
        }
    }

    static WanStats summarizeWan(JSONObject status, List<Device> devices) {
        JSONObject wan = status.optJSONObject("wanStatistics");
        if (wan == null) wan = status.optJSONObject("wan");
        if (wan == null) wan = new JSONObject();

        WanStats stats = new WanStats();
        stats.downMbps = bytesPerSecondToMbps(number(wan.opt("downspeed")));
        stats.upMbps = bytesPerSecondToMbps(number(wan.opt("upspeed")));
        stats.maxDownMbps = bytesPerSecondToMbps(number(wan.opt("maxdownloadspeed")));
        stats.maxUpMbps = bytesPerSecondToMbps(number(wan.opt("maxuploadspeed")));
        if (stats.downMbps <= 0 && stats.upMbps <= 0 && devices != null) {
            double downBps = 0;
            double upBps = 0;
            for (Device device : devices) {
                downBps += device.downBps;
                upBps += device.upBps;
            }
            stats.downMbps = bytesPerSecondToMbps(downBps);
            stats.upMbps = bytesPerSecondToMbps(upBps);
        }
        return stats;
    }

    private static void annotateConnections(List<Device> devices, JSONObject topo) {
        Map<String, String> names = new HashMap<>();
        for (Device device : devices) {
            if (!device.mac.isEmpty()) names.put(device.mac, device.displayName);
        }

        JSONObject graph = topo.optJSONObject("graph");
        JSONArray leafs = graph == null ? null : graph.optJSONArray("leafs");
        if (leafs != null) {
            for (int i = 0; i < leafs.length(); i++) {
                JSONObject leaf = leafs.optJSONObject(i);
                if (leaf == null) continue;
                String mac = leaf.optString("mac", "").toUpperCase(Locale.US);
                if (mac.isEmpty()) continue;
                String name = firstNonEmpty(leaf.optString("locale", ""), leaf.optString("name", ""), leaf.optString("ssid", ""), mac);
                names.put(mac, name);
            }
        }

        for (Device device : devices) {
            if (device.isMeshNode) {
                device.connectionLabel = device.displayName.contains("MiWiFi") ? "Mesh 节点" : device.displayName + " Mesh 节点";
                continue;
            }
            if (!device.connectionLabel.startsWith("via ")) continue;
            String parent = device.connectionLabel.substring(4).toUpperCase(Locale.US);
            device.connectionLabel = "经由 " + firstNonEmpty(names.get(parent), parent);
        }
    }

    private static String routerName(JSONObject topo) {
        JSONObject graph = topo.optJSONObject("graph");
        if (graph == null) return "";
        return firstNonEmpty(graph.optString("name", ""), graph.optString("ssid", ""));
    }

    private static int timeoutForApi(String path) {
        if (path.contains("xqsystem/status")) return AppConfig.ROUTER_STATUS_TIMEOUT_MS;
        if (path.contains("topo_graph")) return AppConfig.ROUTER_TOPO_TIMEOUT_MS;
        if (path.contains("devicelist")) return AppConfig.ROUTER_DEVICE_LIST_TIMEOUT_MS;
        return AppConfig.ROUTER_DEVICE_LIST_TIMEOUT_MS;
    }

    private static String fetchText(String url, String method, String body, int timeoutMs) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");

        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = stream == null ? "" : readUtf8(stream);
        connection.disconnect();

        if (code >= 400) {
            throw new IOException("路由器返回 HTTP " + code + ": " + snippet(text));
        }
        return text;
    }

    private static JSONObject fetchJson(String url, String method, String body, int timeoutMs) throws IOException, JSONException {
        String text = fetchText(url, method, body, timeoutMs);
        try {
            return new JSONObject(text);
        } catch (JSONException error) {
            throw new JSONException("路由器返回的不是 JSON: " + snippet(text));
        }
    }

    private static String readUtf8(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }

    private static String encodeForm(Map<String, String> form) throws IOException {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (!first) builder.append('&');
            first = false;
            builder
                    .append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return builder.toString();
    }

    private static String sha1(String value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("当前 Android 系统不支持 SHA-1", error);
        }
    }

    private static String pickDisplayName(JSONObject device) {
        return firstNonEmpty(device.optString("name", ""), device.optString("oname", ""), device.optString("mac", ""), "未知设备");
    }

    private static String pickIp(JSONArray ips) {
        if (ips == null) return "";
        String first = "";
        for (int i = 0; i < ips.length(); i++) {
            JSONObject row = ips.optJSONObject(i);
            if (row == null) continue;
            String ip = row.optString("ip", "");
            if (first.isEmpty() && !ip.isEmpty()) first = ip;
            if (row.optBoolean("active", false) && !ip.isEmpty()) return ip;
        }
        return first;
    }

    private static double sumIpRate(JSONArray ips, String key) {
        if (ips == null) return 0;
        double sum = 0;
        for (int i = 0; i < ips.length(); i++) {
            JSONObject row = ips.optJSONObject(i);
            if (row != null) sum += number(row.opt(key));
        }
        return sum;
    }

    private static double bytesPerSecondToMbps(double value) {
        if (!Double.isFinite(value)) return 0;
        return Math.round((value * 8d / 1_000_000d) * 1000d) / 1000d;
    }

    private static double number(Object value) {
        if (value == null || value == JSONObject.NULL) return 0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static int intOrFallback(Object value, int fallback) {
        int numeric = (int) Math.round(number(value));
        return numeric <= 0 ? fallback : numeric;
    }

    private static String firstMatch(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return "";
    }

    private static String normalizeHost(String host) {
        String value = host == null ? "" : host.trim();
        value = value.replace("http://", "").replace("https://", "");
        int slash = value.indexOf('/');
        return slash >= 0 ? value.substring(0, slash) : value;
    }

    private static String snippet(String text) {
        if (text == null) return "";
        String compact = text.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > 140 ? compact.substring(0, 140) + "..." : compact;
    }

    private static String apiLabel(String path) {
        if (path.contains("xqsystem/status")) return "status";
        if (path.contains("misystem/devicelist")) return "devicelist";
        if (path.contains("misystem/topo_graph")) return "topo";
        return path;
    }

    private static long elapsedSince(long startedAt) {
        return Math.max(0, System.currentTimeMillis() - startedAt);
    }

    private static String shortError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
        return snippet(message);
    }
}
