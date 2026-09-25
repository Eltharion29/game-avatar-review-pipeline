package example.avatar;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public final class AvatarPipelineTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger publishes = new AtomicInteger();
        AvatarPipeline pipeline = new AvatarPipeline(submission -> {
            publishes.incrementAndGet();
            return "img-final-256";
        });

        AvatarPipeline.Result pending = pipeline.process(new AvatarSubmission(
                "tournament-7", "player-42", Path.of("avatar.png"), AvatarSubmission.ReviewDecision.PENDING, "review-1"));
        require(pending.status() == AvatarPipeline.Result.Status.HELD_FOR_REVIEW, "pending item must be held");
        require(publishes.get() == 0, "pending item must not publish");

        AvatarPipeline.Result approved = pipeline.process(new AvatarSubmission(
                "tournament-7", "player-42", Path.of("avatar.png"), AvatarSubmission.ReviewDecision.APPROVED, "review-2"));
        require(approved.status() == AvatarPipeline.Result.Status.PUBLISHED, "approved item must publish");
        require("img-final-256".equals(approved.imageId()), "processed image must be returned");
        require(publishes.get() == 1, "approved item must publish exactly once");
        System.out.println("AvatarPipelineTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
