package com.example.demo.optimization.algorithm;

import com.example.demo.optimization.domain.OptimizationSolution;
import java.util.ArrayList;
import java.util.List;

public class NonDominatedSorter {

    public static List<List<OptimizationSolution>> sort(List<OptimizationSolution> population) {
        int n = population.size();
        int[] dominationCount = new int[n];
        List<List<Integer>> dominatedBy = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            dominatedBy.add(new ArrayList<>());
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (population.get(i).dominates(population.get(j))) {
                    dominationCount[j]++;
                    dominatedBy.get(i).add(j);
                } else if (population.get(j).dominates(population.get(i))) {
                    dominationCount[i]++;
                    dominatedBy.get(j).add(i);
                }
            }
        }

        List<List<OptimizationSolution>> fronts = new ArrayList<>();
        List<Integer> currentFront = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (dominationCount[i] == 0) {
                currentFront.add(i);
                population.get(i).setRank(fronts.size());
            }
        }

        while (!currentFront.isEmpty()) {
            List<OptimizationSolution> front = new ArrayList<>();
            for (int idx : currentFront) {
                front.add(population.get(idx));
            }
            fronts.add(front);

            List<Integer> nextFront = new ArrayList<>();
            int nextRank = fronts.size();
            for (int idx : currentFront) {
                for (int dominated : dominatedBy.get(idx)) {
                    dominationCount[dominated]--;
                    if (dominationCount[dominated] == 0) {
                        nextFront.add(dominated);
                        population.get(dominated).setRank(nextRank);
                    }
                }
            }
            currentFront = nextFront;
        }

        return fronts;
    }

    public static void computeCrowdingDistance(List<OptimizationSolution> front) {
        if (front.size() <= 2) {
            for (OptimizationSolution solution : front) {
                solution.setCrowdingDistance(Double.MAX_VALUE);
            }
            return;
        }

        int objectiveCount = front.get(0).getObjectiveCount();
        for (OptimizationSolution solution : front) {
            solution.setCrowdingDistance(0.0);
        }

        for (int m = 0; m < objectiveCount; m++) {
            final int objIndex = m;
            front.sort((a, b) -> Double.compare(a.getObjective(objIndex), b.getObjective(objIndex)));

            double minVal = front.get(0).getObjective(m);
            double maxVal = front.get(front.size() - 1).getObjective(m);
            double range = maxVal - minVal;
            if (range < 1e-10) {
                continue;
            }

            front.get(0).setCrowdingDistance(Double.MAX_VALUE);
            front.get(front.size() - 1).setCrowdingDistance(Double.MAX_VALUE);

            for (int i = 1; i < front.size() - 1; i++) {
                double prev = front.get(i - 1).getObjective(m);
                double next = front.get(i + 1).getObjective(m);
                double distance = front.get(i).getCrowdingDistance() + (next - prev) / range;
                front.get(i).setCrowdingDistance(distance);
            }
        }
    }
}
