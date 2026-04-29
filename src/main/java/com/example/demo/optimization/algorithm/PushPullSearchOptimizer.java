package com.example.demo.optimization.algorithm;

import com.example.demo.optimization.domain.OptimizationSolution;
import com.example.demo.optimization.domain.ProcessParameterSpace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

public class PushPullSearchOptimizer {

    private final int populationSize;
    private final int maxGenerations;
    private final int objectiveCount;
    private final Random rng;
    private final double pushRatio;

    public PushPullSearchOptimizer(int populationSize, int maxGenerations, int objectiveCount, long seed) {
        this.populationSize = populationSize;
        this.maxGenerations = maxGenerations;
        this.objectiveCount = objectiveCount;
        this.rng = new Random(seed);
        this.pushRatio = 0.4;
    }

    public List<OptimizationSolution> optimize(Function<OptimizationSolution, Map<String, Double>> evaluator) {
        List<OptimizationSolution> population = initializePopulation();
        population.forEach(solution -> solution.setObjectives(evaluator.apply(solution)));

        int pushGenerations = (int) (maxGenerations * pushRatio);

        for (int gen = 0; gen < maxGenerations; gen++) {
            List<OptimizationSolution> offspring = generateOffspring(population, evaluator, gen < pushGenerations);

            List<OptimizationSolution> combined = new ArrayList<>(population.size() + offspring.size());
            combined.addAll(population);
            combined.addAll(offspring);

            if (gen < pushGenerations) {
                population = pushSelection(combined);
            } else {
                population = pullSelection(combined);
            }
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

    private List<OptimizationSolution> generateOffspring(List<OptimizationSolution> population, Function<OptimizationSolution, Map<String, Double>> evaluator, boolean isPushPhase) {
        List<OptimizationSolution> offspring = new ArrayList<>(populationSize);

        while (offspring.size() < populationSize) {
            OptimizationSolution parent1 = tournamentSelect(population);
            OptimizationSolution parent2 = tournamentSelect(population);

            OptimizationSolution[] children = CrossoverAndMutation.sbxCrossover(parent1, parent2, rng);
            for (OptimizationSolution child : children) {
                double mutationRate = isPushPhase
                    ? 2.0 / ProcessParameterSpace.ALL.length
                    : 1.0 / ProcessParameterSpace.ALL.length;
                CrossoverAndMutation.polynomialMutation(child, mutationRate, rng);

                if (isPushPhase) {
                    pushRepair(child);
                }

                child.setObjectives(evaluator.apply(child));
                offspring.add(child);
                if (offspring.size() >= populationSize) {
                    break;
                }
            }
        }
        return offspring;
    }

    private void pushRepair(OptimizationSolution solution) {
        Map<String, Double> targets = ProcessParameterSpace.targetValues();
        double pushStrength = 0.15;
        for (ProcessParameterSpace space : ProcessParameterSpace.ALL) {
            double current = solution.getParameter(space.name());
            double target = targets.get(space.name());
            double pushed = current + pushStrength * (target - current) * rng.nextDouble();
            solution.setParameter(space.name(), space.clip(pushed));
        }
    }

    private List<OptimizationSolution> pushSelection(List<OptimizationSolution> combined) {
        List<List<OptimizationSolution>> fronts = NonDominatedSorter.sort(combined);
        for (List<OptimizationSolution> front : fronts) {
            NonDominatedSorter.computeCrowdingDistance(front);
        }

        List<OptimizationSolution> selected = new ArrayList<>(populationSize);
        for (List<OptimizationSolution> front : fronts) {
            if (selected.size() + front.size() <= populationSize) {
                selected.addAll(front);
            } else {
                int remaining = populationSize - selected.size();
                front.sort((a, b) -> Double.compare(b.getCrowdingDistance(), a.getCrowdingDistance()));
                selected.addAll(front.subList(0, remaining));
                break;
            }
        }
        return selected;
    }

    private List<OptimizationSolution> pullSelection(List<OptimizationSolution> combined) {
        Mansga3Optimizer mansga = new Mansga3Optimizer(populationSize, 0, objectiveCount, rng.nextLong());
        List<double[]> referencePoints = ReferencePointGenerator.generate(objectiveCount, estimateDivisions());

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

            double[] idealPoint = findIdealPoint(combined);
            double[] nadirPoint = findNadirPoint(combined);

            int[] association = new int[lastFront.size()];
            for (int i = 0; i < lastFront.size(); i++) {
                double[] normalized = normalizeObjectives(lastFront.get(i), idealPoint, nadirPoint);
                association[i] = ReferencePointGenerator.findClosestReferenceIndex(normalized, referencePoints);
            }

            boolean[] taken = new boolean[lastFront.size()];
            for (int i = 0; i < remaining; i++) {
                double minDist = Double.MAX_VALUE;
                int bestIdx = -1;
                for (int j = 0; j < lastFront.size(); j++) {
                    if (taken[j]) {
                        continue;
                    }
                    double[] normalized = normalizeObjectives(lastFront.get(j), idealPoint, nadirPoint);
                    double dist = ReferencePointGenerator.findClosestReference(normalized, referencePoints);
                    if (dist < minDist) {
                        minDist = dist;
                        bestIdx = j;
                    }
                }
                if (bestIdx >= 0) {
                    selected.add(lastFront.get(bestIdx));
                    taken[bestIdx] = true;
                }
            }
        }

        return selected;
    }

    private double[] findIdealPoint(List<OptimizationSolution> solutions) {
        double[] ideal = new double[objectiveCount];
        for (int m = 0; m < objectiveCount; m++) {
            final int objIndex = m;
            ideal[m] = solutions.stream().mapToDouble(s -> s.getObjective(objIndex)).min().orElse(0.0);
        }
        return ideal;
    }

    private double[] findNadirPoint(List<OptimizationSolution> solutions) {
        double[] nadir = new double[objectiveCount];
        for (int m = 0; m < objectiveCount; m++) {
            final int objIndex = m;
            nadir[m] = solutions.stream().mapToDouble(s -> s.getObjective(objIndex)).max().orElse(1.0);
        }
        return nadir;
    }

    private double[] normalizeObjectives(OptimizationSolution solution, double[] ideal, double[] nadir) {
        double[] normalized = new double[objectiveCount];
        for (int m = 0; m < objectiveCount; m++) {
            double range = nadir[m] - ideal[m];
            normalized[m] = range > 1e-10 ? (solution.getObjective(m) - ideal[m]) / range : 0.0;
        }
        return normalized;
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

    private List<OptimizationSolution> extractParetoFront(List<OptimizationSolution> population) {
        List<List<OptimizationSolution>> fronts = NonDominatedSorter.sort(population);
        return fronts.isEmpty() ? population : fronts.get(0);
    }

    private int estimateDivisions() {
        if (objectiveCount <= 3) {
            return 12;
        }
        if (objectiveCount <= 5) {
            return 6;
        }
        return 3;
    }
}
