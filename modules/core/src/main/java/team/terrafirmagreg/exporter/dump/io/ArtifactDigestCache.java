package team.terrafirmagreg.exporter.dump.io;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactDigestCache {
    private static final ConcurrentHashMap<Path, ArtifactDigest> DIGESTS = new ConcurrentHashMap<>();

    public static void remember(Path path, ArtifactDigest digest) {
        DIGESTS.put(path.toAbsolutePath().normalize(), digest);
    }

    public static ArtifactDigest lookup(Path path) {
        return DIGESTS.get(path.toAbsolutePath().normalize());
    }

    public static void forgetUnder(Path root) {
        Path prefix = root.toAbsolutePath().normalize();
        DIGESTS.keySet().removeIf(path -> path.startsWith(prefix));
    }
}
