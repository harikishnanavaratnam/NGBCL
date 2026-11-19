package lk.ngbcl.auction.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.ngbcl.auction.domain.Auction;
import lk.ngbcl.auction.domain.AuctionBid;
import lk.ngbcl.auction.domain.AuctionBidRepository;
import lk.ngbcl.auction.domain.AuctionLot;
import lk.ngbcl.auction.domain.AuctionLotRepository;
import lk.ngbcl.auction.domain.AuctionRepository;
import lk.ngbcl.auction.domain.AuctionSettings;
import lk.ngbcl.auction.domain.AuctionStatus;
import lk.ngbcl.auction.domain.BidStatus;
import lk.ngbcl.auction.domain.LotState;
import lk.ngbcl.auction.domain.TeamBudget;
import lk.ngbcl.auction.domain.TeamBudgetRepository;
import lk.ngbcl.auction.domain.TeamPlayer;
import lk.ngbcl.auction.domain.TeamPlayerRepository;
import lk.ngbcl.auction.service.AuctionDtos.BidView;
import lk.ngbcl.auction.service.AuctionDtos.ConsoleSnapshot;
import lk.ngbcl.auction.service.AuctionDtos.Counts;
import lk.ngbcl.auction.service.AuctionDtos.LotPlayerView;
import lk.ngbcl.auction.service.AuctionDtos.LotView;
import lk.ngbcl.auction.service.AuctionDtos.PublicAuctionState;
import lk.ngbcl.auction.service.AuctionDtos.PublicBid;
import lk.ngbcl.auction.service.AuctionDtos.PublicBuy;
import lk.ngbcl.auction.service.AuctionDtos.PublicLot;
import lk.ngbcl.auction.service.AuctionDtos.PublicResults;
import lk.ngbcl.auction.service.AuctionDtos.PublicTeamTicker;
import lk.ngbcl.auction.service.AuctionDtos.QueueItem;
import lk.ngbcl.auction.service.AuctionDtos.RosterEntry;
import lk.ngbcl.auction.service.AuctionDtos.TeamRail;
import lk.ngbcl.auction.service.AuctionDtos.TeamRoster;
import lk.ngbcl.player.service.PlayerService;
import lk.ngbcl.player.service.PlayerService.LotPlayer;
import lk.ngbcl.player.service.PlayerTeamSource;
import lk.ngbcl.team.service.SquadSource;
import lk.ngbcl.team.service.TeamService;
import lk.ngbcl.team.service.TeamService.TeamCard;
import lk.ngbcl.tournament.domain.TournamentSettings;
import lk.ngbcl.tournament.service.TournamentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds every read view of the auction from the database (the single source of truth):
 * the staff console snapshot, the public display state, results, rosters, and the team/price
 * facts the player and team pages show.
 */
@Component
public class AuctionReadModel implements PlayerTeamSource, SquadSource {

    private static final int LAST_BIDS = 6;
    private static final int TOP_BUYS = 5;

    private final AuctionRepository auctions;
    private final AuctionLotRepository lots;
    private final AuctionBidRepository bids;
    private final TeamBudgetRepository budgets;
    private final TeamPlayerRepository teamPlayers;
    private final AuctionSettingsService settingsService;
    private final TournamentService tournament;
    private final PlayerService players;
    private final TeamService teams;

    public AuctionReadModel(AuctionRepository auctions, AuctionLotRepository lots, AuctionBidRepository bids,
            TeamBudgetRepository budgets, TeamPlayerRepository teamPlayers, AuctionSettingsService settingsService,
            TournamentService tournament, PlayerService players, TeamService teams) {
        this.auctions = auctions;
        this.lots = lots;
        this.bids = bids;
        this.budgets = budgets;
        this.teamPlayers = teamPlayers;
        this.settingsService = settingsService;
        this.tournament = tournament;
        this.players = players;
        this.teams = teams;
    }

    /** Everything one request needs, loaded once. */
    private final class Ctx {
        final Auction auction;
        final AuctionSettings settings;
        final TournamentSettings site;
        final List<AuctionLot> allLots;
        final Map<UUID, LotPlayer> people;
        final Map<UUID, TeamCard> cards;
        final Map<UUID, TeamBudget> budgetByTeam;
        final AuctionLot openLot;

