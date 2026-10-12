-- Existing filesystem documents stay readable; new database uploads are atomic.
CREATE TABLE document_content (
    document_id uuid PRIMARY KEY REFERENCES document(id) ON UPDATE CASCADE ON DELETE CASCADE,
    content bytea NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 3145728)
);
-- Serializes quota checks across concurrent uploads from different drivers.
CREATE TABLE document_storage_lock (id integer PRIMARY KEY CHECK (id = 1));
INSERT INTO document_storage_lock VALUES (1);
