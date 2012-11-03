/**
 * 
 */
package ar.edu.itba.pod.legajo50453.mt;

import java.util.concurrent.Callable;

import ar.edu.itba.pod.api.Result.Item;
import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.util.Immutable;

/**
 * @author champo
 *
 */
@Immutable
public class WorkRequest implements Callable<Item> {
	
	private final Signal reference;
	
	private final Signal candidate;
	
	public WorkRequest(Signal reference, Signal candidate) {
		this.reference = reference;
		this.candidate = candidate;
	}

	@Override
	public Item call() throws Exception {
		return new Item(reference, reference.findDeviation(candidate));
	}

}
