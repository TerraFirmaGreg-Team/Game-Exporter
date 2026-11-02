package team.terrafirmagreg.exporter.contract.dto.atlas;

import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;

public record AtlasSpriteRow(
        int page, int x, int y, int w, int h, String langKey, String tint, String tintColor, ReferenceKind kind) {
    public AtlasSpriteRow {
        kind = kind == null ? ReferenceKind.ITEM : kind;
    }
}
