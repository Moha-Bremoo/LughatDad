import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.Executors;

/**
 * LughatDad HTTP Server
 * Wraps the JavaCC-compiled Arabic interpreter.
 *
 * Each run request:
 *   1. Writes code to a temp file
 *   2. Spawns: java LughatDad <tempfile>  (subprocess, isolated)
 *   3. Captures stdout/stderr and returns as JSON
 *
 * This approach is safe regardless of STATIC=true in the .jj grammar.
 *
 * Endpoints:
 *   GET  /          → serves frontend (../webapp/index.html)
 *   POST /api/run   → {"code":"..."}  →  {"success":bool,"output":[...],"error":...}
 *   GET  /api/health → {"status":"ok"}
 */
public class LughatDadServer {

    static final int  DEFAULT_PORT = 5050;
    // Server is always started from the backend/ directory.
    // Use CWD-based paths — simple and reliable.
    static final Path BACKEND_DIR = Paths.get("").toAbsolutePath();
    static final Path CLASS_DIR   = BACKEND_DIR;
    static final Path WEBAPP_DIR  = BACKEND_DIR.resolve("../webapp").normalize();

    // ── main ────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        int port = resolvePort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/api/run",    new RunHandler());
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/",           new StaticHandler());
        server.start();

        System.out.println("================================================");
        System.out.println("  لغة ضاد — LughatDad Server");
        System.out.println("  URL:      http://localhost:" + port);
        System.out.println("  Frontend: " + WEBAPP_DIR);
        System.out.println("  Classes:  " + CLASS_DIR);
        System.out.println("================================================");
    }

    static int resolvePort() {
        String envPort = System.getenv("PORT");
        if (envPort == null || envPort.isBlank()) return DEFAULT_PORT;
        try {
            return Integer.parseInt(envPort.trim());
        } catch (NumberFormatException ignored) {
            return DEFAULT_PORT;
        }
    }

    // ── POST /api/run ─────────────────────────────────────────────────
    static class RunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            try {
                String body = new String(
                    ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String code = extractJson(body, "code");
                if (code == null) code = "";

                RunResult result = runSubprocess(code);
                String json = toJson(result);

                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type",
                    "application/json; charset=UTF-8");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }

            } catch (Exception e) {
                String errJson = "{\"success\":false,\"output\":[]," +
                    "\"error\":\"" + jsonEsc(e.getMessage()) + "\"}";
                byte[] bytes = errJson.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type",
                    "application/json; charset=UTF-8");
                ex.sendResponseHeaders(500, bytes.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
            } finally {
                ex.close();
            }
        }
    }

    // ── GET /api/health ───────────────────────────────────────────────
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCors(ex);
            String json = "{\"status\":\"ok\",\"compiler\":\"LughatDad.jj\"," +
                "\"javacc\":\"7.0.13\",\"version\":\"1.0\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
            ex.close();
        }
    }

    // ── GET /* — serve static webapp files ───────────────────────────
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String reqPath = ex.getRequestURI().getPath();
            if (reqPath.equals("/") || reqPath.isEmpty()) reqPath = "/index.html";

            Path file = WEBAPP_DIR.resolve(reqPath.substring(1)).normalize();
            if (!Files.exists(file) || Files.isDirectory(file)) {
                file = WEBAPP_DIR.resolve("index.html");
            }

            if (!Files.exists(file)) {
                String msg = "Not found: " + reqPath;
                ex.sendResponseHeaders(404, msg.length());
                ex.getResponseBody().write(msg.getBytes());
                ex.close();
                return;
            }

            byte[] data = Files.readAllBytes(file);
            ex.getResponseHeaders().set("Content-Type", guessMime(file.toString()));
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(data); }
            ex.close();
        }

        static String guessMime(String n) {
            if (n.endsWith(".html")) return "text/html; charset=UTF-8";
            if (n.endsWith(".css"))  return "text/css";
            if (n.endsWith(".js"))   return "application/javascript";
            if (n.endsWith(".json")) return "application/json";
            if (n.endsWith(".png"))  return "image/png";
            if (n.endsWith(".svg"))  return "image/svg+xml";
            if (n.endsWith(".ico"))  return "image/x-icon";
            return "application/octet-stream";
        }
    }

    // ── Run LughatDad as an isolated subprocess ───────────────────────
    /**
     * Writes code to a temp .txt file, then spawns:
     *   java -cp <CLASS_DIR> LughatDad
     *
     * The LughatDad main() is modified (via RunnerMain) to read from stdin,
     * so we feed the code via the process's stdin pipe.
     */
    static RunResult runSubprocess(String code) {
        Path tmp = null;
        try {
            // Write code to a temp file
            tmp = Files.createTempFile("lughatdad_", ".txt");
            Files.writeString(tmp, code, StandardCharsets.UTF_8);

            // Build process — note: JVM flags (-cp, -D) MUST come before class name
            ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-Dfile.encoding=UTF-8",
                "-cp", CLASS_DIR.toAbsolutePath().toString(),
                "LughatDadRunner",
                tmp.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true); // merge stderr into stdout
            pb.environment().put("JAVA_TOOL_OPTIONS", "");

            Process proc = pb.start();

            // Read output
            String output = new String(
                proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            // Wait max 10 seconds
            boolean done = proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) {
                proc.destroyForcibly();
                return new RunResult(false, new String[0],
                    "خطأ: انتهت مهلة التنفيذ (أكثر من 10 ثوانٍ)");
            }

            int exitCode = proc.exitValue();

            // Determine success: exit 0 and no Arabic error prefix
            boolean isError = exitCode != 0 ||
                output.startsWith("خطأ") ||
                output.startsWith("Syntax Error") ||
                output.startsWith("Runtime Error") ||
                output.startsWith("Error:");

            // Strip "Reading from..." and "Success!..." lines added by main()
            String[] allLines = output.split("\\r?\\n");
            java.util.List<String> cleanLines = new java.util.ArrayList<>();
            for (String line : allLines) {
                if (line.isEmpty()) continue;
                if (line.startsWith("Reading from")) continue;
                if (line.startsWith("Success! Grammar")) continue;
                if (line.startsWith(">> Executing")) continue;
                if (line.startsWith("---")) continue;
                if (line.startsWith("Picked up JAVA_TOOL_OPTIONS")) continue;
                if (line.startsWith("Picked up _JAVA_OPTIONS")) continue;
                cleanLines.add(line);
            }

            if (isError) {
                String errMsg = cleanLines.isEmpty() ? output :
                    String.join("\n", cleanLines);
                return new RunResult(false, new String[0], errMsg);
            }

            return new RunResult(true,
                cleanLines.toArray(new String[0]), null);

        } catch (Exception e) {
            return new RunResult(false, new String[0],
                "خطأ في التنفيذ: " + e.getMessage());
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    // ── Data class ────────────────────────────────────────────────────
    static class RunResult {
        boolean  success;
        String[] output;
        String   error;
        RunResult(boolean s, String[] o, String e) {
            success = s; output = o; error = e;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────
    static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    static String toJson(RunResult r) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"success\":").append(r.success).append(",");
        sb.append("\"output\":[");
        for (int i = 0; i < r.output.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(jsonEsc(r.output[i])).append("\"");
        }
        sb.append("],");
        if (r.error == null) sb.append("\"error\":null");
        else sb.append("\"error\":\"").append(jsonEsc(r.error)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    /** Minimal JSON string extraction — handles unicode escapes (backslash-uXXXX) */
    static String extractJson(String json, String key) {
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                switch (n) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':  // decode backslash-uXXXX unicode escapes
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append('\\'); sb.append(n);
                            }
                        }
                        break;
                    default: sb.append('\\'); sb.append(n);
                }
            } else if (c == '"') break;
            else sb.append(c);
        }
        return sb.toString();
    }

    static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
    }
}
