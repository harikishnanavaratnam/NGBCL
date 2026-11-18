package lk.ngbcl.registration.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.files.domain.VerificationStatus;
import lk.ngbcl.files.service.FileAssetView;
import lk.ngbcl.player.service.PlayerService.PlayerSummary;
import lk.ngbcl.registration.domain.DuplicateResolution;
import lk.ngbcl.registration.domain.RegistrationStatus;

/** Staff-only views of registrations. Contain personal data — never returned by public endpoints. */
public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    public record Blocker(String code, String message) {
    }

    public record PlayerRegistrationRow(UUID id, String registrationNumber, String fullName, String email,
            String phone, String homeArea, UUID categoryId, String categoryName, RegistrationStatus status,
            boolean duplicateFlag, VerificationStatus photoStatus, String photoPreviewUrl, Instant submittedAt,
            Long version) {
    }

    public record CandidateRegistration(UUID id, String registrationNumber, String fullName, String email,
            String phone, LocalDate dateOfBirth, RegistrationStatus status, Instant submittedAt) {
    }

    public record DuplicateView(UUID id, String matchedOn, DuplicateResolution resolution, Instant resolvedAt,
            CandidateRegistration registration, PlayerSummary player) {
    }

    public record NoteView(UUID id, String authorName, String body, Instant createdAt) {
    }

    public record PlayerRegistrationDetail(UUID id, String registrationNumber, RegistrationStatus status,
            String fullName, LocalDate dateOfBirth, String gender, String email, String phone, String address,
            String homeArea, String experience, String emergencyName, String emergencyPhone, UUID categoryId,
            String categoryName, FileAssetView photo, boolean duplicateFlag, List<DuplicateView> duplicates,
            PlayerSummary linkedPlayer, UUID playerId, Instant submittedAt, Instant reviewedAt,
            String rejectionReason, String rejectionMessage, List<NoteView> notes, List<Blocker> blockers,
            Long version) {
    }

    public record TeamRegistrationRow(UUID id, String registrationNumber, String teamName, String contactName,
            String contactEmail, String contactPhone, RegistrationStatus status, VerificationStatus logoStatus,
            String logoPreviewUrl, boolean nameTaken, Instant submittedAt, Long version) {
    }

    public record TeamRegistrationDetail(UUID id, String registrationNumber, RegistrationStatus status,
            String teamName, String description, String contactName, String contactEmail, String contactPhone,
            FileAssetView logo, boolean nameTaken, String suggestedShortCode, UUID teamId, Instant submittedAt,
            Instant reviewedAt, String rejectionReason, String rejectionMessage, List<NoteView> notes,
            List<Blocker> blockers, Long version) {
    }

    public record Summary(long pendingPlayers, long pendingTeams, long flaggedPlayers, long approvedPlayers,
            long approvedTeams, long rejectedPlayers, long rejectedTeams) {
    }

    public record BulkResult(List<UUID> approved, List<BulkSkip> skipped) {
    }

    public record BulkSkip(UUID id, String registrationNumber, String code, String message) {
    }
}
