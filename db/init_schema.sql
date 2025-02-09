-- USED Postgres DB running locally --

-- drop table dataset_embedding
create table dataset_embedding (
    id VARCHAR(36) PRIMARY KEY,
    dataset_id VARCHAR(36) not null,
    file_name VARCHAR(60) not null,
    embedding_vector VECTOR(384), --1536
    document_text TEXT,
    metadata JSONB,
    version_count INTEGER,
    created_by VARCHAR(60),
    creation_timestamp TIMESTAMP WITH TIME ZONE default CURRENT_TIMESTAMP,
    modified_by VARCHAR(60),
    modification_timestamp TIMESTAMP WITH TIME ZONE default CURRENT_TIMESTAMP
);