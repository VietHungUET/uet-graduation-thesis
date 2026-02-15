package org.tzi.kodkod.decomp;

/**
 * Configuration for decomposed solving strategy.
 * Contains parameters for partitioning relations and parallel execution.
 * 
 * Based on Pardinus decomposed options but implemented independently.
 * 
 * @author Custom implementation for thesis
 */
public class DecomposedSolverConfig {

    /** Whether decomposed solving is enabled */
    private boolean enabled;

    /**
     * Threshold for relation partitioning.
     * Relations with outdegree <= threshold go to partial problem (P1).
     * Default: 2 (as per Pardinus paper)
     */
    private int threshold;

    /**
     * Number of threads for parallel execution.
     * Default: 4
     */
    private int threads;

    /**
     * Decomposition mode.
     */
    public enum DMode {
        /** Run integrated problems in parallel */
        PARALLEL,
        /** Also run full problem (amalgamated) in parallel */
        HYBRID
    }

    private DMode mode;

    /**
     * Creates default configuration.
     * enabled=true, threshold=2, threads=4, mode=HYBRID
     */
    public DecomposedSolverConfig() {
        this.enabled = true;
        this.threshold = 0;
        this.threads = 4;
        this.mode = DMode.HYBRID;
    }

    /**
     * Creates configuration with specified parameters.
     */
    public DecomposedSolverConfig(boolean enabled, int threshold, int threads, DMode mode) {
        this.enabled = enabled;
        this.threshold = threshold;
        this.threads = threads;
        this.mode = mode;
    }

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Threshold must be >= 0");
        }
        this.threshold = threshold;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("Threads must be >= 1");
        }
        this.threads = threads;
    }

    public DMode getMode() {
        return mode;
    }

    public void setMode(DMode mode) {
        this.mode = mode;
    }

    @Override
    public String toString() {
        return String.format("DecomposedSolverConfig[enabled=%s, threshold=%d, threads=%d, mode=%s]",
                enabled, threshold, threads, mode);
    }
}
