package com.example.demo.optimization.algorithm;

import com.example.demo.optimization.domain.OptimizationSolution;
import com.example.demo.optimization.domain.ProcessParameterSpace;
import java.util.random.RandomGenerator;

public class CrossoverAndMutation {

    private static final double SBX_DISTRIBUTION_INDEX = 20.0;
    private static final double MUTATION_DISTRIBUTION_INDEX = 20.0;

    public static OptimizationSolution[] sbxCrossover(
        OptimizationSolution parent1,
        OptimizationSolution parent2,
        RandomGenerator rng
    ) {
        OptimizationSolution child1 = parent1.copy();
        OptimizationSolution child2 = parent2.copy();

        for (ProcessParameterSpace space : ProcessParameterSpace.ALL) {
            double p1 = parent1.getParameter(space.name());
            double p2 = parent2.getParameter(space.name());

            if (rng.nextDouble() > 0.9) {
                child1.setParameter(space.name(), space.clip(p1));
                child2.setParameter(space.name(), space.clip(p2));
                continue;
            }

            if (Math.abs(p1 - p2) < 1e-10) {
                child1.setParameter(space.name(), space.clip(p1));
                child2.setParameter(space.name(), space.clip(p2));
                continue;
            }

            double u = rng.nextDouble();
            double beta;
            if (u <= 0.5) {
                beta = Math.pow(2.0 * u, 1.0 / (SBX_DISTRIBUTION_INDEX + 1.0));
            } else {
                beta = Math.pow(1.0 / (2.0 * (1.0 - u)), 1.0 / (SBX_DISTRIBUTION_INDEX + 1.0));
            }

            double c1 = 0.5 * ((1 + beta) * p1 + (1 - beta) * p2);
            double c2 = 0.5 * ((1 - beta) * p1 + (1 + beta) * p2);

            child1.setParameter(space.name(), space.clip(c1));
            child2.setParameter(space.name(), space.clip(c2));
        }

        return new OptimizationSolution[]{child1, child2};
    }

    public static void polynomialMutation(OptimizationSolution solution, double probability, RandomGenerator rng) {
        for (ProcessParameterSpace space : ProcessParameterSpace.ALL) {
            if (rng.nextDouble() > probability) {
                continue;
            }

            double value = solution.getParameter(space.name());
            double delta;
            double u = rng.nextDouble();

            if (u < 0.5) {
                delta = Math.pow(2.0 * u, 1.0 / (MUTATION_DISTRIBUTION_INDEX + 1.0)) - 1.0;
            } else {
                delta = 1.0 - Math.pow(2.0 * (1.0 - u), 1.0 / (MUTATION_DISTRIBUTION_INDEX + 1.0));
            }

            double range = space.upperBound() - space.lowerBound();
            double mutated = value + delta * range * 0.1;
            solution.setParameter(space.name(), space.clip(mutated));
        }
    }
}
