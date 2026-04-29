package com.example.demo.optimization.algorithm;

import java.util.ArrayList;
import java.util.List;

public class ReferencePointGenerator {

    public static List<double[]> generate(int objectives, int divisions) {
        List<double[]> points = new ArrayList<>();
        double[] point = new double[objectives];
        generateRecursive(points, point, objectives, divisions, 0, divisions);
        return points;
    }

    private static void generateRecursive(List<double[]> points, double[] point, int objectives, int divisions, int index, int remaining) {
        if (index == objectives - 1) {
            point[index] = (double) remaining / divisions;
            double[] copy = new double[objectives];
            System.arraycopy(point, 0, copy, 0, objectives);
            points.add(copy);
            return;
        }
        for (int i = 0; i <= remaining; i++) {
            point[index] = (double) i / divisions;
            generateRecursive(points, point, objectives, divisions, index + 1, remaining - i);
        }
    }

    public static double findClosestReference(double[] objectives, List<double[]> referencePoints) {
        double minDist = Double.MAX_VALUE;
        for (double[] ref : referencePoints) {
            double dist = perpendicularDistance(objectives, ref);
            if (dist < minDist) {
                minDist = dist;
            }
        }
        return minDist;
    }

    public static int findClosestReferenceIndex(double[] normalizedObjectives, List<double[]> referencePoints) {
        double minDist = Double.MAX_VALUE;
        int minIndex = 0;
        for (int i = 0; i < referencePoints.size(); i++) {
            double dist = perpendicularDistance(normalizedObjectives, referencePoints.get(i));
            if (dist < minDist) {
                minDist = dist;
                minIndex = i;
            }
        }
        return minIndex;
    }

    private static double perpendicularDistance(double[] point, double[] reference) {
        double refDot = 0.0;
        for (double v : reference) {
            refDot += v * v;
        }
        double dotProduct = 0.0;
        for (int i = 0; i < point.length; i++) {
            dotProduct += point[i] * reference[i];
        }
        double projScale = refDot > 1e-10 ? dotProduct / refDot : 0.0;
        double distance = 0.0;
        for (int i = 0; i < point.length; i++) {
            double diff = point[i] - projScale * reference[i];
            distance += diff * diff;
        }
        return Math.sqrt(distance);
    }
}
