package com.example.demo.optimization.algorithm;

import com.example.demo.optimization.domain.OptimizationSolution;
import com.example.demo.optimization.domain.ProcessParameterSpace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

public class Mansga3Optimizer {

    private final int populationSize;
    private final int maxGenerations;
    private final Random rng;
    private final List<double[]> referencePoints;
    private final int objectiveCount;

    public Mansga3Optimizer(int populationSize, int maxGenerations, int objectiveCount, long seed) {
        this.populationSize = populationSize;
        this.maxGenerations = maxGenerations;
        this.objectiveCount = objectiveCount;
        this.rng = new Random(seed);
        int divisions = estimateDivisions(objectiveCount, populationSize);
        this.referencePoints = ReferencePointGenerator.generate(objectiveCount, divisions);
    }

    public List<OptimizationSolution> optimize(Function<OptimizationSolution, Map<String, Double>> evaluator) {
        List<OptimizationSolution> population = initializePopulation();
        population.forEach(solution -> solution.setObjectives(evaluator.apply(solution)));

        for (int gen = 0; gen < maxGenerations; gen++) {
            List<OptimizationSolution> offspring = generateOffspring(population, evaluator);
            List<OptimizationSolution> combined = new ArrayList<>(population.size() + offspring.size());
            combined.addAll(population);
            combined.addAll(offspring);

            population = environmentalSelection(combined);
        }

        return extractParetoFront(population);
    }

    private List<OptimizationSolution> initializePopulation() {
        List<OptimizationSolution> population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            Map<String, Double> params = new LinkedHashMap<>();
            for (ProcessParameterSpace space : ProcessParameterSpace.ALL) {
                params.put(space.name(), space.random(rng));
            }
            population.add(new OptimizationSolution(params));
        }
        return population;
    }

    private List<OptimizationSolution> generateOffspring(List<OptimizationSolution> population, Function<OptimizationSolution, Map<String, Double>> evaluator) {
        List<OptimizationSolution> offspring = new ArrayList<>(populationSize);
        while (offspring.size() < populationSize) {
            OptimizationSolution parent1 = tournamentSelect(population);
            OptimizationSolution parent2 = tournamentSelect(population);

            OptimizationSolution[] children = CrossoverAndMutation.sbxCrossover(parent1, parent2, rng);
            for (OptimizationSolution child : children) {
                CrossoverAndMutation.polynomialMutation(child, 1.0 / ProcessParameterSpace.ALL.length, rng);
                child.setObjectives(evaluator.apply(child));
                offspring.add(child);
                if (offspring.size() >= populationSize) {
                    break;
                }
            }
        }
        return offspring;
    }

    private OptimizationSolution tournamentSelect(List<OptimizationSolution> population) {
        int i = rng.nextInt(population.size());
        int j = rng.nextInt(population.size());
        OptimizationSolution a = population.get(i);
        OptimizationSolution b = population.get(j);
        if (a.getRank() < b.getRank()) {
            return a;
        }
        if (b.getRank() < a.getRank()) {
            return b;
        }
        return a.getCrowdingDistance() >= b.getCrowdingDistance() ? a : b;
    }

    private List<OptimizationSolution> environmentalSelection(List<OptimizationSolution> combined) {
        List<List<OptimizationSolution>> fronts = NonDominatedSorter.sort(combined);
        for (List<OptimizationSolution> front : fronts) {
            NonDominatedSorter.computeCrowdingDistance(front);
        }

        List<OptimizationSolution> selected = new ArrayList<>(populationSize);
        int frontIndex = 0;
        while (frontIndex < fronts.size() && selected.size() + fronts.get(frontIndex).size() <= populationSize) {
            selected.addAll(fronts.get(frontIndex));
            frontIndex++;
        }

        if (selected.size() < populationSize && frontIndex < fronts.size()) {
            List<OptimizationSolution> lastFront = fronts.get(frontIndex);
            int remaining = populationSize - selected.size();
            List<OptimizationSolution> niched = nichingSelection(lastFront, remaining);
            selected.addAll(niched);
        }

        return selected;
    }

    private List<OptimizationSolution> nichingSelection(List<OptimizationSolution> front, int count) {
        double[] idealPoint = findIdealPoint(front);
        double[] nadirPoint = findNadirPoint(front);

        double[][] normalized = normalizeObjectives(front, idealPoint, nadirPoint);

        double[] nicheCounts = new double[referencePoints.size()];
        int[] association = new int[front.size()];
        for (int i = 0; i < front.size(); i++) {
            association[i] = ReferencePointGenerator.findClosestReferenceIndex(normalized[i], referencePoints);
        }

        List<OptimizationSolution> selected = new ArrayList<>(count);
        boolean[] taken = new boolean[front.size()];

        for (int round = 0; round < count; round++) {
            double minNiche = Double.MAX_VALUE;
            for (double nc : nicheCounts) {
                if (nc < minNiche) {
                    minNiche = nc;
                }
            }

            List<Integer> minRefIndices = new ArrayList<>();
            for (int r = 0; r < nicheCounts.length; r++) {
                if (Math.abs(nicheCounts[r] - minNiche) < 1e-10) {
                    minRefIndices.add(r);
                }
            }

            boolean found = false;
            for (int refIdx : minRefIndices) {
                for (int i = 0; i < front.size(); i++) {
                    if (!taken[i] && association[i] == refIdx) {
                        selected.add(front.get(i));
                        taken[i] = true;
                        nicheCounts[refIdx]++;
                        found = true;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }

            if (!found) {
                for (int i = 0; i < front.size(); i++) {
                    if (!taken[i]) {
                        selected.add(front.get(i));
                        taken[i] = true;
                        break;
                    }
                }
            }
        }

        return selected;
    }

    private double[] findIdealPoint(List<OptimizationSolution> front) {
        double[] ideal = new double[objectiveCount];
        for (int m = 0; m < objectiveCount; m++) {
            ideal[m] = Double.MAX_VALUE;
            for (OptimizationSolution solution : front) {
                ideal[m] = Math.min(ideal[m], solution.getObjective(m));
            }
        }
        return ideal;
    }

    private double[] findNadirPoint(List<OptimizationSolution> front) {
        double[] nadir = new double[objectiveCount];
        for (int m = 0; m < objectiveCount; m++) {
            nadir[m] = Double.NEGATIVE_INFINITY;
            for (OptimizationSolution solution : front) {
                nadir[m] = Math.max(nadir[m], solution.getObjective(m));
            }
        }
        return nadir;
    }

    private double[][] normalizeObjectives(List<OptimizationSolution> front, double[] ideal, double[] nadir) {
        double[][] normalized = new double[front.size()][objectiveCount];
        for (int i = 0; i < front.size(); i++) {
            for (int m = 0; m < objectiveCount; m++) {
                double range = nadir[m] - ideal[m];
                normalized[i][m] = range > 1e-10 ? (front.get(i).getObjective(m) - ideal[m]) / range : 0.0;
            }
        }
        return normalized;
    }

    private List<OptimizationSolution> extractParetoFront(List<OptimizationSolution> population) {
        List<List<OptimizationSolution>> fronts = NonDominatedSorter.sort(population);
        return fronts.isEmpty() ? population : fronts.get(0);
    }

    private int estimateDivisions(int objectives, int popSize) {
        if (objectives <= 3) {
            return 12;
        }
        if (objectives <= 5) {
            return 6;
        }
        return 3;
    }
}
