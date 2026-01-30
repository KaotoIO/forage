package io.kaoto.forage.agent;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import java.util.ArrayList;
import java.util.List;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;

/**
 * Extended agent configuration that supports guardrail instances in addition to classes.
 *
 * <p>This class extends the standard Camel {@link AgentConfiguration} to provide support
 * for pre-configured guardrail instances. When guardrail instances are provided, they
 * take precedence over guardrail classes, allowing for properly configured guardrails
 * loaded via ServiceLoader.
 */
public class ForageAgentConfiguration extends AgentConfiguration {

    private List<InputGuardrail> inputGuardrails;
    private List<OutputGuardrail> outputGuardrails;

    public ForageAgentConfiguration() {
        super();
    }

    /**
     * Gets the list of input guardrail instances.
     *
     * @return the list of input guardrail instances, or null if not set
     */
    public List<InputGuardrail> getInputGuardrails() {
        return inputGuardrails;
    }

    /**
     * Sets the input guardrail instances.
     *
     * @param inputGuardrails the input guardrail instances
     * @return this configuration for method chaining
     */
    public ForageAgentConfiguration withInputGuardrails(List<InputGuardrail> inputGuardrails) {
        this.inputGuardrails = inputGuardrails;
        return this;
    }

    /**
     * Adds an input guardrail instance.
     *
     * @param inputGuardrail the input guardrail instance to add
     * @return this configuration for method chaining
     */
    public ForageAgentConfiguration withInputGuardrail(InputGuardrail inputGuardrail) {
        if (this.inputGuardrails == null) {
            this.inputGuardrails = new ArrayList<>();
        }
        this.inputGuardrails.add(inputGuardrail);
        return this;
    }

    /**
     * Gets the list of output guardrail instances.
     *
     * @return the list of output guardrail instances, or null if not set
     */
    public List<OutputGuardrail> getOutputGuardrails() {
        return outputGuardrails;
    }

    /**
     * Sets the output guardrail instances.
     *
     * @param outputGuardrails the output guardrail instances
     * @return this configuration for method chaining
     */
    public ForageAgentConfiguration withOutputGuardrails(List<OutputGuardrail> outputGuardrails) {
        this.outputGuardrails = outputGuardrails;
        return this;
    }

    /**
     * Adds an output guardrail instance.
     *
     * @param outputGuardrail the output guardrail instance to add
     * @return this configuration for method chaining
     */
    public ForageAgentConfiguration withOutputGuardrail(OutputGuardrail outputGuardrail) {
        if (this.outputGuardrails == null) {
            this.outputGuardrails = new ArrayList<>();
        }
        this.outputGuardrails.add(outputGuardrail);
        return this;
    }

    /**
     * Checks if input guardrail instances are available.
     *
     * @return true if input guardrail instances are configured
     */
    public boolean hasInputGuardrails() {
        return inputGuardrails != null && !inputGuardrails.isEmpty();
    }

    /**
     * Checks if output guardrail instances are available.
     *
     * @return true if output guardrail instances are configured
     */
    public boolean hasOutputGuardrails() {
        return outputGuardrails != null && !outputGuardrails.isEmpty();
    }
}
