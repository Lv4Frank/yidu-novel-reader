package qa;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 极简本地 HTTP mock 服务（仅用于 JVM 测试 LlmClient）。
 * 绑定 127.0.0.1 随机端口，记录每个请求（方法/路径/查询/头/体），
 * 由 Harness 设定的 Handler 决定返回（支持 JSON、SSE 分块、延迟、状态码）。
 * 响应统一使用 Connection: close；JSON 带 Content-Length，SSE 不带 Content-Length（以 EOF 结束）。
 */
public final class NetMock implements AutoCloseable {

    public static final class Request {
        public final String method;
        public final String path;
        public final String query;
        public final String body;
        private final Map<String, String> headers;

        Request(String method, String path, String query, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        public Map<String, String> headers() {
            return headers;
        }

        public String queryParam(String name) {
            if (query == null || query.isEmpty()) return null;
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                String key = idx < 0 ? pair : pair.substring(0, idx);
                if (name.equals(decode(key))) return idx < 0 ? "" : decode(pair.substring(idx + 1));
            }
            return null;
        }

        public int queryParamCount(String name) {
            if (query == null || query.isEmpty()) return 0;
            int count = 0;
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                String key = idx < 0 ? pair : pair.substring(0, idx);
                if (name.equals(decode(key))) count++;
            }
            return count;
        }

        @Override
        public String toString() {
            return method + " " + path + (query.isEmpty() ? "" : "?" + query) + " body=" + body;
        }

        private static String decode(String s) {
            try {
                return URLDecoder.decode(s, "UTF-8");
            } catch (Exception e) {
                return s;
            }
        }
    }

    public static final class Response {
        int status = 200;
        String contentType = "application/json; charset=utf-8";
        boolean sendContentLength = true;
        final List<byte[]> parts = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        final Map<String, String> headers = new LinkedHashMap<>();

        public static Response json(int status, String body) {
            Response r = new Response();
            r.status = status;
            r.contentType = "application/json; charset=utf-8";
            r.parts.add(body.getBytes(StandardCharsets.UTF_8));
            r.delays.add(0L);
            return r;
        }

        public static Response sse() {
            Response r = new Response();
            r.status = 200;
            r.contentType = "text/event-stream; charset=utf-8";
            r.sendContentLength = false;
            return r;
        }

        public Response header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Response status(int status) {
            this.status = status;
            return this;
        }

        public Response push(String text) {
            return push(text, 0L);
        }

        public Response push(String text, long delayBeforeMs) {
            parts.add(text.getBytes(StandardCharsets.UTF_8));
            delays.add(delayBeforeMs);
            return this;
        }

        /** 在响应体之前插入等待（用于读超时测试）。 */
        public Response delayBeforeBody(long ms) {
            parts.add(0, new byte[0]);
            delays.add(0, ms);
            return this;
        }
    }

    public interface Handler {
        Response handle(Request request) throws Exception;
    }

    private final ServerSocket serverSocket;
    private final ExecutorService pool;
    private final List<Request> requests = new ArrayList<>();
    private volatile Handler handler;

    public NetMock() throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "net-mock");
            thread.setDaemon(true);
            return thread;
        };
        pool = Executors.newCachedThreadPool(factory);
        pool.submit(this::acceptLoop);
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    public String hostPort() {
        return "127.0.0.1:" + port();
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    public Request lastRequest() {
        synchronized (requests) {
            return requests.isEmpty() ? null : requests.get(requests.size() - 1);
        }
    }

    public List<Request> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    public int requestCount() {
        synchronized (requests) {
            return requests.size();
        }
    }

    public void clearRequests() {
        synchronized (requests) {
            requests.clear();
        }
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                pool.submit(() -> serve(socket));
            } catch (IOException e) {
                if (serverSocket.isClosed()) return;
            }
        }
    }

    private void serve(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(120_000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String target = parts.length > 1 ? parts[1] : "/";
            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = readLine(in)) != null) {
                if (line.isEmpty()) break;
                int idx = line.indexOf(':');
                if (idx > 0) {
                    headers.put(line.substring(0, idx).trim().toLowerCase(Locale.ROOT),
                            line.substring(idx + 1).trim());
                }
            }
            int contentLength = 0;
            String cl = headers.get("content-length");
            if (cl != null && !cl.trim().isEmpty()) {
                try {
                    contentLength = Integer.parseInt(cl.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            byte[] bodyBytes = readN(in, contentLength);
            String path = target;
            String query = "";
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                query = target.substring(q + 1);
            }
            Request request = new Request(method, path, query,
                    java.util.Collections.unmodifiableMap(headers),
                    new String(bodyBytes, StandardCharsets.UTF_8));
            synchronized (requests) {
                requests.add(request);
            }
            Handler h = this.handler;
            Response response;
            if (h == null) {
                response = Response.json(200, "{\"choices\":[{\"message\":{\"content\":\"MOCK-NO-HANDLER\"},\"finish_reason\":\"stop\"}]}");
            } else {
                try {
                    response = h.handle(request);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    String msg = String.valueOf(e.getMessage()).replace("\"", "'");
                    response = Response.json(500, "{\"error\":{\"message\":\"mock handler failed: " + msg + "\"}}");
                }
            }
            if (response == null) response = Response.json(200, "{}");
            writeResponse(s.getOutputStream(), response);
        } catch (IOException ignored) {
            // 客户端取消/断开属于预期情况
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void writeResponse(OutputStream out, Response response) {
        try {
            StringBuilder head = new StringBuilder();
            head.append("HTTP/1.1 ").append(response.status).append(' ')
                    .append(reason(response.status)).append("\r\n");
            head.append("Content-Type: ").append(response.contentType).append("\r\n");
            head.append("Connection: close\r\n");
            for (Map.Entry<String, String> entry : response.headers.entrySet()) {
                head.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
            long total = 0;
            for (byte[] part : response.parts) {
                total += part.length;
            }
            if (response.sendContentLength) {
                head.append("Content-Length: ").append(total).append("\r\n");
            }
            head.append("\r\n");
            out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            for (int i = 0; i < response.parts.size(); i++) {
                long delay = response.delays.get(i);
                if (delay > 0) Thread.sleep(delay);
                byte[] data = response.parts.get(i);
                if (data.length > 0) {
                    out.write(data);
                    out.flush();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // 客户端已断开
        }
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 402: return "Payment Required";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default: return "Status";
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buffer.write(b);
        }
        if (b == -1 && buffer.size() == 0) return null;
        return buffer.toString("UTF-8");
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] data = new byte[n];
        int offset = 0;
        while (offset < n) {
            int read = in.read(data, offset, n - offset);
            if (read < 0) break;
            offset += read;
        }
        if (offset == n) return data;
        byte[] shorter = new byte[offset];
        System.arraycopy(data, 0, shorter, 0, offset);
        return shorter;
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
