package io.kaoto.forage.config.editor;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Provides the current content of properties files. An IDE passes the content of open documents, including unsaved
 * changes, so that the edits computed for them apply to what the user sees.
 */
@FunctionalInterface
public interface DocumentSource {

    /** Reads the files from disk. */
    DocumentSource DISK = file -> file.isFile() ? Optional.of(PropertiesDocument.read(file)) : Optional.empty();

    /**
     * @param file the file
     * @return the current content of the file, or empty if the file does not exist
     * @throws IOException if the file cannot be read
     */
    Optional<PropertiesDocument> load(File file) throws IOException;
}
