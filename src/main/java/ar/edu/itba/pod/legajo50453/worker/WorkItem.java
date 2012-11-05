/**
 * 
 */
package ar.edu.itba.pod.legajo50453.worker;

import java.util.concurrent.Callable;

import ar.edu.itba.pod.api.Result.Item;
import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.util.Immutable;

/**
 * @author champo
 *
 */
@Immutable
public class WorkItem implements Callable<Item> {
	
	private final Signal reference;
	
	private final Signal candidate;
	
	public WorkItem(Signal reference, Signal candidate) {
		this.reference = reference;
		this.candidate = candidate;
	}

	@Override
	public Item call() throws Exception {
		return new Item(reference, reference.findDeviation(candidate));
	}

}
