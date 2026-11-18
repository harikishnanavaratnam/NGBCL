package lk.ngbcl.files.domain;

/** Technical state of the stored object. */
public enum UploadStatus {
    /** Upload URL issued, nothing confirmed yet. */
    PENDING,
    /** Object received and passed type / size / dimension checks. */
    UPLOADED,
    /** Object failed checks and was removed. */
    INVALID,
    /** Abandoned or replaced; object removed. */
    DELETED
}
