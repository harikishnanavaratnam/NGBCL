package lk.ngbcl.competition.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.ngbcl.audit.service.AuditEvent;
import lk.ngbcl.audit.service.AuditService;
import lk.ngbcl.competition.domain.CompetitionFormat;
import lk.ngbcl.competition.domain.CompetitionSettings;
import lk.ngbcl.competition.domain.CompetitionSettingsRepository;
import lk.ngbcl.competition.domain.Court;
import lk.ngbcl.competition.domain.CourtRepository;
import lk.ngbcl.competition.domain.FixtureTemplateItem;
import lk.ngbcl.competition.domain.FixtureTemplateItemRepository;
import lk.ngbcl.competition.domain.GenderRule;
import lk.ngbcl.competition.domain.MatchCategory;
import lk.ngbcl.competition.domain.MatchCategoryRepository;
import lk.ngbcl.competition.domain.TeamFixture;
import lk.ngbcl.competition.domain.TeamFixtureRepository;
import lk.ngbcl.competition.service.CompetitionDtos.CategoryView;
import lk.ngbcl.competition.service.CompetitionDtos.CourtView;
import lk.ngbcl.competition.service.CompetitionDtos.SettingsView;
import lk.ngbcl.competition.service.CompetitionDtos.SetupView;
import lk.ngbcl.competition.service.CompetitionDtos.TemplateItemView;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.FieldErrors;
import lk.ngbcl.tournament.service.TournamentService;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Match categories, the fixture template, courts and the format rules (FIX-001, FIX-007).
 * A fresh edition is seeded with a neutral template (MS, WS, MD, WD, XD) and three courts;
 * organisers change any of it before generating fixtures.
 */
@Service
public class CompetitionSetupService {

    private final CompetitionSettingsRepository settings;
    private final MatchCategoryRepository categories;
    private final FixtureTemplateItemRepository template;
    private final CourtRepository courts;
    private final TeamFixtureRepository fixtures;
    private final TournamentService tournament;
    private final AuditService audit;
    private final EntityManager em;

    public CompetitionSetupService(CompetitionSettingsRepository settings, MatchCategoryRepository categories,
            FixtureTemplateItemRepository template, CourtRepository courts, TeamFixtureRepository fixtures,
            TournamentService tournament, AuditService audit, EntityManager em) {
        this.settings = settings;
        this.categories = categories;
        this.template = template;
        this.courts = courts;
        this.fixtures = fixtures;
        this.tournament = tournament;
        this.audit = audit;
        this.em = em;
    }

    public record SettingsInput(CompetitionFormat format, int groupCount, int qualifiersPerGroup, boolean thirdPlaceMatch,
            int maxMatchesPerPlayer, int matchMinutes, int restMinutes, Long version) {
    }

    public record CategoryInput(String code, String name, int playersPerSide, GenderRule genderRule, Boolean active,
            Long version) {
    }

    public record CourtInput(String name, Boolean active, Long version) {
    }

    // ---- Seed --------------------------------------------------------------------------

    /** The edition's settings; the first call also seeds default categories, template and courts. */
    @Transactional
    public CompetitionSettings ensure(UUID t) {
        if (settings.insertIfMissing(t) == 1) {
            seed(t);
        }
        return settings.findById(t).orElseThrow();
    }

    private void seed(UUID t) {
        Object[][] defaults = {
            {"MS", "Men's singles", 1, GenderRule.MALE},
            {"WS", "Women's singles", 1, GenderRule.FEMALE},
            {"MD", "Men's doubles", 2, GenderRule.MALE},
            {"WD", "Women's doubles", 2, GenderRule.FEMALE},
            {"XD", "Mixed doubles", 2, GenderRule.MIXED},
        };
        int order = 0;
        for (Object[] d : defaults) {
            MatchCategory c = categories.save(new MatchCategory(t, (String) d[0], (String) d[1], (Integer) d[2],
                    (GenderRule) d[3], ++order));
            template.save(new FixtureTemplateItem(t, order, c.getId()));
        }
        for (int i = 1; i <= 3; i++) {
            courts.save(new Court(t, "Court " + i, i));
        }
        em.flush();
    }

