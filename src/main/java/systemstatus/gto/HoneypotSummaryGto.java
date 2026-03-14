package systemstatus.gto;

public record HoneypotSummaryGto(
    long totalSessions,
    long totalLogins,
    long successfulLogins,
    long uniqueIps,
    long totalCommands
) {}