package team.terrafirmagreg.exporter.spi;

import java.util.*;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class SourceSet<S extends CaptureSource> {

    private final String vertical;
    private final MergePolicy policy;
    private final List<S> sources = new ArrayList<>();
    private final Set<String> ids = new HashSet<>();
    private final List<String> diagnostics = new ArrayList<>();

    public SourceSet(String vertical, MergePolicy policy) {
        this.vertical = Objects.requireNonNull(vertical, "vertical");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public SourceSet<S> add(S source) {
        Objects.requireNonNull(source, "source");
        SourceDescriptor descriptor = source.descriptor();
        if (descriptor.mergePolicy() != policy) {
            throw new IllegalArgumentException("Source " + descriptor.id() + " declares " + descriptor.mergePolicy()
                    + " but " + vertical + " requires " + policy);
        }
        if (!ids.add(descriptor.id())) {
            String diagnostic = "Duplicate " + vertical + " source id: " + descriptor.id();
            diagnostics.add(diagnostic);
            ExportLog.warn("{}", diagnostic);
        }
        sources.add(source);
        return this;
    }

    public boolean isEmpty() {
        return sources.isEmpty();
    }

    public List<S> available() {
        List<S> available = sources.stream().filter(CaptureSource::isAvailable).toList();
        if (policy == MergePolicy.EXCLUSIVE && available.size() > 1) {
            throw new IllegalStateException("Multiple exclusive " + vertical + " sources are available: "
                    + available.stream().map(CaptureSource::id).toList());
        }
        if (policy == MergePolicy.FIRST_AVAILABLE && !available.isEmpty()) {
            return List.of(available.getFirst());
        }
        return available;
    }

    public S first() {
        List<S> available = available();
        return available.isEmpty() ? null : available.getFirst();
    }

    public MergePolicy policy() {
        return policy;
    }

    public List<String> diagnostics() {
        return List.copyOf(diagnostics);
    }
}
