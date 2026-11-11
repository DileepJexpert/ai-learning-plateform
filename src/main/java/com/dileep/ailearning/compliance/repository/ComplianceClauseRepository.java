package com.dileep.ailearning.compliance.repository;

import com.dileep.ailearning.compliance.model.ComplianceClause;
import com.dileep.ailearning.compliance.search.ReciprocalRankFusion.ScoredItem;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * PostgreSQL + pgvector repository for regulatory circular clauses (Blueprint 1).
 * Supports both dense cosine similarity search and sparse full-text search (BM25).
 */
@Repository
public class ComplianceClauseRepository {

    private final JdbcClient jdbc;

    public ComplianceClauseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Store a compliance clause with its embedding vector.
     */
    public void save(ComplianceClause clause) {
        jdbc.sql("""
                INSERT INTO compliance_clauses (circular_id, circular_title, authority, effective_date,
                                                clause_ref, section_title, content, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector)
                """)
                .param(clause.circularId())
                .param(clause.circularTitle())
                .param(clause.authority())
                .param(clause.effectiveDate())
                .param(clause.clauseRef())
                .param(clause.sectionTitle())
                .param(clause.content())
                .param(new PGvector(clause.embedding()).toString())
                .update();
    }

    /**
     * Batch insert clauses.
     */
    public void saveAll(List<ComplianceClause> clauses) {
        for (ComplianceClause clause : clauses) {
            save(clause);
        }
    }

    /**
     * Dense cosine-similarity search.
     */
    public List<ScoredItem<ComplianceClause>> searchDense(float[] queryEmbedding, String circularIdFilter, int limit) {
        String sql = """
                SELECT id, circular_id, circular_title, authority, effective_date,
                       clause_ref, section_title, content,
                       1 - (embedding <=> ?::vector) AS score
                FROM compliance_clauses
                WHERE (? IS NULL OR circular_id = ?)
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;

        String vectorStr = new PGvector(queryEmbedding).toString();
        return jdbc.sql(sql)
                .param(vectorStr)
                .param(circularIdFilter)
                .param(circularIdFilter)
                .param(vectorStr)
                .param(limit)
                .query((rs, rowNum) -> new ScoredItem<>(mapClause(rs), rs.getDouble("score")))
                .list();
    }

    /**
     * Sparse full-text (BM25) search using PostgreSQL tsvector and ts_rank_cd.
     */
    public List<ScoredItem<ComplianceClause>> searchSparse(String queryText, String circularIdFilter, int limit) {
        String sql = """
                SELECT id, circular_id, circular_title, authority, effective_date,
                       clause_ref, section_title, content,
                       ts_rank_cd(tsv_content, plainto_tsquery('english', ?)) AS score
                FROM compliance_clauses
                WHERE tsv_content @@ plainto_tsquery('english', ?)
                  AND (? IS NULL OR circular_id = ?)
                ORDER BY score DESC
                LIMIT ?
                """;

        return jdbc.sql(sql)
                .param(queryText)
                .param(queryText)
                .param(circularIdFilter)
                .param(circularIdFilter)
                .param(limit)
                .query((rs, rowNum) -> new ScoredItem<>(mapClause(rs), rs.getDouble("score")))
                .list();
    }

    /**
     * Count total stored clauses.
     */
    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM compliance_clauses")
                .query(Long.class)
                .single();
    }

    /**
     * Delete clauses for a circular.
     */
    public int deleteByCircularId(String circularId) {
        return jdbc.sql("DELETE FROM compliance_clauses WHERE circular_id = ?")
                .param(circularId)
                .update();
    }

    private ComplianceClause mapClause(ResultSet rs) throws SQLException {
        var ts = rs.getTimestamp("created_at");
        return new ComplianceClause(
                rs.getLong("id"),
                rs.getString("circular_id"),
                rs.getString("circular_title"),
                rs.getString("authority"),
                rs.getString("effective_date"),
                rs.getString("clause_ref"),
                rs.getString("section_title"),
                rs.getString("content"),
                null,
                ts != null ? ts.toInstant() : null
        );
    }
}
