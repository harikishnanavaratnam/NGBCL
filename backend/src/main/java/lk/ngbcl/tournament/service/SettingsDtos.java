package lk.ngbcl.tournament.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lk.ngbcl.files.service.FileAssetView;
import lk.ngbcl.tournament.domain.PlayerFieldRules;
import lk.ngbcl.tournament.domain.TournamentPhase;
import lk.ngbcl.tournament.domain.TournamentStatus;
import lk.ngbcl.tournament.domain.WindowState;

/** Request/response shapes for the admin settings screens. */
public final class SettingsDtos {

    private SettingsDtos() {
    }

    // ---- Responses ---------------------------------------------------------

    public record SettingsResponse(General general, PublicInfo publicInfo, Registration registration, Branding branding,
            long settingsVersion, Instant updatedAt) {
    }

    public record General(UUID id, String slug, String name, String shortName, String edition, String tagline,
            String description, String organiser, String presenterLine, LocalDate startsOn, LocalDate endsOn, String venue,
            String city, Short courts, TournamentStatus status, String timezone, String currency, TournamentPhase phase,
            long tournamentVersion) {
    }

    public record PublicInfo(String facebookUrl, String contactEmail, String contactPhone, Long prizeFirst,
            Long prizeSecond, Long prizeThird, Long teamEntryFee, boolean showPlayerPrices, boolean showTeamBudgets,
            TournamentPhase phaseOverride) {
    }

    public record Registration(Instant playerOpensAt, Instant playerClosesAt, WindowState playerState,
            Instant teamOpensAt, Instant teamClosesAt, WindowState teamState, boolean categoryRequired,
            PlayerFieldRules playerFields, boolean dupMatchEmail, boolean dupMatchPhone, boolean dupMatchNameDob,
            int maxPhotoBytes, int minPhotoWidth, int minPhotoHeight, boolean notifyOnRegistration) {
    }

    public record Branding(FileAssetView logo, FileAssetView favicon, FileAssetView organiserLogo, FileAssetView poster) {
    }

    // ---- Requests ----------------------------------------------------------

    public record UpdateGeneralRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 40) String shortName,
            @Size(max = 160) String tagline,
            @Size(max = 4000) String description,
            @Size(max = 160) String organiser,
            @Size(max = 160) String presenterLine,
            LocalDate startsOn,
            LocalDate endsOn,
            @Size(max = 200) String venue,
            @Size(max = 80) String city,
            @Min(1) @Max(20) Short courts,
            @Valid @NotNull PublicInfoRequest publicInfo,
            @NotNull Long tournamentVersion,
            @NotNull Long settingsVersion) {
    }

    public record PublicInfoRequest(
            @Size(max = 300) @Pattern(regexp = "^$|^https://.*", message = "Use a full https:// link") String facebookUrl,
            @Email @Size(max = 254) String contactEmail,
            @Size(max = 30) @Pattern(regexp = "^$|^[+0-9 ()-]{7,30}$", message = "Enter a valid phone number") String contactPhone,
            @PositiveOrZero Long prizeFirst,
            @PositiveOrZero Long prizeSecond,
            @PositiveOrZero Long prizeThird,
            @PositiveOrZero Long teamEntryFee,
            boolean showPlayerPrices,
            boolean showTeamBudgets,
            TournamentPhase phaseOverride) {
    }

    public record UpdateRegistrationRequest(
            Instant playerOpensAt,
            Instant playerClosesAt,
            Instant teamOpensAt,
            Instant teamClosesAt,
            boolean categoryRequired,
            @NotNull PlayerFieldRules playerFields,
            boolean dupMatchEmail,
            boolean dupMatchPhone,
            boolean dupMatchNameDob,
            @Min(262_144) @Max(20_971_520) int maxPhotoBytes,
            @Min(100) @Max(4000) int minPhotoWidth,
            @Min(100) @Max(5000) int minPhotoHeight,
            boolean notifyOnRegistration,
            @NotNull Long settingsVersion) {
    }

    public record UpdateBrandingRequest(UUID logoAssetId, UUID faviconAssetId, UUID organiserLogoAssetId,
            UUID posterAssetId, @NotNull Long settingsVersion) {
    }

    public record ChangeStatusRequest(@NotNull TournamentStatus status, @Size(max = 500) String reason) {
    }
}
