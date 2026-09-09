import type { MetaWordEntryPayload } from "@/types/api";

export const MAX_ENTRY_BATCH_SIZE = 1000;

export function chunkEntries(entries: MetaWordEntryPayload[], batchSize = MAX_ENTRY_BATCH_SIZE) {
    if (!Number.isInteger(batchSize) || batchSize < 1) {
        throw new Error("batchSize must be a positive integer");
    }

    const batches: MetaWordEntryPayload[][] = [];
    for (let index = 0; index < entries.length; index += batchSize) {
        batches.push(entries.slice(index, index + batchSize));
    }
    return batches;
}
