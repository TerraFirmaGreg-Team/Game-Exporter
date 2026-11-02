package team.terrafirmagreg.exporter.contract.text;

public record DisplayFill(String modId, String key, DumpText name) {
    public DisplayFill {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("modId");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key");
        }
        if (name == null) {
            throw new IllegalArgumentException("name");
        }
    }
}
