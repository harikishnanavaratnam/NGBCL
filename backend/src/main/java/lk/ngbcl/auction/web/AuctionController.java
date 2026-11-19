package lk.ngbcl.auction.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.auction.domain.CallState;
import lk.ngbcl.auction.service.AuctionCorrections;
import lk.ngbcl.auction.service.AuctionDtos.ConsoleSnapshot;
import lk.ngbcl.auction.service.AuctionDtos.SettingsView;
import lk.ngbcl.auction.service.AuctionDtos.TeamRoster;
import lk.ngbcl.auction.service.AuctionEngine;
import lk.ngbcl.auction.service.AuctionEngine.BidCommand;
import lk.ngbcl.auction.service.AuctionEngine.BidResult;
import lk.ngbcl.auction.service.AuctionReadModel;
import lk.ngbcl.auction.service.AuctionSettingsService;
import lk.ngbcl.auction.service.AuctionSettingsService.SettingsInput;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff auction API. Settings and queue need AUCTION_CONFIGURE, the live console AUCTION_CONTROL,
 * corrections AUCTION_CORRECT (enforced on the services). Every command answers with the fresh
 * console snapshot so the caller never shows stale state.
 */
@RestController
@RequestMapping("/api/admin/auction")
public class AuctionController {

    private final AuctionSettingsService settings;
    private final AuctionEngine engine;
    private final AuctionCorrections corrections;
    private final AuctionReadModel read;

    public AuctionController(AuctionSettingsService settings, AuctionEngine engine, AuctionCorrections corrections,
            AuctionReadModel read) {
        this.settings = settings;
        this.engine = engine;
        this.corrections = corrections;
        this.read = read;
    }

    public record SettingsRequest(long initialBudget, int maxSquadSize, Integer minSquadSize, long defaultBasePrice,
            long minIncrement, boolean allowReauction, boolean allowManualAdjustment, Instant startsAt, Long version,
            @Size(max = 300) String reason) {
    }

    public record OrderRequest(@NotNull List<UUID> lotIds) {
    }

    public record BasePriceRequest(Long basePrice) {
    }

    public record OpenRequest(UUID lotId) {
    }

    public record CallRequest(@NotNull UUID lotId, @NotNull CallState state) {
    }

    public record BidRequest(@NotNull UUID lotId, @NotNull UUID teamId, long amount) {
    }

    public record LotRequest(@NotNull UUID lotId) {
    }

    public record SellRequest(@NotNull UUID lotId, @NotNull UUID bidId) {
    }

    public record ReasonRequest(@Size(max = 300) String reason) {
    }

    public record NextRoundRequest(List<UUID> lotIds) {
    }

    public record MoveRequest(@NotNull UUID teamId, Long price, @Size(max = 300) String reason) {
    }

    public record AdjustRequest(long amount, @Size(max = 300) String reason) {
    }

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record BidResponse(BidResult bid, ConsoleSnapshot snapshot) {
    }

    // ---- Settings ------------------------------------------------------------------------

    @GetMapping("/settings")
    public SettingsView getSettings() {
        return settings.get();
    }

    @PutMapping("/settings")
    public SettingsView updateSettings(@Valid @RequestBody SettingsRequest r) {
        return settings.update(new SettingsInput(r.initialBudget(), r.maxSquadSize(), r.minSquadSize(),
                r.defaultBasePrice(), r.minIncrement(), r.allowReauction(), r.allowManualAdjustment(), r.startsAt(),
                r.version(), r.reason()));
    }

    // ---- Snapshot & queue ----------------------------------------------------------------

    /** Prepares the auction if needed (queue follows approved players) and returns the console state. */
    @GetMapping
    public ConsoleSnapshot snapshot() {
        engine.prepare();
        return read.console();
    }

    @PutMapping("/queue/order")
    public ConsoleSnapshot reorder(@Valid @RequestBody OrderRequest r) {
        engine.reorder(r.lotIds());
        return read.console();
    }

