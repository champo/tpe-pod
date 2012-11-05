/**
 * 
 */
package ar.edu.itba.pod.legajo50453.mt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.Result.Item;
import ar.edu.itba.pod.api.Signal;

/**
 * @author champo
 *
 */
public class Processor {
	
	private final SignalStore store;
	
	private final ExecutorService pool;
	
	public Processor(int threads, SignalStore store) {
		pool = Executors.newFixedThreadPool(threads);
		this.store = store;
	}
	
	public Result process(Signal signal) {
		
		final List<Future<Item>> localFutures = new ArrayList<>();
		for (final Signal reference : store.getPrimaries()) {
			final Future<Item> future = pool.submit(new WorkRequest(reference, signal));
			localFutures.add(future);
		}
		
		Result result = new Result(signal);
		for (final Future<Item> future : localFutures) {
			try {
				result = result.include(future.get());
			} catch (final InterruptedException e) {
				e.printStackTrace();
			} catch (final ExecutionException e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}

	public void stop() {
		//FIXME: Do sth
	}

}
