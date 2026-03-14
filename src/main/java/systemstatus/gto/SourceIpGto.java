package systemstatus.gto;

public record SourceIpGto(
    String ip,
    long count,
    String country,
    String countryCode,
    String city,
    Double latitude,
    Double longitude
) {}