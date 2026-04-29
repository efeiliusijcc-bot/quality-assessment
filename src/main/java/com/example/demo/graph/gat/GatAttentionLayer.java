package com.example.demo.graph.gat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GatAttentionLayer {

    private final int inputDim;
    private final int outputDim;
    private final int headCount;
    private final double[][][] headWeights;
    private final double[][] attentionVectors;

    public GatAttentionLayer(int inputDim, int outputDim, int headCount, long seed) {
        this.inputDim = inputDim;
        this.outputDim = outputDim;
        this.headCount = headCount;
        this.headWeights = new double[headCount][outputDim][inputDim];
        this.attentionVectors = new double[headCount][2 * outputDim];
        initializeParameters(seed);
    }

    public GatAttentionLayer(int inputDim, int outputDim, int headCount) {
        this(inputDim, outputDim, headCount, 42);
    }

    private void initializeParameters(long seed) {
        Random rng = new Random(seed);
        double scale = Math.sqrt(2.0 / (inputDim + outputDim));
        for (int h = 0; h < headCount; h++) {
            for (int i = 0; i < outputDim; i++) {
                for (int j = 0; j < inputDim; j++) {
                    headWeights[h][i][j] = rng.nextGaussian() * scale;
                }
            }
            for (int i = 0; i < 2 * outputDim; i++) {
                attentionVectors[h][i] = rng.nextGaussian() * scale;
            }
        }
    }

    public List<double[]> forward(double[][] nodeFeatures, int[][] adjacency) {
        int nodeCount = nodeFeatures.length;
        List<double[]> aggregatedEmbeddings = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            aggregatedEmbeddings.add(new double[headCount * outputDim]);
        }

        for (int h = 0; h < headCount; h++) {
            double[][] transformed = matMul(nodeFeatures, headWeights[h]);

            double[][] attentionScores = new double[nodeCount][nodeCount];
            for (int i = 0; i < nodeCount; i++) {
                for (int j = 0; j < nodeCount; j++) {
                    if (adjacency[i][j] == 0 && i != j) {
                        attentionScores[i][j] = Double.NEGATIVE_INFINITY;
                        continue;
                    }
                    attentionScores[i][j] = computeAttentionScore(transformed[i], transformed[j], attentionVectors[h]);
                }
            }

            double[][] attentionWeights = softmaxRows(attentionScores, adjacency);

            for (int i = 0; i < nodeCount; i++) {
                double[] newEmbedding = new double[outputDim];
                for (int j = 0; j < nodeCount; j++) {
                    double weight = attentionWeights[i][j];
                    if (weight == 0.0) {
                        continue;
                    }
                    for (int d = 0; d < outputDim; d++) {
                        newEmbedding[d] += weight * transformed[j][d];
                    }
                }
                for (int d = 0; d < outputDim; d++) {
                    newEmbedding[d] = leakyRelu(newEmbedding[d]);
                }
                System.arraycopy(newEmbedding, 0, aggregatedEmbeddings.get(i), h * outputDim, outputDim);
            }
        }

        return aggregatedEmbeddings;
    }

    public double[][] computeAttentionWeights(double[][] nodeFeatures, int[][] adjacency) {
        int nodeCount = nodeFeatures.length;
        double[][] combinedWeights = new double[nodeCount][nodeCount];

        for (int h = 0; h < headCount; h++) {
            double[][] transformed = matMul(nodeFeatures, headWeights[h]);

            double[][] attentionScores = new double[nodeCount][nodeCount];
            for (int i = 0; i < nodeCount; i++) {
                for (int j = 0; j < nodeCount; j++) {
                    if (adjacency[i][j] == 0 && i != j) {
                        attentionScores[i][j] = Double.NEGATIVE_INFINITY;
                        continue;
                    }
                    attentionScores[i][j] = computeAttentionScore(transformed[i], transformed[j], attentionVectors[h]);
                }
            }

            double[][] headWeights = softmaxRows(attentionScores, adjacency);
            for (int i = 0; i < nodeCount; i++) {
                for (int j = 0; j < nodeCount; j++) {
                    combinedWeights[i][j] += headWeights[i][j] / headCount;
                }
            }
        }

        return combinedWeights;
    }

    private double computeAttentionScore(double[] source, double[] target, double[] attentionVec) {
        double score = 0.0;
        for (int i = 0; i < source.length; i++) {
            score += attentionVec[i] * source[i];
        }
        for (int i = 0; i < target.length; i++) {
            score += attentionVec[source.length + i] * target[i];
        }
        return leakyRelu(score);
    }

    private double[][] matMul(double[][] matrix, double[][] weights) {
        int rows = matrix.length;
        int cols = weights.length;
        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double sum = 0.0;
                for (int k = 0; k < matrix[i].length; k++) {
                    sum += matrix[i][k] * weights[j][k];
                }
                result[i][j] = sum;
            }
        }
        return result;
    }

    private double[][] softmaxRows(double[][] scores, int[][] adjacency) {
        int n = scores.length;
        double[][] result = new double[n][n];
        for (int i = 0; i < n; i++) {
            double maxVal = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                if ((adjacency[i][j] > 0 || i == j) && scores[i][j] > maxVal) {
                    maxVal = scores[i][j];
                }
            }
            if (Double.isInfinite(maxVal)) {
                result[i][i] = 1.0;
                continue;
            }
            double sumExp = 0.0;
            for (int j = 0; j < n; j++) {
                if (adjacency[i][j] > 0 || i == j) {
                    result[i][j] = Math.exp(scores[i][j] - maxVal);
                    sumExp += result[i][j];
                }
            }
            if (sumExp == 0.0) {
                result[i][i] = 1.0;
                continue;
            }
            for (int j = 0; j < n; j++) {
                if (adjacency[i][j] > 0 || i == j) {
                    result[i][j] /= sumExp;
                }
            }
        }
        return result;
    }

    private double leakyRelu(double x) {
        return x > 0 ? x : 0.2 * x;
    }

    public int getInputDim() {
        return inputDim;
    }

    public int getOutputDim() {
        return outputDim;
    }

    public int getHeadCount() {
        return headCount;
    }
}
