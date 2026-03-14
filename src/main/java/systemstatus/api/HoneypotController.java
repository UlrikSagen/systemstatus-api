package systemstatus.api;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import systemstatus.gto.*;
import systemstatus.service.HoneypotService;

@RestController
@RequestMapping("/honeypot")
public class HoneypotController {

    private final HoneypotService service;

    public HoneypotController(HoneypotService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public HoneypotSummaryGto summary() {
        return service.getSummary();
    }

    @GetMapping("/recent-logins")
    public List<LoginAttemptGto> recentLogins(@RequestParam(defaultValue = "20") int limit) {
        return service.getRecentLogins(Math.min(limit, 100));
    }

    @GetMapping("/recent-commands")
    public List<CommandGto> recentCommands(@RequestParam(defaultValue = "20") int limit) {
        return service.getRecentCommands(Math.min(limit, 100));
    }

    @GetMapping("/top-ips")
    public List<SourceIpGto> topIps(@RequestParam(defaultValue = "10") int limit) {
        return service.getTopIps(Math.min(limit, 50));
    }

    @GetMapping("/top-credentials")
    public List<CredentialGto> topCredentials(@RequestParam(defaultValue = "10") int limit) {
        return service.getTopCredentials(Math.min(limit, 50));
    }

    @GetMapping("/top-commands")
    public List<TopCommandGto> topCommands(@RequestParam(defaultValue = "10") int limit) {
        return service.getTopCommands(Math.min(limit, 50));
    }

    @GetMapping("/activity")
    public List<ActivityGto> activity(@RequestParam(defaultValue = "24") int hours) {
        return service.getHourlyActivity(Math.min(hours, 168));
    }

    @GetMapping("/geo")
    public List<SourceIpGto> geo() {
        return service.getGeoData();
    }
}