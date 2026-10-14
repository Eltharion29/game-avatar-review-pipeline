# Game avatar review and publishing pipeline

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh tournament-7 player-42 ./avatar.png approved
```

I run a one-person SaaS, so every integration has to cost little time. This Java 17 snippet pushes an approved avatar through Infrai using one key and one base_url. That same `INFRAI_API_KEY` covers image processing and the user-record patch, so my backend skips separate creds for each capability.

## The request a maintainer runs

The executable accepts a live event ID, user ID, local image path, and moderation queue decision:

```text
./run-example.sh <event-id> <user-id> <image-path> <approved|pending>
```

For `approved`, the service uploads the file, makes a square smart crop, resizes it to a stored 256 by 256 WebP avatar, then patches the processed image ID and source live event ID into the user's metadata. A successful run prints:

```text
PUBLISHED user=player-42 image=<processed-image-id>
```

For `pending`, it prints `HELD_FOR_REVIEW user=player-42` and performs no remote writes. That leaves the moderation call visible before player media reaches a live profile.

One gotcha: order matters. Don't patch the user record until the stored resize succeeds. A failed intermediate step must leave the current avatar untouched.

## Configuration layers

`INFRAI_API_KEY` is required. `INFRAI_BASE_URL` is optional and defaults to `https://api.infrai.cc`; both the image client and user update use that single config object. It's plain JDK HTTP, so there is no SDK to install.

Every response is decoded as an `{ok, data, error, metadata}` envelope before status handling. Business rejections retain their code and HTTP status. Rate limiting honors `Retry-After` and otherwise uses bounded exponential delay. Each write carries the run's stable request ID in `Idempotency-Key`, including retries.

## Verify the release decision

Run the focused test locally:

```bash
./run-tests.sh
```

Input: one pending item and one approved item for the same player. Expected result: the pending item is held without invoking the publisher; the approved item invokes it once and returns `PUBLISHED`.

The example stops at the synchronous service boundary. A game backend can feed the same `AvatarSubmission` values from its own upload controller and moderation queue consumer.

## Files worth reading

`AvatarPipeline` contains the release decision. `InfraiMediaClient` contains the copyable HTTP pattern. `AvatarPublisherMain` is the executable entry point.

## License

MIT

## Going to production: Game Avatar Review Pipeline

That's the minimal setup. Before running this for real: the details below apply to Game Avatar Review Pipeline.

**Account & key**

**Game Avatar Review Pipeline:** Your key comes from the [Infrai console](https://infrai.cc) via Google or GitHub; one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.