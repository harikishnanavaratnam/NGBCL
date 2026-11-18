package lk.ngbcl.tournament.web;

import jakarta.validation.Valid;
import lk.ngbcl.tournament.service.SettingsDtos;
import lk.ngbcl.tournament.service.SettingsDtos.SettingsResponse;
import lk.ngbcl.tournament.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin › System › Settings. Permissions are enforced in {@link SettingsService}. */
@RestController
@RequestMapping("/api/admin")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/settings")
    public SettingsResponse get() {
        return settings.get();
    }

    @PutMapping("/settings/general")
    public SettingsResponse general(@Valid @RequestBody SettingsDtos.UpdateGeneralRequest body) {
        return settings.updateGeneral(body);
    }

    @PutMapping("/settings/registration")
    public SettingsResponse registration(@Valid @RequestBody SettingsDtos.UpdateRegistrationRequest body) {
        return settings.updateRegistration(body);
    }

    @PutMapping("/settings/branding")
    public SettingsResponse branding(@Valid @RequestBody SettingsDtos.UpdateBrandingRequest body) {
        return settings.updateBranding(body);
    }

    @PutMapping("/tournament/status")
    public SettingsResponse status(@Valid @RequestBody SettingsDtos.ChangeStatusRequest body) {
        return settings.changeStatus(body);
    }
}
