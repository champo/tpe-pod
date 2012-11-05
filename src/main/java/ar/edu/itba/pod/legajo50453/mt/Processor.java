/**
 * 
 */
package ar.edu.itba.pod.legajo50453.mt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.Result.Item;
import ar.edu.itba.pod.api.Signal;

/**
 * @author champo
 *
 */
public class Processor {
	
	/**
	 * @author champo
	 *
	 */
	public interface WorkReady {

		public void result(Result result);
	}

	private final class WorkItem {

		public WorkItem(Signal signal, WorkReady ready) {
			this.signal = signal;
			this.callback = ready;
		}

		Signal signal;
		
		WorkReady callback;
		
	}

	/**
	 * @author champo
	 *
	 */
	private final class QueueConsumer implements Runnable {
		
		@Override
		public void run() {
			
			try {
				WorkItem item;
				while ((item = queue.take()) != null) {
					final Result result = process(item.signal);
					item.callback.result(result);
				}
			} catch (final InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}

	private final SignalStore store;
	
	private final ExecutorService pool;

	private final Thread thread;

	private final BlockingQueue<WorkItem> queue;
	
	public Processor(int threads, SignalStore store) {
		pool = Executors.newFixedThreadPool(threads);
		this.store = store;
		
		queue = new LinkedBlockingDeque<>();
		thread = new Thread(new QueueConsumer());
		
		thread.start();
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
	
	public void request(Signal signal, WorkReady ready) {
		queue.add(new WorkItem(signal, ready));
	}

	public void stop() {
		//FIXME: Do sth
	}

}
