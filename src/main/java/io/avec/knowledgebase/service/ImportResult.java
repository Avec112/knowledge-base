package io.avec.knowledgebase.service;

import java.util.List;

/**
 * Outcome of a {@link KnowledgeBaseImportService#importZip(byte[])} call.
 *
 * @param importedCount     number of articles that were created
 * @param skippedSlugs      slugs that already existed and were left untouched
 * @param newCategoriesCount number of categories that were created to reproduce the zip's folder structure
 */
public record ImportResult(int importedCount, List<String> skippedSlugs, int newCategoriesCount) {

    public ImportResult {
        skippedSlugs = skippedSlugs == null ? List.of() : List.copyOf(skippedSlugs);
    }
}