    // ---- Read ----------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyAuthority('FIXTURE_MANAGE', 'SETTINGS_MANAGE')")
    public SetupView setup() {
        UUID t = tournament.currentTournamentId();
        CompetitionSettings s = ensure(t);
        List<TeamFixture> all = fixtures.findByTournamentIdOrderByFixtureNumberAsc(t);
        return new SetupView(view(s), categoryViews(t), templateViews(t), courtViews(t), !all.isEmpty(),
                all.stream().anyMatch(TeamFixture::isPublished));
    }

    static SettingsView view(CompetitionSettings s) {
        return new SettingsView(s.getFormat(), s.getGroupCount(), s.getQualifiersPerGroup(), s.isThirdPlaceMatch(),
                s.getMaxMatchesPerPlayer(), s.getMatchMinutes(), s.getRestMinutes(), s.getVersion());
    }

    List<CategoryView> categoryViews(UUID t) {
        Set<UUID> used = new HashSet<>(em.createQuery(
                "select distinct m.categoryId from Match m, TeamFixture f where f.id = m.fixtureId and f.tournamentId = :t",
                UUID.class).setParameter("t", t).getResultList());
        return categories.findByTournamentIdOrderByDisplayOrderAscCodeAsc(t).stream()
                .map(c -> new CategoryView(c.getId(), c.getCode(), c.getName(), c.getPlayersPerSide(), c.getGenderRule(),
                        c.isActive(), used.contains(c.getId()), c.getVersion()))
                .toList();
    }

    List<TemplateItemView> templateViews(UUID t) {
        Map<UUID, MatchCategory> byId = categories.findByTournamentIdOrderByDisplayOrderAscCodeAsc(t).stream()
                .collect(Collectors.toMap(MatchCategory::getId, Function.identity()));
        return template.findByTournamentIdOrderByPositionAsc(t).stream().map(i -> {
            MatchCategory c = byId.get(i.getCategoryId());
            return new TemplateItemView(i.getPosition(), i.getCategoryId(), c.getCode(), c.getName());
        }).toList();
    }

    List<CourtView> courtViews(UUID t) {
        return courts.findByTournamentIdOrderByDisplayOrderAscNameAsc(t).stream()
                .map(c -> new CourtView(c.getId(), c.getName(), c.isActive(), c.getVersion())).toList();
    }

    // ---- Settings ------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('FIXTURE_MANAGE')")
    public SetupView updateSettings(SettingsInput in) {
        UUID t = tournament.currentTournamentId();
        CompetitionSettings s = ensure(t);
        if (in.version() != null && !Objects.equals(in.version(), s.getVersion())) {
            throw ApiException.conflict("STALE_DATA", "Someone else saved the competition settings. Reload to see their changes.");
        }
        FieldErrors e = new FieldErrors();
        if (in.format() == null) {
            e.add("format", "Choose a format.");
        }
        if (in.groupCount() < 1 || in.groupCount() > 8) {
            e.add("groupCount", "Between 1 and 8 groups.");
        }
        if (in.qualifiersPerGroup() < 1 || in.qualifiersPerGroup() > 8) {
            e.add("qualifiersPerGroup", "Between 1 and 8 teams.");
        }
        if (in.format() == CompetitionFormat.GROUPS_KNOCKOUT
                && Integer.bitCount(in.groupCount() * in.qualifiersPerGroup()) != 1) {
            e.add("qualifiersPerGroup", "Groups × qualifiers must be 2, 4, 8 or 16 so the knockout bracket is full.");
        }
        if (in.format() == CompetitionFormat.GROUPS_KNOCKOUT && in.groupCount() * in.qualifiersPerGroup() < 2) {
            e.add("qualifiersPerGroup", "At least two teams must reach the knockout.");
        }
        if (in.maxMatchesPerPlayer() < 1 || in.maxMatchesPerPlayer() > 10) {
            e.add("maxMatchesPerPlayer", "Between 1 and 10.");
        }
        if (in.matchMinutes() < 5 || in.matchMinutes() > 240) {
            e.add("matchMinutes", "Between 5 and 240 minutes.");
        }
        if (in.restMinutes() < 0 || in.restMinutes() > 240) {
            e.add("restMinutes", "Between 0 and 240 minutes.");
        }
        e.throwIfAny();
        Map<String, Object> before = snapshot(s);
        s.update(in.format(), in.groupCount(), in.qualifiersPerGroup(), in.thirdPlaceMatch(), in.maxMatchesPerPlayer(),
                in.matchMinutes(), in.restMinutes());
        settings.flush();
        audit.record(AuditEvent.of("competition.settings.updated", "CompetitionSettings", t).inTournament(t)
                .withChange(before, snapshot(s)));
        return setup();
    }

