-- Increase the private-content ceiling without changing existing migrations.
ALTER TABLE document_content DROP CONSTRAINT document_content_content_check;
ALTER TABLE document_content ADD CONSTRAINT document_content_content_check
    CHECK (octet_length(content) BETWEEN 1 AND 4194304);
