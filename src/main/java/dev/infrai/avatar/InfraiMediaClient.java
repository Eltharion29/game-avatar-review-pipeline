package example.avatar;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public final class InfraiMediaClient implements AvatarPipeline.AvatarPublisher {
    private static final int MAX_ATTEMPTS = 4;
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiMediaClient(InfraiConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String publish(AvatarSubmission submission) throws IOException, InterruptedException {
        String uploaded = upload(submission.imagePath(), submission.requestId());
        String cropped = postJson("/v1/image/smart_crop",
                "{\"image\":" + Json.quote(uploaded) + ",\"aspect\":\"1:1\"}", submission.requestId());
        String optimized = postJson("/v1/image/resize",
                "{\"image\":" + Json.quote(cropped) + ",\"width\":256,\"height\":256," +
                "\"fit\":\"cover\",\"enlarge\":false,\"format\":\"webp\",\"store\":true}", submission.requestId());
        patchUser(submission.userId(), submission.eventId(), optimized, submission.requestId());
        return optimized;
    }

    private String upload(Path file, String requestId) throws IOException, InterruptedException {
        String boundary = "infrai-" + requestId.replaceAll("[^A-Za-z0-9]", "");
        byte[] bytes = Files.readAllBytes(file);
        String filename = file.getFileName().toString();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"filename\"\r\n\r\n" +
                filename + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" +
                filename.replace("\"", "") + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofByteArrays(java.util.List.of(head, bytes, tail));
        return send("POST", "/v1/image/upload", body, "multipart/form-data; boundary=" + boundary, requestId);
    }

    private String postJson(String path, String body, String requestId) throws IOException, InterruptedException {
        return send("POST", path, HttpRequest.BodyPublishers.ofString(body), "application/json", requestId);
    }

    private void patchUser(String userId, String eventId, String imageId, String requestId) throws IOException, InterruptedException {
        String body = "{\"user_id\":" + Json.quote(userId) +
                ",\"metadata\":{\"avatar_image_id\":" + Json.quote(imageId) +
                ",\"avatar_live_event_id\":" + Json.quote(eventId) + "}}";
        send("PATCH", "/v1/auth/user/update/" + encodePath(userId),
                HttpRequest.BodyPublishers.ofString(body), "application/json", requestId);
    }

    private String send(String method, String path, HttpRequest.BodyPublisher body, String contentType, String requestId)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", contentType)
                    .header("Idempotency-Key", requestId)
                    .method(method, body)
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope = envelope(response.body());
            if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) throw apiError(envelope, response.statusCode());
            if (response.statusCode() >= 500) throw new IOException("Infrai transport response " + response.statusCode());
            return imageId(envelope.get("data"));
        }
        throw new IOException("Retry budget exhausted");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> envelope(String body) throws IOException {
        try {
            Object parsed = Json.parse(body);
            if (parsed instanceof Map<?, ?> map) return (Map<String, Object>) map;
            throw new IllegalArgumentException("Envelope is not an object");
        } catch (RuntimeException exception) {
            throw new IOException("Invalid JSON response", exception);
        }
    }

    private static InfraiException apiError(Map<String, Object> envelope, int status) {
        Object raw = envelope.get("error");
        if (raw instanceof Map<?, ?> error) {
            Object rawCode = error.get("code");
            Object rawMessage = error.get("message");
            String code = rawCode == null ? "REQUEST_REJECTED" : String.valueOf(rawCode);
            String message = rawMessage == null ? "Request rejected" : String.valueOf(rawMessage);
            return new InfraiException(code, message, status);
        }
        return new InfraiException("REQUEST_REJECTED", "Request rejected", status);
    }

    private static String imageId(Object data) throws IOException {
        if (data instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id == null) id = map.get("image");
            if (id != null) return String.valueOf(id);
        }
        throw new IOException("Response data did not contain an image identifier");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> parseRetryAfter(value, attempt))
                .orElse(250L * (1L << attempt));
    }

    private static long parseRetryAfter(String value, int attempt) {
        try { return Math.max(0L, Long.parseLong(value.trim()) * 1000L); }
        catch (NumberFormatException ignored) { return 250L * (1L << attempt); }
    }

    private static String encodePath(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
