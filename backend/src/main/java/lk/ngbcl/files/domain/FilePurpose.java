package lk.ngbcl.files.domain;

/**
 * What an upload is for. Each purpose has its own rules (aspect ratio, minimum size) and its
 * own folder in the bucket. The public copy is always re-encoded (JPEG for photos, PNG for
 * logos so transparency survives), which also strips EXIF/GPS metadata.
 */
public enum FilePurpose {
    PLAYER_PHOTO("players", 4, 5, 600, 750, PublicFormat.JPEG, 1600),
    TEAM_LOGO("teams", 1, 1, 256, 256, PublicFormat.PNG, 1024),
    TOURNAMENT_LOGO("branding", 0, 0, 256, 128, PublicFormat.PNG, 2000),
    FAVICON("branding", 1, 1, 180, 180, PublicFormat.PNG, 512),
    ORGANISER_LOGO("branding", 0, 0, 128, 128, PublicFormat.PNG, 1024),
    POSTER("branding", 16, 9, 1280, 720, PublicFormat.JPEG, 1920),
    SPONSOR_LOGO("sponsors", 0, 0, 200, 100, PublicFormat.PNG, 1200),
    COMMITTEE_PHOTO("committee", 4, 5, 300, 375, PublicFormat.JPEG, 1000),
    NEWS_COVER("news", 16, 9, 960, 540, PublicFormat.JPEG, 1600);

    public enum PublicFormat {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png");

        public final String contentType;
        public final String extension;

        PublicFormat(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    public final String folder;
    /** Required aspect ratio as w:h, or 0:0 for any. Checked with a 2 % tolerance. */
    public final int ratioW;
    public final int ratioH;
    public final int minWidth;
    public final int minHeight;
    public final PublicFormat publicFormat;
    /** Longest side of the public copy; larger uploads are scaled down. */
    public final int maxPublicSide;

    FilePurpose(String folder, int ratioW, int ratioH, int minWidth, int minHeight, PublicFormat publicFormat,
            int maxPublicSide) {
        this.folder = folder;
        this.ratioW = ratioW;
        this.ratioH = ratioH;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.publicFormat = publicFormat;
        this.maxPublicSide = maxPublicSide;
    }

    public boolean hasFixedRatio() {
        return ratioW > 0 && ratioH > 0;
    }
}
