package systemstatus.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import systemstatus.gto.*;

@Service
public class HoneypotService {

    private final JdbcTemplate jdbc;

    public HoneypotService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public HoneypotSummaryGto getSummary() {
        return jdbc.queryForObject("""
            SELECT
                (SELECT COUNT(*) FROM cowrie_sessions),
                (SELECT COUNT(*) FROM cowrie_logins),
                (SELECT COUNT(*) FROM cowrie_logins WHERE success = true),
                (SELECT COUNT(DISTINCT src_ip) FROM cowrie_sessions),
                (SELECT COUNT(*) FROM cowrie_commands)
            """,
            (rs, i) -> new HoneypotSummaryGto(
                rs.getLong(1), rs.getLong(2), rs.getLong(3),
                rs.getLong(4), rs.getLong(5)
            ));
    }

    public List<LoginAttemptGto> getRecentLogins(int limit) {
        return jdbc.query("""
            SELECT l.timestamp, l.src_ip, l.username, l.password, l.success,
                   g.country, g.country_code, g.city
            FROM cowrie_logins l
            LEFT JOIN ip_geo g ON l.src_ip = g.ip
            ORDER BY l.timestamp DESC
            LIMIT ?
            """,
            (rs, i) -> new LoginAttemptGto(
                rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getBoolean(5),
                rs.getString(6), rs.getString(7), rs.getString(8)
            ), limit);
    }

    public List<CredentialGto> getTopCredentials(int limit) {
        return jdbc.query("""
            SELECT username, password, COUNT(*) as count
            FROM cowrie_logins
            GROUP BY username, password
            ORDER BY count DESC
            LIMIT ?
            """,
            (rs, i) -> new CredentialGto(
                rs.getString(1), rs.getString(2), rs.getLong(3)
            ), limit);
    }

    public List<SourceIpGto> getTopIps(int limit) {
        return jdbc.query("""
            SELECT s.src_ip, COUNT(*) as count,
                   g.country, g.country_code, g.city, g.latitude, g.longitude
            FROM cowrie_sessions s
            LEFT JOIN ip_geo g ON s.src_ip = g.ip
            GROUP BY s.src_ip, g.country, g.country_code, g.city, g.latitude, g.longitude
            ORDER BY count DESC
            LIMIT ?
            """,
            (rs, i) -> new SourceIpGto(
                rs.getString(1), rs.getLong(2), rs.getString(3),
                rs.getString(4), rs.getString(5),
                rs.getObject(6, Double.class), rs.getObject(7, Double.class)
            ), limit);
    }

    public List<CommandGto> getRecentCommands(int limit) {
        return jdbc.query("""
            SELECT c.timestamp, c.src_ip, c.input, g.country
            FROM cowrie_commands c
            LEFT JOIN ip_geo g ON c.src_ip = g.ip
            ORDER BY c.timestamp DESC
            LIMIT ?
            """,
            (rs, i) -> new CommandGto(
                rs.getString(1), rs.getString(2),
                rs.getString(3), rs.getString(4)
            ), limit);
    }

    public List<TopCommandGto> getTopCommands(int limit) {
        return jdbc.query("""
            SELECT input, COUNT(*) as count
            FROM cowrie_commands
            GROUP BY input
            ORDER BY count DESC
            LIMIT ?
            """,
            (rs, i) -> new TopCommandGto(
                rs.getString(1), rs.getLong(2)
            ), limit);
    }

    public List<ActivityGto> getHourlyActivity(int hours) {
        return jdbc.query("""
            SELECT TO_CHAR(date_trunc('hour', timestamp), 'YYYY-MM-DD HH24:00') as period,
                   COUNT(*) as count
            FROM cowrie_logins
            WHERE timestamp > NOW() - MAKE_INTERVAL(hours => ?)
            GROUP BY period
            ORDER BY period
            """,
            (rs, i) -> new ActivityGto(
                rs.getString(1), rs.getLong(2)
            ), hours);
    }

    public List<SourceIpGto> getGeoData() {
        return jdbc.query("""
            SELECT g.ip, COUNT(s.id) as count,
                   g.country, g.country_code, g.city, g.latitude, g.longitude
            FROM ip_geo g
            JOIN cowrie_sessions s ON s.src_ip = g.ip
            WHERE g.latitude IS NOT NULL
            GROUP BY g.ip, g.country, g.country_code, g.city, g.latitude, g.longitude
            """,
            (rs, i) -> new SourceIpGto(
                rs.getString(1), rs.getLong(2), rs.getString(3),
                rs.getString(4), rs.getString(5),
                rs.getObject(6, Double.class), rs.getObject(7, Double.class)
            ));
    }
}