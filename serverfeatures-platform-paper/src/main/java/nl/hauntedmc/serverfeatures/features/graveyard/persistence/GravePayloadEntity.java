package nl.hauntedmc.serverfeatures.features.graveyard.persistence;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "player_graveyard_payloads")
public class GravePayloadEntity {
    @Id
    @Column(name = "grave_id", length = 36, nullable = false)
    private String graveId;

    @Column(name = "payload_revision", nullable = false)
    private long payloadRevision;

    @Column(name = "payload_codec", nullable = false)
    private int payloadCodec;

    @Column(name = "compressed", nullable = false)
    private boolean compressed;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "payload", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] payload;

    @Column(name = "payload_checksum", length = 64, nullable = false)
    private String payloadChecksum;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public String getGraveId() { return graveId; }
    public void setGraveId(String graveId) { this.graveId = graveId; }
    public long getPayloadRevision() { return payloadRevision; }
    public void setPayloadRevision(long payloadRevision) { this.payloadRevision = payloadRevision; }
    public int getPayloadCodec() { return payloadCodec; }
    public void setPayloadCodec(int payloadCodec) { this.payloadCodec = payloadCodec; }
    public boolean isCompressed() { return compressed; }
    public void setCompressed(boolean compressed) { this.compressed = compressed; }
    public byte[] getPayload() { return payload; }
    public void setPayload(byte[] payload) { this.payload = payload; }
    public String getPayloadChecksum() { return payloadChecksum; }
    public void setPayloadChecksum(String payloadChecksum) { this.payloadChecksum = payloadChecksum; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public long getRowVersion() { return rowVersion; }
}
