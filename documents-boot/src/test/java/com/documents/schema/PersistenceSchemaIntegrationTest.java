package com.documents.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@DisplayName("JPA 스키마 매핑 검증")
class PersistenceSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("워크스페이스와 문서와 블록 기본 키는 도메인별 컬럼명으로 생성된다")
    void primaryKeyColumnsUseDomainSpecificNames() {
        assertThat(countColumn("WORKSPACES", "WORKSPACE_ID")).isEqualTo(1);
        assertThat(countColumn("WORKSPACES", "ID")).isZero();

        assertThat(countColumn("DOCUMENTS", "DOCUMENT_ID")).isEqualTo(1);
        assertThat(countColumn("DOCUMENTS", "ID")).isZero();

        assertThat(countColumn("BLOCKS", "BLOCK_ID")).isEqualTo(1);
        assertThat(countColumn("BLOCKS", "ID")).isZero();
    }

    @Test
    @DisplayName("문서와 블록 sort_key 컬럼은 null을 허용하지 않는다")
    void sortKeyColumnsAreNotNullable() {
        assertThat(isNullable("DOCUMENTS", "SORT_KEY")).isFalse();
        assertThat(isNullable("BLOCKS", "SORT_KEY")).isFalse();
    }

    @Test
    @DisplayName("블록 sort_key 컬럼 길이는 ordered sortKey 정책 길이와 일치한다")
    void blockSortKeyColumnLengthMatchesPolicy() {
        assertThat(characterMaximumLength("BLOCKS", "SORT_KEY")).isEqualTo(24);
    }

    private int countColumn(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.columns
                        where table_name = ?
                          and column_name = ?
                        """,
                Integer.class,
                tableName,
                columnName
        );
        return count == null ? 0 : count;
    }

    private boolean isNullable(String tableName, String columnName) {
        String nullable = jdbcTemplate.queryForObject(
                """
                        select is_nullable
                        from information_schema.columns
                        where table_name = ?
                          and column_name = ?
                        """,
                String.class,
                tableName,
                columnName
        );
        return "YES".equalsIgnoreCase(nullable);
    }

    private int characterMaximumLength(String tableName, String columnName) {
        Integer length = jdbcTemplate.queryForObject(
                """
                        select character_maximum_length
                        from information_schema.columns
                        where table_name = ?
                          and column_name = ?
                        """,
                Integer.class,
                tableName,
                columnName
        );
        return length == null ? 0 : length;
    }
}