        Ctx(Auction auction) {
            UUID t = auction.getTournamentId();
            this.auction = auction;
            this.settings = settingsService.peek(t);
            this.site = tournament.settingsFor(t);
            this.allLots = lots.findByAuctionIdOrderByRoundAscQueueOrderAsc(auction.getId());
            this.people = players.lotPlayers(allLots.stream().map(AuctionLot::getTournamentPlayerId).toList());
            this.cards = teams.cards(t);
            this.budgetByTeam = budgets.findByAuctionId(auction.getId()).stream()
                    .collect(Collectors.toMap(TeamBudget::getTeamId, Function.identity()));
            this.openLot = auction.getCurrentLotId() == null ? null
                    : allLots.stream().filter(l -> l.getId().equals(auction.getCurrentLotId())).findFirst().orElse(null);
        }

        String teamName(UUID id) {
            TeamCard c = id == null ? null : cards.get(id);
            return c == null ? null : c.name();
        }
    }

    // ---- Console -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('AUCTION_CONFIGURE', 'AUCTION_CONTROL', 'AUCTION_CORRECT')")
    public ConsoleSnapshot console() {
        UUID t = tournament.currentTournamentId();
        Auction a = auctions.findByTournamentId(t).orElseThrow(() -> new IllegalStateException("Auction not prepared"));
        Ctx c = new Ctx(a);

        List<QueueItem> queue = c.allLots.stream().map(l -> queueItem(c, l)).toList();
        List<TeamRail> rails;
        if (a.getStatus() == AuctionStatus.NOT_STARTED) {
            rails = teams.activeTeams(t).stream().map(card -> new TeamRail(card.id(), card.slug(), card.name(),
                    card.shortCode(), card.colorSlot(), card.logoUrl(), c.settings.getInitialBudget(), 0, 0,
                    c.settings.getInitialBudget(), 0, c.settings.getMaxSquadSize(), c.settings.getInitialBudget(), false,
                    "NOT_STARTED")).toList();
        } else {
            rails = c.budgetByTeam.values().stream().map(b -> rail(c, b))
                    .sorted(Comparator.comparing(TeamRail::name, String.CASE_INSENSITIVE_ORDER)).toList();
        }
        return new ConsoleSnapshot(a.getId(), a.getStatus(), a.getCurrentRound(), a.getStateVersion(),
                a.getLotsOpened(), a.getStartedAt(), a.getLastActionAt(), a.getLastEvent(),
                AuctionSettingsService.view(c.settings, a.getStatus() != AuctionStatus.NOT_STARTED),
                c.openLot == null ? null : lotView(c, c.openLot), queue, rails, counts(c.allLots),
                canStartNextRound(c), startBlockers(c, rails.size()));
    }

    private List<String> startBlockers(Ctx c, int activeTeams) {
        List<String> out = new ArrayList<>();
        if (c.auction.getStatus() != AuctionStatus.NOT_STARTED) {
            return out;
        }
        if (activeTeams < 2) {
            out.add("At least two active teams are needed.");
        }
        if (c.allLots.stream().noneMatch(l -> l.getState() == LotState.QUEUED)) {
            out.add("No approved players are waiting in the queue.");
        }
        return out;
    }

    /** Next round is possible once this round's queue is done and something went unsold (AUC-024). */
    private boolean canStartNextRound(Ctx c) {
        return c.auction.getStatus() == AuctionStatus.LIVE && c.settings.isAllowReauction() && c.openLot == null
                && c.allLots.stream().noneMatch(l -> l.getState() == LotState.QUEUED)
                && c.allLots.stream().anyMatch(l -> l.getState() == LotState.UNSOLD);
    }

    private TeamRail rail(Ctx c, TeamBudget b) {
        TeamCard card = c.cards.get(b.getTeamId());
        String blocked = c.auction.getStatus() == AuctionStatus.LIVE
                ? AuctionRules.blockedReason(b, c.settings, c.openLot)
                : c.auction.getStatus().name();
        if (blocked == null && c.openLot == null) {
            blocked = "NO_LOT";
        }
        return new TeamRail(b.getTeamId(), card.slug(), card.name(), card.shortCode(), card.colorSlot(), card.logoUrl(),
                b.getInitialBudget(), b.getAdjustments(), b.getSpent(), b.getRemaining(), b.getSquadCount(),
                c.settings.getMaxSquadSize(), AuctionRules.maxBid(b, c.settings), blocked == null, blocked);
    }

