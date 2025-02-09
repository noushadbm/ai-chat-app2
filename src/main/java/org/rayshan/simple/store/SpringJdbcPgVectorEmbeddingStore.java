package org.rayshan.simple.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.log4j.Log4j2;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

@Log4j2
public class SpringJdbcPgVectorEmbeddingStore implements EmbeddingStore<TextSegment> {
    private static final String SQL_INSERT_EMBEDDING = "INSERT INTO dataset_embedding " +
            "(id, dataset_id, file_name, embedding_vector, document_text, metadata, version_count, created_by, creation_timestamp, modified_by, modification_timestamp) "
            + "VALUES (:id, :datasetId, :fileName, :embeddingVector, :documentText, :metadata::jsonb, :versionCount, :createdBy, :creationTimestamp, :modifiedBy, :modificationTimestamp)";

    private static final String SQL_DELETE_EMBEDDING_BY_FILENAME = "DELETE FROM dataset_embedding WHERE file_name = :fileName AND dataset_id = :datasetId";

    private static final String SQL_FIND_RELEVANT = "WITH temp AS (" +
            "SELECT (2 - (embedding_vector <=> :embeddingVector)) / 2 AS score, id, embedding_vector, document_text, metadata "
            + "FROM dataset_embedding WHERE dataset_id IN (:datasetIds)) "
            + "SELECT * FROM temp WHERE score >= :minScore ORDER BY score DESC LIMIT :maxResults";

    private ObjectMapper objectMapper;
    private NamedParameterJdbcTemplate jdbcTemplate;
    private List<String> datasetIds;

    /**
     * Constructs a new PgVectorEmbeddingStore with multiple dataset identifiers.
     *
     * @param datasetIds   A list of dataset identifiers.
     * @param jdbcTemplate The jdbcTemplate for database operations.
     */
    public SpringJdbcPgVectorEmbeddingStore(List<String> datasetIds, NamedParameterJdbcTemplate jdbcTemplate) {
        this.datasetIds = new ArrayList<>(datasetIds);
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        log.debug("PgVectorEmbeddingStore initialized with dataset identifiers: {}", datasetIds);
    }

    /**
     * Constructs a new PgVectorEmbeddingStore with a single dataset identifier.
     *
     * @param datasetId    A single dataset identifier.
     * @param jdbcTemplate The jdbcTemplate for database operations.
     */
    public SpringJdbcPgVectorEmbeddingStore(String datasetId, NamedParameterJdbcTemplate jdbcTemplate) {
        this(Collections.singletonList(datasetId), jdbcTemplate);
        log.info("PgVectorEmbeddingStore initialized with single dataset identifier: {}", datasetId);
    }

    @Override
    public String add(Embedding embedding) {
        log.debug("Adding a single embedding to the database");
        String id = UUID.randomUUID().toString();

        try {
            MapSqlParameterSource params = createSqlParameterSource(
                    id,
                    this.datasetIds.get(0),
                    embedding,
                    null);
            jdbcTemplate.update(SQL_INSERT_EMBEDDING, params);
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON", e);
            return null;
        }

        log.info("Added embedding with ID: {}", id);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        log.debug("Adding a single embedding with provided ID: {}", id);

        try {
            MapSqlParameterSource params = createSqlParameterSource(
                    id,
                    this.datasetIds.get(0),
                    embedding,
                    null);
            jdbcTemplate.update(SQL_INSERT_EMBEDDING, params);
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON", e);
        }

        log.info("Added embedding with ID: {}", id);
    }

    @Override
    public String add(Embedding embedding, TextSegment embedded) {
        log.debug("Adding an embedding with associated text segment to the database");
        String id = UUID.randomUUID().toString();

        try {
            MapSqlParameterSource params = createSqlParameterSource(
                    id,
                    this.datasetIds.get(0),
                    embedding,
                    embedded);
            jdbcTemplate.update(SQL_INSERT_EMBEDDING, params);
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON", e);
            return null;
        }

        log.info("Added embedding with ID: {}", id);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        log.debug("Adding a list of embeddings to the database");
        if (embeddings.isEmpty()) {
            log.info("No embeddings to add.");
            return Collections.emptyList();
        }

        if (!hasSingleDatasetIdentifier()) {
            log.info("Operation requires a single dataset identifier.");
            return Collections.emptyList();
        }

        List<TextSegment> textSegments = Collections.nCopies(embeddings.size(), null);
        return addAll(embeddings, textSegments);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        log.debug("Adding a list of embeddings and associated text segments to the database");
        if (embeddings.isEmpty() || textSegments.isEmpty()) {
            log.info("No embeddings or text segments to add.");
            return Collections.emptyList();
        }

        if (!hasSingleDatasetIdentifier()) {
            log.info("Operation requires a single dataset identifier.");
            return Collections.emptyList();
        }

        List<MapSqlParameterSource> paramsForBatchInsert = prepareBatchInsertParams(embeddings, textSegments);

        List<String> generatedIds = new ArrayList<>();

        for (MapSqlParameterSource params : paramsForBatchInsert) {
            generatedIds.add(params.getValue("id").toString());
        }

        jdbcTemplate.batchUpdate(SQL_INSERT_EMBEDDING, paramsForBatchInsert.toArray(new SqlParameterSource[0]));
        log.info("Batch insert of embeddings and text segments completed");
        return generatedIds;
    }

