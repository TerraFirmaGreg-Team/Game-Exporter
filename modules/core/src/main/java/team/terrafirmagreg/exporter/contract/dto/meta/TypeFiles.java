package team.terrafirmagreg.exporter.contract.dto.meta;

import org.jspecify.annotations.Nullable;

public record TypeFiles(
        long count, long bytes, String size, @Nullable LargestFile largest) {}
