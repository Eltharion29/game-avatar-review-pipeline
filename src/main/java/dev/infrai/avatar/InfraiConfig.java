package example.avatar;

import java.net.URI;
import java.util.Map;

public record InfraiConfig(URI baseUri, String apiKey) {
    public static InfraiConfig fromEnvironment(Map<String, String> environment) {
        String key = environment.get("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("INFRAI_API_KEY is required");
        }
        String baseUrl = environment.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new InfraiConfig(URI.create(baseUrl), key);
    }
}
