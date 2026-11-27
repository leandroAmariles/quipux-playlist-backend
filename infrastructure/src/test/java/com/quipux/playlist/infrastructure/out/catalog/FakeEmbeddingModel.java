package com.quipux.playlist.infrastructure.out.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Embeddings without a model: the same text always gives the same vector. It keeps every text it was asked for.
 */
final class FakeEmbeddingModel implements EmbeddingModel {

    static final int DIMENSIONS = 768;

    private final List<String> requested = new CopyOnWriteArrayList<>();
    private final Function<String, float[]> vectors;

    FakeEmbeddingModel() {
        this(FakeEmbeddingModel::randomVectorOf);
    }

    FakeEmbeddingModel(Function<String, float[]> vectors) {
        this.vectors = vectors;
    }

    List<String> requested() {
        return requested;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        for (String text : request.getInstructions()) {
            requested.add(text);
            embeddings.add(new Embedding(vectors.apply(text), embeddings.size()));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getFormattedContent(MetadataMode.NONE));
    }

    @Override
    public String getEmbeddingContent(Document document) {
        return document.getFormattedContent(MetadataMode.NONE);
    }

    // Only positive values: every pair is closer than a right angle, as the vectors of a real model are.
    private static float[] randomVectorOf(String text) {
        Random random = new Random(text.hashCode());
        float[] vector = new float[DIMENSIONS];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = random.nextFloat();
        }
        return vector;
    }
}