    private LotView lotView(Ctx c, AuctionLot l) {
        LotPlayer p = c.people.get(l.getTournamentPlayerId());
        List<BidView> ladder = bids.findByLotIdAndRoundOrderByBidNumberDesc(l.getId(), l.getRound()).stream()
                .map(b -> bidView(c, b)).toList();
        return new LotView(l.getId(), l.getLotNumber(), l.getRound(), l.getState(), l.getCallState(), l.getBasePrice(),
                p != null && p.basePriceOverride() != null, l.getCurrentAmount(), l.getLeadingTeamId(),
                AuctionRules.nextMinimum(l, c.settings), l.getFinalPrice(), l.getWinningTeamId(),
                p == null ? null : new LotPlayerView(p.entryId(), p.slug(), p.fullName(), p.categoryName(), p.photoUrl()),
                ladder, l.getOpenedAt());
    }

    private BidView bidView(Ctx c, AuctionBid b) {
        TeamCard t = c.cards.get(b.getTeamId());
        return new BidView(b.getId(), b.getTeamId(), t == null ? null : t.name(), t == null ? null : t.shortCode(),
                t == null ? null : t.colorSlot(), b.getAmount(), b.getBidNumber(), b.getStatus(), b.getCreatedAt());
    }

    private QueueItem queueItem(Ctx c, AuctionLot l) {
        LotPlayer p = c.people.get(l.getTournamentPlayerId());
        return new QueueItem(l.getId(), l.getTournamentPlayerId(), p == null ? "Unknown player" : p.fullName(),
                p == null ? null : p.categoryName(), p == null ? null : p.photoUrl(), l.getBasePrice(),
                p != null && p.basePriceOverride() != null, l.getState(), l.getRound(), l.getQueueOrder(),
                l.getLotNumber(), l.getFinalPrice(), l.getWinningTeamId(), c.teamName(l.getWinningTeamId()));
    }

    static Counts counts(List<AuctionLot> all) {
        Map<LotState, Long> by = all.stream().collect(Collectors.groupingBy(AuctionLot::getState, Collectors.counting()));
        int withdrawn = by.getOrDefault(LotState.WITHDRAWN, 0L).intValue();
        return new Counts(all.size() - withdrawn, by.getOrDefault(LotState.QUEUED, 0L).intValue(),
                by.getOrDefault(LotState.SOLD, 0L).intValue(), by.getOrDefault(LotState.UNSOLD, 0L).intValue(),
                withdrawn);
    }

