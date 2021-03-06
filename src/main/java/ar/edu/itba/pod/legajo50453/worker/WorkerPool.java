/**
 * 
 */
package ar.edu.itba.pod.legajo50453.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.Result.Item;
import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.legajo50453.mt.SignalStore;

/**
 * @author champo
 *
 */
public final class WorkerPool {
	
	final static Logger logger = LoggerFactory.getLogger(WorkerPool.class);
	
	public static interface Ready {
		public void result(Result result);
	}

	private final SignalStore store;
	
	private final ExecutorService pool;

	private final Thread thread;

	private final BlockingQueue<WorkRequest> queue;
	
	public WorkerPool(int threads, SignalStore store) {
		pool = Executors.newFixedThreadPool(threads);
		this.store = store;
		
		queue = new LinkedBlockingDeque<>();
		thread = new Thread(new QueueConsumer());
		thread.start();
	}
	
	public Result process(Signal signal) {
		
		logger.debug("Comparing signal", signal);
		final List<Future<Item>> localFutures = new ArrayList<>();
		for (final Signal reference : store.getPrimaries()) {
			final Future<Item> future = pool.submit(new WorkItem(reference, signal));
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
	
	public void request(Signal signal, Ready ready) {
		queue.add(new WorkRequest(signal, ready));
	}


	public void reset() {
		queue.clear();
	}
	
	private static final class WorkRequest {

		Signal signal;
		
		Ready callback;
		
		public WorkRequest(Signal signal, Ready ready) {
			this.signal = signal;
			this.callback = ready;
		}
		
	}

	private final class QueueConsumer implements Runnable {
		
		@Override
		public void run() {
			
			try {
				WorkRequest item;
				while ((item = queue.take()) != null) {
					final Result result = process(item.signal);
					
					try { 
						item.callback.result(result);
					} catch (final RuntimeException e) {
						logger.error("Got exception on callback", e);
					}
				}
				
			} catch (final InterruptedException e) {
				logger.debug("Interrupted queue consumer", e);
			}
			
		}
	}

}
