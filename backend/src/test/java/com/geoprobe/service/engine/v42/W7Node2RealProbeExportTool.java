package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class W7Node2RealProbeExportTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path DEFAULT_ENV_FILE = Path.of("../.env");

    private W7Node2RealProbeExportTool() {
    }

    public static void main(String[] args) throws Exception {
        Args parsed = Args.parse(args);
        Map<String, String> env = loadEnv(parsed.envFile());
        DbConfig dbConfig = DbConfig.fromEnv(env);
        List<String> competitors;
        List<Map<String, Object>> rows;
        try (Connection connection = DriverManager.getConnection(dbConfig.jdbcUrl(), dbConfig.username(), dbConfig.password())) {
            competitors = loadCompetitors(connection, parsed.taskId());
            rows = loadProbeRows(connection, parsed.taskId(), competitors);
        }
        Files.createDirectories(parsed.output().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(parsed.output(), StandardCharsets.UTF_8)) {
            for (Map<String, Object> row : rows) {
                writer.write(MAPPER.writeValueAsString(row));
                writer.newLine();
            }
        }
        System.out.println("taskId=" + parsed.taskId());
        System.out.println("probeCount=" + rows.size());
        System.out.println("competitorCount=" + competitors.size());
        System.out.println("output=" + parsed.output());
    }

    private static List<String> loadCompetitors(Connection connection, String taskId) throws SQLException {
        String sql = """
                SELECT competitor_name_snapshot
                FROM diagnosis_task_competitors
                WHERE diagnosis_task_id = ?
                ORDER BY seq_no ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, taskId);
            try (ResultSet rs = statement.executeQuery()) {
                ArrayList<String> competitors = new ArrayList<>();
                while (rs.next()) {
                    String competitor = rs.getString("competitor_name_snapshot");
                    if (StringUtils.hasText(competitor) && !competitors.contains(competitor.trim())) {
                        competitors.add(competitor.trim());
                    }
                }
                return List.copyOf(competitors);
            }
        }
    }

    private static List<Map<String, Object>> loadProbeRows(Connection connection,
                                                           String taskId,
                                                           List<String> competitors) throws SQLException {
        String sql = """
                SELECT p.id,
                       p.probe_ref,
                       p.bucket_type_code,
                       p.probe_type_code,
                       p.d1_identity,
                       p.d2_context,
                       p.d3_pain_point,
                       p.d4_jtbd,
                       p.d5_knowledge,
                       p.d6_constraints,
                       p.render_text,
                       p.triggered_rules_json,
                       t.brand_name_snapshot,
                       t.category_snapshot
                FROM diagnosis_probes p
                JOIN diagnosis_tasks t ON t.id = p.diagnosis_task_id
                WHERE p.diagnosis_task_id = ?
                ORDER BY p.seq_no ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, taskId);
            try (ResultSet rs = statement.executeQuery()) {
                ArrayList<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(toExportRow(rs, competitors));
                }
                return List.copyOf(rows);
            }
        }
    }

    private static Map<String, Object> toExportRow(ResultSet rs, List<String> competitors) throws SQLException {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("probe_id", rs.getString("id"));
        row.put("probe_ref", rs.getString("probe_ref"));
        row.put("brand", rs.getString("brand_name_snapshot"));
        row.put("category", rs.getString("category_snapshot"));
        row.put("competitors", competitors);
        row.put("bucket_type", upper(rs.getString("bucket_type_code")));
        row.put("probe_type", upper(rs.getString("probe_type_code")));
        row.put("state_vector", Map.of(
                "D1", upper(rs.getString("d1_identity")),
                "D2", upper(rs.getString("d2_context")),
                "D3", upper(rs.getString("d3_pain_point")),
                "D4", upper(rs.getString("d4_jtbd")),
                "D5", upper(rs.getString("d5_knowledge")),
                "D6", upper(rs.getString("d6_constraints"))
        ));
        row.put("probe_text", blankToEmpty(rs.getString("render_text")));
        row.put("triggered_rules_json", blankToEmpty(rs.getString("triggered_rules_json")));
        return Map.copyOf(row);
    }

    private static String upper(String value) {
        return blankToEmpty(value).trim();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, String> loadEnv(Path path) throws IOException {
        LinkedHashMap<String, String> env = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return env;
        }
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            int idx = line.indexOf('=');
            env.put(line.substring(0, idx).trim(), stripQuotes(line.substring(idx + 1).trim()));
        }
        return env;
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record DbConfig(String host, int port, String database, String username, String password) {

        static DbConfig fromEnv(Map<String, String> env) {
            String host = firstNonBlank(System.getenv("DB_HOST"), env.get("DB_HOST"), "127.0.0.1");
            String portText = firstNonBlank(System.getenv("DB_PORT"), env.get("DB_PORT"), "3306");
            String database = firstNonBlank(System.getenv("DB_NAME"), env.get("DB_NAME"), "geoprobe");
            String username = firstNonBlank(System.getenv("DB_USERNAME"), env.get("DB_USERNAME"));
            String password = firstNonBlank(System.getenv("DB_PASSWORD"), env.get("DB_PASSWORD"));
            if (!StringUtils.hasText(username)) {
                throw new IllegalStateException("缺少 DB_USERNAME");
            }
            return new DbConfig(host.trim(), Integer.parseInt(portText.trim()), database.trim(), username.trim(), Objects.requireNonNullElse(password, ""));
        }

        String jdbcUrl() {
            return "jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8"
                    .formatted(host, port, database);
        }
    }

    private record Args(Path output, Path envFile, String taskId) {

        private static Args parse(String[] args) {
            Path output = null;
            Path envFile = DEFAULT_ENV_FILE;
            String taskId = null;
            for (int index = 0; index < args.length; index++) {
                String arg = Objects.requireNonNullElse(args[index], "").trim();
                switch (arg) {
                    case "--output" -> output = Path.of(requireValue(args, ++index, "--output"));
                    case "--env-file" -> envFile = Path.of(requireValue(args, ++index, "--env-file"));
                    case "--task-id" -> taskId = requireValue(args, ++index, "--task-id");
                    default -> throw new IllegalArgumentException("未知参数: " + arg);
                }
            }
            if (output == null || !StringUtils.hasText(taskId)) {
                throw new IllegalArgumentException("必须提供 --output、--task-id");
            }
            return new Args(output, envFile, taskId.trim());
        }

        private static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length || !StringUtils.hasText(args[index])) {
                throw new IllegalArgumentException(flag + " 缺少值");
            }
            return args[index];
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return "";
    }
}
