package sk.concentra.jcml.pipeline;

/**
 * Marker interface for pipeline actions that require the full input batch
 * in a single {@code process()} call. ConfigurablePipeline will pass all
 * items at once rather than calling once per item.
 *
 * Use this for: sorting, barrier/collect patterns, run-once side effects,
 * or any action whose logic depends on seeing all items together.
 */
public interface StreamAction extends PipelineAction {
}