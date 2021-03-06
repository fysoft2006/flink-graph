package flink.graphs.library;

import flink.graphs.*;
import flink.graphs.spargel.MessageIterator;
import flink.graphs.spargel.MessagingFunction;
import flink.graphs.spargel.VertexUpdateFunction;

import org.apache.flink.types.NullValue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * An implementation of the label propagation algorithm.
 * The iterative algorithm detects communities by propagating labels.
 * In each iteration, a vertex adopts the label that is most frequent among its neighbors' labels.
 * Labels are represented by Longs and we assume a total ordering among them, in order to break ties.
 * The algorithm converges when no vertex changes its value or the maximum number of iterations have been reached.
 * Note that different initializations might lead to different results.
 *
 */
@SuppressWarnings("serial")
public class LabelPropagation<K extends Comparable<K> & Serializable> implements GraphAlgorithm<K, Long, NullValue> {

    private final int maxIterations;

    public LabelPropagation(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    @Override
    public Graph<K, Long, NullValue> run(Graph<K, Long, NullValue> input) {

    	// iteratively adopt the most frequent label among the neighbors
    	// of each vertex
    	return input.runVertexCentricIteration(
                new UpdateVertexLabel<K>(),
                new SendNewLabelToNeighbors<K>(),
                maxIterations
        );
    }

    /**
     * Function that updates the value of a vertex by adopting the most frequent label
     * among its in-neighbors
     */
    public static final class UpdateVertexLabel<K extends Comparable<K> & Serializable>
            extends VertexUpdateFunction<K, Long, Long> {

		public void updateVertex(K vertexKey, Long vertexValue, MessageIterator<Long> inMessages) {
			Map<Long, Long> labelsWithFrequencies = new HashMap<Long, Long>();

			long maxFrequency = 1;
			long mostFrequentLabel = vertexValue;

			// store the labels with their frequencies
			for (Long msg : inMessages) {
				if (labelsWithFrequencies.containsKey(msg)) {
					long currentFreq = labelsWithFrequencies.get(msg);
					labelsWithFrequencies.put(msg, currentFreq + 1);
				}
				else {
					labelsWithFrequencies.put(msg, 1L);
				}
			}
			// select the most frequent label: if two or more labels have the same frequency,
			// the node adopts the label with the highest value
			for (Entry<Long, Long> entry : labelsWithFrequencies.entrySet()) {
				if (entry.getValue() == maxFrequency) {
					// check the label value to break ties
					if (entry.getKey() > mostFrequentLabel) {
						mostFrequentLabel = entry.getKey();
					}
				}
				else if (entry.getValue() > maxFrequency) {
					maxFrequency = entry.getValue();
					mostFrequentLabel = entry.getKey();
				}
			}

			// set the new vertex value
			setNewVertexValue(mostFrequentLabel);
		}
    }

    /**
     * Sends the vertex label to all out-neighbors
     */
    public static final class SendNewLabelToNeighbors<K extends Comparable<K> & Serializable>
            extends MessagingFunction<K, Long, Long, NullValue> {

    	public void sendMessages(K vertexKey, Long newLabel) {
            sendMessageToAllNeighbors(newLabel);
        }
    }
}