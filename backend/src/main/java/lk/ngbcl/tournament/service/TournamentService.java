package lk.ngbcl.tournament.service;

import java.util.UUID;
import lk.ngbcl.files.domain.FilePurpose;
import lk.ngbcl.files.service.FileService;
import lk.ngbcl.files.service.UploadContext;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.tournament.domain.Tournament;
import lk.ngbcl.tournament.domain.TournamentRepository;
import lk.ngbcl.tournament.domain.TournamentSettings;
import lk.ngbcl.tournament.domain.TournamentSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The current edition and its settings — what every other module scopes its data to. */
@Service
public class TournamentService implements UploadContext {

    private final TournamentRepository tournaments;
    private final TournamentSettingsRepository settings;

    public TournamentService(TournamentRepository tournaments, TournamentSettingsRepository settings) {
        this.tournaments = tournaments;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Tournament current() {
        return tournaments.findByCurrentEditionTrue().orElseThrow(() -> ApiException.notFound("Tournament"));
    }

    /** Settings row for a tournament, created with defaults if missing. */
    @Transactional
    public TournamentSettings settingsFor(UUID tournamentId) {
        return settings.findById(tournamentId).orElseGet(() -> settings.save(new TournamentSettings(tournamentId)));
    }

    @Override
    @Transactional(readOnly = true)
    public UUID currentTournamentId() {
        return current().getId();
    }

    @Override
    @Transactional
    public FileService.UploadLimits limitsFor(FilePurpose purpose) {
        if (purpose == FilePurpose.PLAYER_PHOTO) {
            TournamentSettings s = settingsFor(currentTournamentId());
            return new FileService.UploadLimits(s.getMaxPhotoBytes(), s.getMinPhotoWidth(), s.getMinPhotoHeight());
        }
        return FileService.UploadLimits.defaultsFor(purpose);
    }
}
