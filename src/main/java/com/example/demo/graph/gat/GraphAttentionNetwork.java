package com.example.demo.graph.gat;

import java.util.List;
import java.util.Random;

public class GraphAttentionNetwork {

    private final List<GatAttentionLayer> layers;
    private final int embeddingDim;

    public GraphAttentionNetwork(List<GatAttentionLayer> layers) {
        this.layers = layers;
        GatAttentionLayer last = layers.get(layers.size() - 1);
        this.embeddingDim = last.getHeadCount() * last.getOutputDim();
    }

    public static GraphAttentionNetwork create(int featureDim, int hiddenDim, int embeddingDim, int heads, long seed) {
        Random rng = new Random(seed);
        GatAttentionLayer layer1 = new GatAttentionLayer(featureDim, hiddenDim, heads, rng.nextLong());
        GatAttentionLayer layer2 = new GatAttentionLayer(hiddenDim * heads, embeddingDim, Math.max(1, heads / 2), rng.nextLong());
        return new GraphAttentionNetwork(List.of(layer1, layer2));
    }

    public static GraphAttentionNetwork createDefault(int featureDim) {
        return create(featureDim, 8, 4, 4, 42);
    }

    public double[][] forward(double[][] nodeFeatures, int[][] adjacency) {
        double[][] currentFeatures = nodeFeatures;

        for (int l = 0; l < layers.size(); l++) {
            GatAttentionLayer layer = layers.get(l);
            List<double[]> embeddings = layer.forward(currentFeatures, adjacency);
            currentFeatures = embeddings.toArray(new double[0][]);
            if (l < layers.size() - 1) {
                currentFeatures = applyRelu(currentFeatures);
            }
        }

        return currentFeatures;
    }

    public double[][] computeAttentionWeights(double[][] nodeFeatures, int[][] adjacency) {
        return layers.get(0).computeAttentionWeights(nodeFeatures, adjacency);
    }

    private double[][] applyRelu(double[][] features) {
        double[][] result = new double[features.length][];
        for (int i = 0; i < features.length; i++) {
            result[i] = new double[features[i].length];
            for (int j = 0; j < features[i].length; j++) {
                result[i][j] = Math.max(0, features[i][j]);
            }
        }
        return result;
    }

    public int getEmbeddingDim() {
        return embeddingDim;
    }

    public List<GatAttentionLayer> getLayers() {
        return layers;
    }
}