    // ---- Public display ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PublicAuctionState publicState() {
        UUID t = tournament.currentTournamentId();
        return auctions.findByTournamentId(t).map(this::publicState).orElseGet(() -> {
            AuctionSettings s = settingsService.peek(t);
            return new PublicAuctionState(AuctionStatus.NOT_STARTED, 1, 0, 0, 0, s.getStartsAt(), null, null,
                    List.of(), List.of(), tournament.settingsFor(t).isShowTeamBudgets());
        });
    }

    /** Called by {@link AuctionEvents} inside the command's transaction. */
    public PublicAuctionState publicState(Auction a) {
        Ctx c = new Ctx(a);
        boolean budgetsPublic = c.site.isShowTeamBudgets();
        boolean pricesPublic = c.site.isShowPlayerPrices();
        Map<UUID, PublicTeamTicker> tickers = tickers(c, budgetsPublic);

        AuctionLot lastClosed = c.allLots.stream()
                .filter(l -> l.getClosedAt() != null && (l.getState() == LotState.SOLD || l.getState() == LotState.UNSOLD))
                .max(Comparator.comparing(AuctionLot::getClosedAt)).orElse(null);

        List<AuctionLot> sold = c.allLots.stream().filter(l -> l.getState() == LotState.SOLD).toList();
        Comparator<AuctionLot> order = pricesPublic
                ? Comparator.comparing(AuctionLot::getFinalPrice).reversed()
                : Comparator.comparing(AuctionLot::getClosedAt).reversed();
        List<PublicBuy> top = sold.stream().sorted(order).limit(TOP_BUYS).map(l -> buy(c, l, pricesPublic)).toList();
        int total = (int) c.allLots.stream().filter(l -> l.getState() != LotState.WITHDRAWN).count();

        return new PublicAuctionState(a.getStatus(), a.getCurrentRound(), a.getStateVersion(), a.getLotsOpened(), total,
                c.settings.getStartsAt(), c.openLot == null ? null : publicLot(c, c.openLot, tickers),
                lastClosed == null ? null : publicLot(c, lastClosed, tickers),
                tickers.values().stream().sorted(Comparator.comparing(PublicTeamTicker::name,
                        String.CASE_INSENSITIVE_ORDER)).toList(),
                top, budgetsPublic);
    }

    private Map<UUID, PublicTeamTicker> tickers(Ctx c, boolean budgetsPublic) {
        Map<UUID, PublicTeamTicker> out = new HashMap<>();
        c.budgetByTeam.values().forEach(b -> {
            TeamCard card = c.cards.get(b.getTeamId());
            out.put(b.getTeamId(), new PublicTeamTicker(card.slug(), card.name(), card.shortCode(), card.colorSlot(),
                    card.logoUrl(), b.getSquadCount(), c.settings.getMaxSquadSize(),
                    budgetsPublic ? b.getRemaining() : null, budgetsPublic ? b.getSpent() : null));
        });
        return out;
    }

    /** Live lot amounts are public — they are called out in the hall (AUC-033). */
    private PublicLot publicLot(Ctx c, AuctionLot l, Map<UUID, PublicTeamTicker> tickers) {
        LotPlayer p = c.people.get(l.getTournamentPlayerId());
        List<PublicBid> last = bids.findByLotIdAndRoundAndStatusOrderByBidNumberDesc(l.getId(), l.getRound(),
                BidStatus.ACCEPTED).stream().limit(LAST_BIDS).map(b -> {
                    TeamCard t = c.cards.get(b.getTeamId());
                    return new PublicBid(t == null ? null : t.name(), t == null ? null : t.shortCode(),
                            t == null ? null : t.colorSlot(), b.getAmount());
                }).toList();
        return new PublicLot(l.getLotNumber(), l.getRound(), l.getState(), l.getCallState(),
                p == null ? null : p.slug(), p == null ? null : p.fullName(), p == null ? null : p.categoryName(),
                p == null ? null : p.photoUrl(), l.getBasePrice(), l.getCurrentAmount(),
                l.getLeadingTeamId() == null ? null : tickers.get(l.getLeadingTeamId()), last, l.getFinalPrice(),
                l.getWinningTeamId() == null ? null : tickers.get(l.getWinningTeamId()));
    }

    private PublicBuy buy(Ctx c, AuctionLot l, boolean pricesPublic) {
        LotPlayer p = c.people.get(l.getTournamentPlayerId());
        TeamCard t = l.getWinningTeamId() == null ? null : c.cards.get(l.getWinningTeamId());
        return new PublicBuy(l.getLotNumber(), p == null ? null : p.slug(), p == null ? null : p.fullName(),
                p == null ? null : p.categoryName(), p == null ? null : p.photoUrl(), t == null ? null : t.name(),
                t == null ? null : t.slug(), t == null ? null : t.colorSlot(),
                pricesPublic ? l.getFinalPrice() : null);
    }

    @Transactional(readOnly = true)
    public PublicResults publicResults() {
        UUID t = tournament.currentTournamentId();
        TournamentSettings site = tournament.settingsFor(t);
        Auction a = auctions.findByTournamentId(t).orElse(null);
        if (a == null || a.getStatus() == AuctionStatus.NOT_STARTED) {
            return new PublicResults(AuctionStatus.NOT_STARTED, List.of(), List.of(), List.of(),
                    site.isShowTeamBudgets(), site.isShowPlayerPrices());
        }
        Ctx c = new Ctx(a);
        boolean prices = site.isShowPlayerPrices();
        List<PublicBuy> sold = c.allLots.stream().filter(l -> l.getState() == LotState.SOLD)
                .sorted(Comparator.comparing(AuctionLot::getLotNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(l -> buy(c, l, prices)).toList();
        List<PublicBuy> unsold = c.allLots.stream().filter(l -> l.getState() == LotState.UNSOLD)
                .map(l -> buy(c, l, false)).toList();
        List<PublicTeamTicker> tickers = tickers(c, site.isShowTeamBudgets()).values().stream()
                .sorted(Comparator.comparing(PublicTeamTicker::name, String.CASE_INSENSITIVE_ORDER)).toList();
        return new PublicResults(a.getStatus(), sold, unsold, tickers, site.isShowTeamBudgets(), prices);
    }

    // ---- Rosters (staff) -----------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('AUCTION_CONFIGURE', 'AUCTION_CONTROL', 'AUCTION_CORRECT')")
    public List<TeamRoster> rosters() {
        UUID t = tournament.currentTournamentId();
        Auction a = auctions.findByTournamentId(t).orElse(null);
        if (a == null || a.getStatus() == AuctionStatus.NOT_STARTED) {
            return List.of();
        }
        Ctx c = new Ctx(a);
        List<TeamPlayer> active = teamPlayers.findByTournamentIdAndActiveTrue(t);
        Map<UUID, LotPlayer> people = players.lotPlayers(active.stream().map(TeamPlayer::getTournamentPlayerId).toList());
        Map<UUID, List<TeamPlayer>> byTeam = active.stream().collect(Collectors.groupingBy(TeamPlayer::getTeamId));
        Map<UUID, UUID> lotByEntry = new HashMap<>();
        c.allLots.forEach(l -> lotByEntry.put(l.getTournamentPlayerId(), l.getId()));
        return c.budgetByTeam.values().stream().map(b -> rail(c, b))
                .sorted(Comparator.comparing(TeamRail::name, String.CASE_INSENSITIVE_ORDER))
                .map(r -> new TeamRoster(r, byTeam.getOrDefault(r.teamId(), List.of()).stream()
                        .sorted(Comparator.comparing(TeamPlayer::getLotNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(tp -> {
                            LotPlayer p = people.get(tp.getTournamentPlayerId());
                            return new RosterEntry(lotByEntry.get(tp.getTournamentPlayerId()), tp.getTournamentPlayerId(), p == null ? null : p.slug(),
                                    p == null ? "Unknown player" : p.fullName(), p == null ? null : p.categoryName(),
                                    p == null ? null : p.photoUrl(), tp.getSource(), tp.getPurchasePrice(),
                                    tp.getLotNumber());
                        }).toList()))
                .toList();
    }

    // ---- Facts for the public player and team pages ---------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PlayerTeam> teamsOf(Collection<UUID> entryIds) {
        UUID t = tournament.currentTournamentId();
        Set<UUID> wanted = new HashSet<>(entryIds);
        boolean prices = tournament.settingsFor(t).isShowPlayerPrices();
        Map<UUID, TeamCard> cards = teams.cards(t);
        Map<UUID, PlayerTeam> out = new HashMap<>();
        for (TeamPlayer tp : teamPlayers.findByTournamentIdAndActiveTrue(t)) {
            if (!wanted.contains(tp.getTournamentPlayerId())) {
                continue;
            }
            TeamCard card = cards.get(tp.getTeamId());
            if (card != null) {
                out.put(tp.getTournamentPlayerId(), new PlayerTeam(card.slug(), card.name(), card.colorSlot(),
                        prices ? tp.getPurchasePrice() : null, tp.getLotNumber()));
            }
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SquadMember> squadOf(UUID teamId) {
        UUID t = tournament.currentTournamentId();
        boolean prices = tournament.settingsFor(t).isShowPlayerPrices();
        List<TeamPlayer> active = teamPlayers.findByTeamIdAndActiveTrue(teamId);
        Map<UUID, LotPlayer> people = players.lotPlayers(active.stream().map(TeamPlayer::getTournamentPlayerId).toList());
        return active.stream()
                .sorted(Comparator.comparing(TeamPlayer::getLotNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(tp -> people.get(tp.getTournamentPlayerId())).filter(Objects::nonNull)
                .map(p -> {
                    TeamPlayer tp = active.stream().filter(x -> x.getTournamentPlayerId().equals(p.entryId()))
                            .findFirst().orElseThrow();
                    return new SquadMember(p.slug(), p.fullName(), p.categoryName(), p.photoUrl(),
                            prices ? tp.getPurchasePrice() : null, tp.getLotNumber());
                }).toList();
    }
}
