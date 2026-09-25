package com.jarvis.rag

interface IRagIndexStore {
    fun indexDocument(metadata: DocumentMetadata, chunks: List<DocumentChunk>)
    fun deleteDocument(docId: String)
    fun getChunk(chunkId: String): DocumentChunk?
    fun getAllChunks(): List<DocumentChunk>
    fun getChunksForDocument(docId: String): List<DocumentChunk>
    fun getAllDocuments(): List<DocumentMetadata>
    fun getTermPostings(term: String): List<TermPosting>
    fun getStats(): RagStats
}