    private static Map<String, Object> snapshot(CompetitionSettings s) {
        Map<String, Object> m = new HashMap<>();
        m.put("format", s.getFormat());
        m.put("groupCount", s.getGroupCount());
        m.put("qualifiersPerGroup", s.getQualifiersPerGroup());
        m.put("thirdPlaceMatch", s.isThirdPlaceMatch());
        m.put("maxMatchesPerPlayer", s.getMaxMatchesPerPlayer());
        m.put("matchMinutes", s.getMatchMinutes());
        m.put("restMinutes", s.getRestMinutes());
        return m;
    }

    // ---- Categories ----------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyAuthority('FIXTURE_MANAGE', 'SETTINGS_MANAGE')")
    public SetupView saveCategory(UUID id, CategoryInput in) {
        UUID t = tournament.currentTournamentId();
        ensure(t);
        String code = in.code() == null ? "" : in.code().trim().toUpperCase(Locale.ROOT);
        String name = in.name() == null ? "" : in.name().trim();
        GenderRule rule = in.genderRule() == null ? GenderRule.ANY : in.genderRule();
        FieldErrors e = new FieldErrors();
        if (!code.matches("[A-Z0-9]{1,8}")) {
            e.add("code", "Use 1–8 letters or digits, e.g. MS or XD.");
        }
        if (name.isEmpty() || name.length() > 60) {
            e.add("name", "Enter a name up to 60 characters.");
        }
        if (in.playersPerSide() != 1 && in.playersPerSide() != 2) {
            e.add("playersPerSide", "Singles (1) or doubles (2).");
        }
        if (rule == GenderRule.MIXED && in.playersPerSide() != 2) {
            e.add("genderRule", "Mixed needs two players per side.");
        }
        List<MatchCategory> all = categories.findByTournamentIdOrderByDisplayOrderAscCodeAsc(t);
        if (all.stream().anyMatch(c -> c.getCode().equals(code) && !c.getId().equals(id))) {
            e.add("code", "Another category already uses this code.");
        }
        MatchCategory c = null;
        if (id != null) {
            c = all.stream().filter(x -> x.getId().equals(id)).findFirst()
                    .orElseThrow(() -> ApiException.notFound("Match category"));
            if (in.version() != null && !Objects.equals(in.version(), c.getVersion())) {
                throw ApiException.conflict("STALE_DATA", "This category was changed by someone else. Reload and try again.");
            }
            boolean inUse = categoryViews(t).stream().anyMatch(v -> v.id().equals(id) && v.inUse());
            if (inUse && in.playersPerSide() != c.getPlayersPerSide()) {
                e.add("playersPerSide", "Matches already use this category. Regenerate fixtures to change singles/doubles.");
            }
            if (Boolean.FALSE.equals(in.active()) && template.findByTournamentIdOrderByPositionAsc(t).stream()
                    .anyMatch(i -> i.getCategoryId().equals(id))) {
                e.add("active", "Remove it from the fixture template first.");
            }
        }
        e.throwIfAny();
        if (c == null) {
            int order = all.stream().mapToInt(MatchCategory::getDisplayOrder).max().orElse(0) + 1;
            c = categories.save(new MatchCategory(t, code, name, in.playersPerSide(), rule, order));
            audit.record(AuditEvent.of("competition.category.created", "MatchCategory", c.getId()).inTournament(t)
                    .withChange(null, Map.of("code", code, "name", name)));
        } else {
            Map<String, Object> before = Map.of("code", c.getCode(), "name", c.getName(), "playersPerSide",
                    c.getPlayersPerSide(), "genderRule", c.getGenderRule(), "active", c.isActive());
            c.update(code, name, in.playersPerSide(), rule);
            if (in.active() != null) {
                c.setActive(in.active());
            }
            audit.record(AuditEvent.of("competition.category.updated", "MatchCategory", c.getId()).inTournament(t)
                    .withChange(before, Map.of("code", code, "name", name, "playersPerSide", in.playersPerSide(),
                            "genderRule", rule, "active", c.isActive())));
        }
        categories.flush();
        return setup();
    }

