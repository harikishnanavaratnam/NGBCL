package lk.ngbcl.reporting.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import lk.ngbcl.reporting.service.ReportService;
import lk.ngbcl.reporting.service.ReportService.Report;
import lk.ngbcl.shared.web.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin › System › Reports. The admin app turns each report into an Excel workbook or CSV. */
@RestController
@RequestMapping("/api/admin/reports")
public class ReportsController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    private final ReportService reports;

    public ReportsController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/results")
    public Report results() {
        return reports.results();
    }

    @GetMapping("/standings")
    public Report standings() {
        return reports.standingsReport();
    }

    @GetMapping("/players")
    public Report players() {
        return reports.players();
    }

    @GetMapping("/auction")
    public Report auction() {
        return reports.auction();
    }

    /** {@code from} / {@code to} are calendar days in Asia/Colombo; {@code to} is inclusive. */
    @GetMapping("/audit")
    public Report audit(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return reports.audit(day(from, false), day(to, true));
    }

    private static Instant day(String value, boolean end) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate d = LocalDate.parse(value.trim());
            return (end ? d.plusDays(1) : d).atStartOfDay(ZONE).toInstant();
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("INVALID_DATE", "Use dates like 2026-11-20.");
        }
    }
}
