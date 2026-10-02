package com.substation.ops.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 启动时把 procedures.sql 里的存储过程建到 MySQL 上。
 * 之所以放在这里而不是 mysql/schema.sql：后者是构建期在容器里用 mysql 客户端跑的，
 * 带 BEGIN...END 的过程体会被语句分隔符截断，只能在应用侧执行。
 */
@Slf4j
@Component
@Order(1)
public class ProcedureInitializer implements ApplicationRunner {

    private static final String SCRIPT = "procedures.sql";

    /** 语句分隔符行（单独一行 "$$"），避免过程体里的分号被误切 */
    private static final String STATEMENT_SEPARATOR = "\\$\\$";

    private final JdbcTemplate jdbcTemplate;

    public ProcedureInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> statements = loadStatements();
        for (String sql : statements) {
            try {
                jdbcTemplate.execute(sql);
            } catch (RuntimeException e) {
                log.error("执行存储过程脚本失败，已中断后续流程。SQL 片段：{}", sql, e);
                throw e;
            }
        }
        log.info("存储过程初始化完成，共执行 {} 条语句", statements.size());
    }

    private List<String> loadStatements() {
        ClassPathResource resource = new ClassPathResource(SCRIPT);
        String raw;
        try (InputStream in = resource.getInputStream()) {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 " + SCRIPT + " 失败", e);
        }

        List<String> statements = new ArrayList<>();
        for (String chunk : raw.split(STATEMENT_SEPARATOR)) {
            String sql = stripComments(chunk).trim();
            if (!sql.isEmpty()) {
                statements.add(sql);
            }
        }
        return statements;
    }

    private String stripComments(String chunk) {
        StringBuilder kept = new StringBuilder();
        for (String line : chunk.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--")) {
                kept.append(line).append('\n');
            }
        }
        return kept.toString();
    }
}
