package systemstatus.gto;

public record LoginAttemptGto(
    String timestamp,
    String sourceIp,
    String username,
    String password,
    boolean success,
    String country,
    String countryCode,
    String city
) {}