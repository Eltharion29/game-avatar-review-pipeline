package example.avatar;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public final class AvatarPublisherMain {
    private AvatarPublisherMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: AvatarPublisherMain <event-id> <user-id> <image-path> <approved|pending>");
            System.exit(2);
        }
        AvatarSubmission.ReviewDecision decision = AvatarSubmission.ReviewDecision.valueOf(args[3].toUpperCase(Locale.ROOT));
        AvatarSubmission submission = new AvatarSubmission(args[0], args[1], Path.of(args[2]), decision, UUID.randomUUID().toString());
        AvatarPipeline pipeline = new AvatarPipeline(new InfraiMediaClient(InfraiConfig.fromEnvironment(System.getenv())));
        AvatarPipeline.Result result = pipeline.process(submission);
        String suffix = result.imageId() == null ? "" : " image=" + result.imageId();
        System.out.println(result.status() + " user=" + result.userId() + suffix);
    }
}
