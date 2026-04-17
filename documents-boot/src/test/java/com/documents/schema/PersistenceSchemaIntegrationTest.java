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
    @DisplayName("문서와 블록 기본 키는 도메인별 컬럼명으로 생성된다")
    void primaryKeyColumnsUseDomainSpecificNames() {
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
    @DisplayName("문서 visibility 컬럼은 문자열 enum으로 생성되고 null을 허용하지 않는다")
    void documentVisibilityColumnIsRequired() {
        assertThat(countColumn("DOCUMENTS", "VISIBILITY")).isEqualTo(1);
        assertThat(isNullable("DOCUMENTS", "VISIBILITY")).isFalse();
    }

    @Test
    @DisplayName("블록 sort_key 컬럼 길이는 ordered sortKey 정책 길이와 일치한다")
    void blockSortKeyColumnLengthMatchesPolicy() {
        assertThat(characterMaximumLength("BLOCKS", "SORT_KEY")).isEqualTo(24);
    }

    @Test
    @DisplayName("문서 연관관계 FK는 parent cascade delete 규칙을 생성한다")
    void documentForeignKeysUseExpectedDeleteRules() {
        assertThat(countForeignKey("DOCUMENTS", "FK_DOCUMENTS_PARENT")).isEqualTo(1);
        assertThat(deleteRule("FK_DOCUMENTS_PARENT")).isEqualTo("CASCADE");
    }

    @Test
    @DisplayName("블록 연관관계 FK는 document 참조와 parent cascade delete 규칙을 생성한다")
    void blockForeignKeysUseExpectedDeleteRules() {
        assertThat(countForeignKey("BLOCKS", "FK_BLOCKS_DOCUMENT")).isEqualTo(1);
        assertThat(countForeignKey("BLOCKS", "FK_BLOCKS_PARENT")).isEqualTo(1);
        assertThat(deleteRule("FK_BLOCKS_DOCUMENT")).isEqualTo("CASCADE");
        assertThat(deleteRule("FK_BLOCKS_PARENT")).isEqualTo("CASCADE");
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

    private int countForeignKey(String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.table_constraints
                        where table_name = ?
                          and constraint_type = 'FOREIGN KEY'
                          and constraint_name = ?
                        """,
                Integer.class,
                tableName,
                constraintName
        );
        return count == null ? 0 : count;
    }

    private String deleteRule(String constraintName) {
        return jdbcTemplate.queryForObject(
                """
                        select delete_rule
                        from information_schema.referential_constraints
                        where constraint_name = ?
                        """,
                String.class,
                constraintName
        );
    }
}