    @PutMapping("/players/{entryId}/base-price")
    public ConsoleSnapshot basePrice(@PathVariable UUID entryId, @RequestBody BasePriceRequest r) {
        engine.setBasePrice(entryId, r.basePrice());
        return read.console();
    }

    // ---- Live console ----------------------------------------------------------------------

    @PostMapping("/start")
    public ConsoleSnapshot start() {
        engine.start();
        return read.console();
    }

    @PostMapping("/pause")
    public ConsoleSnapshot pause() {
        engine.pause();
        return read.console();
    }

    @PostMapping("/resume")
    public ConsoleSnapshot resume() {
        engine.resume();
        return read.console();
    }

    @PostMapping("/end")
    public ConsoleSnapshot end(@Valid @RequestBody(required = false) ReasonRequest r) {
        engine.end(r == null ? null : r.reason());
        return read.console();
    }

    @PostMapping("/lots/open")
    public ConsoleSnapshot open(@RequestBody(required = false) OpenRequest r) {
        engine.openLot(r == null ? null : r.lotId());
        return read.console();
    }

    @PostMapping("/lots/call")
    public ConsoleSnapshot call(@Valid @RequestBody CallRequest r) {
        engine.call(r.lotId(), r.state());
        return read.console();
    }

    /** Idempotency-Key header: retrying the same bid (e.g. after a timeout) never records it twice. */
    @PostMapping("/bids")
    @ResponseStatus(HttpStatus.CREATED)
    public BidResponse bid(@RequestHeader(name = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody BidRequest r) {
        BidResult result = engine.bid(new BidCommand(r.lotId(), r.teamId(), r.amount(), key));
        return new BidResponse(result, read.console());
    }

    @PostMapping("/bids/undo")
    public ConsoleSnapshot undo(@Valid @RequestBody LotRequest r) {
        engine.undoLastBid(r.lotId());
        return read.console();
    }

    @PostMapping("/lots/sell")
    public ConsoleSnapshot sell(@Valid @RequestBody SellRequest r) {
        engine.sell(r.lotId(), r.bidId());
        return read.console();
    }

    @PostMapping("/lots/unsold")
    public ConsoleSnapshot unsold(@Valid @RequestBody LotRequest r) {
        engine.markUnsold(r.lotId());
        return read.console();
    }

    @PostMapping("/lots/{lotId}/withdraw")
    public ConsoleSnapshot withdraw(@PathVariable UUID lotId, @Valid @RequestBody ReasonRequest r) {
        engine.withdraw(lotId, r.reason());
        return read.console();
    }

    @PostMapping("/lots/{lotId}/restore")
    public ConsoleSnapshot restore(@PathVariable UUID lotId) {
        engine.restore(lotId);
        return read.console();
    }

    @PostMapping("/rounds/next")
    public ConsoleSnapshot nextRound(@RequestBody(required = false) NextRoundRequest r) {
        engine.nextRound(r == null ? null : r.lotIds());
        return read.console();
    }

    // ---- Rosters & corrections -------------------------------------------------------------

    @GetMapping("/rosters")
    public List<TeamRoster> rosters() {
        return read.rosters();
    }

    @PostMapping("/lots/{lotId}/release")
    public List<TeamRoster> release(@PathVariable UUID lotId, @Valid @RequestBody ReasonRequest r) {
        corrections.releaseSale(lotId, r.reason());
        return read.rosters();
    }

    @PostMapping("/lots/{lotId}/move")
    public List<TeamRoster> move(@PathVariable UUID lotId, @Valid @RequestBody MoveRequest r) {
        corrections.moveSale(lotId, r.teamId(), r.price(), r.reason());
        return read.rosters();
    }

    @PostMapping("/teams/{teamId}/adjust")
    public List<TeamRoster> adjust(@PathVariable UUID teamId, @Valid @RequestBody AdjustRequest r) {
        corrections.adjustBudget(teamId, r.amount(), r.reason());
        return read.rosters();
    }
}
