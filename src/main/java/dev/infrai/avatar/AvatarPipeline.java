package example.avatar;

public final class AvatarPipeline {
    public interface AvatarPublisher {
        String publish(AvatarSubmission submission) throws Exception;
    }

    public record Result(Status status, String userId, String imageId) {
        public enum Status { PUBLISHED, HELD_FOR_REVIEW }
    }

    private final AvatarPublisher publisher;

    public AvatarPipeline(AvatarPublisher publisher) { this.publisher = publisher; }

    public Result process(AvatarSubmission submission) throws Exception {
        if (submission.decision() != AvatarSubmission.ReviewDecision.APPROVED) {
            return new Result(Result.Status.HELD_FOR_REVIEW, submission.userId(), null);
        }
        String imageId = publisher.publish(submission);
        return new Result(Result.Status.PUBLISHED, submission.userId(), imageId);
    }
}