    /** The ordered list of matches in every tie. Applies to fixtures generated after the change. */
    @Transactional
    @PreAuthorize("hasAnyAuthority('FIXTURE_MANAGE', 'SETTINGS_MANAGE')")
    public SetupView saveTemplate(List<UUID> categoryIds) {
        UUID t = tournament.currentTournamentId();
        ensure(t);
        if (categoryIds == null || categoryIds.isEmpty() || categoryIds.size() > 12) {
            throw ApiException.unprocessable("INVALID_TEMPLATE", "A tie needs between 1 and 12 matches.");
        }
        Map<UUID, MatchCategory> active = categories.findByTournamentIdOrderByDisplayOrderAscCodeAsc(t).stream()
                .filter(MatchCategory::isActive).collect(Collectors.toMap(MatchCategory::getId, Function.identity()));
        for (UUID id : categoryIds) {
            if (!active.containsKey(id)) {
                throw ApiException.unprocessable("INVALID_TEMPLATE", "Use active match categories only.");
            }
        }
        List<String> before = templateViews(t).stream().map(TemplateItemView::code).toList();
        template.deleteByTournament(t);
        template.flush();
        int pos = 0;
        for (UUID id : categoryIds) {
            template.save(new FixtureTemplateItem(t, ++pos, id));
        }
        template.flush();
        List<String> after = new ArrayList<>();
        categoryIds.forEach(id -> after.add(active.get(id).getCode()));
        audit.record(AuditEvent.of("competition.template.updated", "FixtureTemplate", t).inTournament(t)
                .withChange(Map.of("matches", before), Map.of("matches", after)));
        return setup();
    }

    // ---- Courts --------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyAuthority('FIXTURE_MANAGE', 'SETTINGS_MANAGE')")
    public SetupView saveCourt(UUID id, CourtInput in) {
        UUID t = tournament.currentTournamentId();
        ensure(t);
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isEmpty() || name.length() > 40) {
            new FieldErrors().add("name", "Enter a court name up to 40 characters.").throwIfAny();
        }
        List<Court> all = courts.findByTournamentIdOrderByDisplayOrderAscNameAsc(t);
        if (all.stream().anyMatch(c -> c.getName().equalsIgnoreCase(name) && !c.getId().equals(id))) {
            new FieldErrors().add("name", "Another court already has this name.").throwIfAny();
        }
        if (id == null) {
            int order = all.stream().mapToInt(Court::getDisplayOrder).max().orElse(0) + 1;
            Court c = courts.save(new Court(t, name, order));
            audit.record(AuditEvent.of("competition.court.created", "Court", c.getId()).inTournament(t)
                    .withChange(null, Map.of("name", name)));
        } else {
            Court c = all.stream().filter(x -> x.getId().equals(id)).findFirst()
                    .orElseThrow(() -> ApiException.notFound("Court"));
            if (in.version() != null && !Objects.equals(in.version(), c.getVersion())) {
                throw ApiException.conflict("STALE_DATA", "This court was changed by someone else. Reload and try again.");
            }
            Map<String, Object> before = Map.of("name", c.getName(), "active", c.isActive());
            c.rename(name);
            if (in.active() != null) {
                c.setActive(in.active());
            }
            audit.record(AuditEvent.of("competition.court.updated", "Court", c.getId()).inTournament(t)
                    .withChange(before, Map.of("name", name, "active", c.isActive())));
        }
        courts.flush();
        return setup();
    }
}
