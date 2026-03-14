package systemstatus.gto;

public record CommandGto(
    String timestamp,
    String sourceIp,
    String input,
    String country
) {}