    /**
     * Finds embeddings that are most relevant (closest in space) to a provided
     * reference embedding.
     *
     * @param referenceEmbedding The embedding to use as a reference. The returned
     *                           embeddings should be relevant (closest) to this.
     * @param maxResults         The maximum number of embeddings to return.
     * @param minScore           The minimum relevance score, ranging from 0 to 1
     *                           (inclusive).
     *                           Only embeddings with a score of this value or
     *                           higher will be returned.
     * @return A list of EmbeddingMatch objects. Each EmbeddingMatch includes a
     *         relevance score (derived from cosine distance), ranging from 0 (not
     *         relevant) to 1 (highly relevant).
     */
    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults,
                                                          double minScore) {
        log.debug("Finding embeddings relevant to a given reference embedding");

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("embeddingVector", new PGvector(referenceEmbedding.vector()))
                .addValue("datasetIds", this.datasetIds)
                .addValue("minScore", minScore)
                .addValue("maxResults", maxResults);

        return jdbcTemplate.query(SQL_FIND_RELEVANT, parameters, new RowMapper<EmbeddingMatch<TextSegment>>() {
            @Override
            public EmbeddingMatch<TextSegment> mapRow(ResultSet rs, int rowNum) throws SQLException {
                double score = rs.getDouble("score");
                String embeddingId = rs.getString("id");
                PGobject embeddingVectorObj = (PGobject) rs.getObject("embedding_vector");
                PGvector vector = new PGvector(embeddingVectorObj.getValue());
                Embedding embedding = new Embedding(vector.toArray());
                String text = rs.getString("document_text");
                String metadataJson = Optional.ofNullable(rs.getString("metadata")).orElse("{}");
                Map<String, String> metadataMap;
                Metadata metadata;
                try {
                    metadataMap = objectMapper.readValue(metadataJson, new TypeReference<Map<String, String>>() {
                    });
                    metadata = new Metadata(metadataMap);
                } catch (JsonProcessingException e) {
                    log.error("Error processing JSON", e);
                    return null;
                }
                TextSegment textSegment = text == null || text.isEmpty() ? null : TextSegment.from(text, metadata);
                return new EmbeddingMatch<>(score, embeddingId, embedding, textSegment);
            }
        });
    }

    /**
     * Deletes embeddings from the database based on filename.
     *
     * @param filename The filename of the embeddings to be deleted.
     */
    public void deleteEmbeddingByFilename(String filename) {
        log.debug("Deleting embeddings from the database based on filename");
        if (!hasSingleDatasetIdentifier()) {
            log.info("Operation requires a single dataset identifier.");
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fileName", filename)
                .addValue("datasetId", this.datasetIds.get(0));

        jdbcTemplate.update(SQL_DELETE_EMBEDDING_BY_FILENAME, params);
        log.info("Embeddings with filename {} have been deleted", filename);
    }

    /**
     * Prepares the batch insert parameters for adding embeddings and text segments
     * to the database.
     *
     * @param embeddings   The list of embeddings to be added.
     * @param textSegments The list of text segments associated with each embedding.
     * @return A list of MapSqlParameterSource objects for batch insertion.
     */
    private List<MapSqlParameterSource> prepareBatchInsertParams(List<Embedding> embeddings,
                                                                 List<TextSegment> textSegments) {
        log.debug("Preparing batch insert parameters for embeddings and text segments");
        List<MapSqlParameterSource> paramsList = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); ++i) {
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = textSegments.get(i);
            try {
                MapSqlParameterSource params = createSqlParameterSource(
                        UUID.randomUUID().toString(),
                        this.datasetIds.get(0),
                        embedding,
                        textSegment);
                paramsList.add(params);
            } catch (JsonProcessingException e) {
                log.error("Error processing JSON", e);
            }
        }
        return paramsList;
    }

    /**
     * Creates and initializes a MapSqlParameterSource object with parameters for
     * SQL operations.
     *
     * @param id          The unique identifier for the embedding.
     * @param datasetId   The identifier of the dataset.
     * @param embedding   The embedding object to be stored in the database.
     * @param textSegment The text segment associated with the embedding.
     * @return A MapSqlParameterSource object populated with the required
     *         parameters.
     * @throws JsonProcessingException If an error occurs during JSON serialization.
     */
    private MapSqlParameterSource createSqlParameterSource(String id, String datasetId, Embedding embedding,
                                                           TextSegment textSegment) throws JsonProcessingException {
        log.debug("Creating SQL parameter source for embedding ID: {}", id);

        // Convert metadata to JSON string
        Map<String, String> metadataMap = new HashMap<>(textSegment.metadata().asMap());
        String metadataJson = this.objectMapper.writeValueAsString(metadataMap);

        // Create and populate parameters for SQL operation
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("datasetId", datasetId)
                .addValue("fileName", metadataMap.get("file_name"))
                .addValue("embeddingVector", new PGvector(embedding.vector()))
                .addValue("documentText", textSegment.text())
                .addValue("metadata", metadataJson)
                .addValue("versionCount", 1)
                .addValue("createdBy", "createdBy") // Consider using a dynamic value
                .addValue("creationTimestamp", LocalDateTime.now())
                .addValue("modifiedBy", "modifiedBy") // Consider using a dynamic value
                .addValue("modificationTimestamp", LocalDateTime.now());

        log.debug("SQL parameter source created for embedding ID: {}", id);
        return params;
    }

    private boolean hasSingleDatasetIdentifier() {
        return this.datasetIds.size() == 1;
    }
}
