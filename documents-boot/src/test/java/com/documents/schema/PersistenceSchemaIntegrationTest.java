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

        assertThat(countColumn("DOCUMENT_RESOURCES", "DOCUMENT_RESOURCE_ID")).isEqualTo(1);
        assertThat(countColumn("DOCUMENT_RESOURCES", "ID")).isZero();
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
    @DisplayName("문서와 블록 감사 컬럼은 created_by, modified_by, deleted_at으로 생성된다")
    void auditableColumnsUseExpectedNames() {
        assertThat(countColumn("DOCUMENTS", "CREATED_BY")).isEqualTo(1);
        assertThat(countColumn("DOCUMENTS", "MODIFIED_BY")).isEqualTo(1);
        assertThat(countColumn("DOCUMENTS", "DELETED_AT")).isEqualTo(1);
        assertThat(countColumn("DOCUMENTS", "UPDATED_BY")).isZero();

        assertThat(countColumn("BLOCKS", "CREATED_BY")).isEqualTo(1);
        assertThat(countColumn("BLOCKS", "MODIFIED_BY")).isEqualTo(1);
        assertThat(countColumn("BLOCKS", "DELETED_AT")).isEqualTo(1);
        assertThat(countColumn("BLOCKS", "UPDATED_BY")).isZero();

        assertThat(countColumn("DOCUMENT_RESOURCES", "CREATED_BY")).isEqualTo(1);
        assertThat(countColumn("DOCUMENT_RESOURCES", "MODIFIED_BY")).isEqualTo(1);
        assertThat(countColumn("DOCUMENT_RESOURCES", "DELETED_AT")).isEqualTo(1);
        assertThat(countColumn("DOCUMENT_RESOURCES", "OWNER_USER_ID")).isEqualTo(1);
        assertThat(countColumn("DOCUMENT_RESOURCES", "DOCUMENT_VERSION")).isEqualTo(1);
        assertThat(countColumn("DOCUMENT_RESOURCES", "STATUS")).isEqualTo(1);
        assertThat(countColumn("DOCUMENT_RESOURCES", "PURGE_AT")).isEqualTo(1);
        assertThat(countColumn("DOCUMENT_RESOURCES", "LAST_ERROR")).isEqualTo(1);
        assertThat(countColumn("DOCUMENT_RESOURCES", "REPAIRED_AT")).isEqualTo(1);
        assertThat(countColumn("DOCUMENT_RESOURCES", "UPDATED_BY")).isZero();
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

    @Test
    @DisplayName("문서 리소스 ledger는 문서와 블록 FK 없이 독립 컬럼으로 생성된다")
    void documentResourceLedgerUsesStandaloneColumns() {
        assertThat(countColumn("DOCUMENT_RESOURCES", "DOCUMENT_ID")).isEqualTo(1);
        assertThat(countColumn("DOCUMENT_RESOURCES", "BLOCK_ID")).isEqualTo(1);
        assertThat(countForeignKeys("DOCUMENT_RESOURCES")).isZero();
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

    private int countForeignKeys(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.table_constraints
                        where table_name = ?
                          and constraint_type = 'FOREIGN KEY'
                        """,
                Integer.class,
                tableName
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
