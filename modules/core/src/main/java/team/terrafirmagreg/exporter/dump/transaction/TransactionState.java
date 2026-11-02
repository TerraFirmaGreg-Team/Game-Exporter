package team.terrafirmagreg.exporter.dump.transaction;

import java.util.Objects;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;

public final class TransactionState {
    private volatile DumpTransaction.State state = DumpTransaction.State.OPEN;

    public DumpTransaction.State get() {
        return state;
    }

    public void require(DumpTransaction.State expected) {
        if (state != expected) {
            throw new IllegalStateException("Expected transaction state " + expected + " but was " + state);
        }
    }

    public synchronized void transition(DumpTransaction.State expected, DumpTransaction.State next) {
        require(expected);
        state = next;
    }

    public void ensureWritable(ArtifactAddress address) {
        Objects.requireNonNull(address, "address");
        if (address instanceof ArtifactAddress.Manifest) {
            if (state != DumpTransaction.State.FINALIZED) {
                throw new IllegalStateException("Root manifest can only be written during finalization");
            }
            return;
        }
        if (state != DumpTransaction.State.OPEN
                && state != DumpTransaction.State.CONTENT_COMMITTED
                && state != DumpTransaction.State.SUPPORTING_COMMITTED) {
            throw new IllegalStateException("Transaction is not writable in state " + state);
        }
        if (state == DumpTransaction.State.CONTENT_COMMITTED
                && !(address instanceof ArtifactAddress.Meta || address instanceof ArtifactAddress.Root)) {
            throw new IllegalStateException("Only supporting metadata may be written after content commit");
        }
    }
}
