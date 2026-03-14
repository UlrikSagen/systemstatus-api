package systemstatus.gto;

public record CredentialGto(
    String username,
    String password,
    long count
) {}