package example.avatar;

import java.nio.file.Path;

public record AvatarSubmission(String eventId, String userId, Path imagePath, ReviewDecision decision, String requestId) {
    public AvatarSubmission {
        if (eventId.isBlank() || userId.isBlank() || requestId.isBlank()) {
            throw new IllegalArgumentException("eventId, userId, and requestId are required");
        }
    }

    public enum ReviewDecision { APPROVED, PENDING }
}
