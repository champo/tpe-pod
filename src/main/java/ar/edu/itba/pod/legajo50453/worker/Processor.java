/**
 * 
 */
package ar.edu.itba.pod.legajo50453.worker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.View;
import org.jgroups.util.FutureListener;
import org.jgroups.util.NotifyingFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.Result.Item;
import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.legajo50453.FutureImpl;
import ar.edu.itba.pod.legajo50453.message.MessageDispatcher;
import ar.edu.itba.pod.legajo50453.message.SimilarRequest;

/**
 * @author champo
 *
 */
public final class Processor {

	final static Logger logger = LoggerFactory.getLogger(Processor.class);
	
	private final Object workLock = new Object();
	
	private final MessageDispatcher dispatcher;
	
	private final LinkedBlockingDeque<WorkRequest> requests;
	
	private final Set<WorkRequest> working;

	private final Channel channel;
	
	private Thread runner;

	private final Semaphore permit;
	
	private boolean paused;

	private boolean running;
	
	private final Semaphore requestReady = new Semaphore(0);

	public Processor(Channel channel, MessageDispatcher dispatcher) {
		this.dispatcher = dispatcher;
		this.requests = new LinkedBlockingDeque<>();
		this.working = new HashSet<>();
		this.channel = channel;
		this.permit = new Semaphore(0);
	}

	public void start() {
		
		running = true;
		if (runner == null) {
			runner = new Thread(new RequestConsumer());
			runner.start();
		}
		
		paused = false;
		permit.release();
	}
	
	public boolean isRunning() {
		return running;
	}
	
	public Future<Result> process(Signal reference) {
		
		final WorkRequest request = new WorkRequest();
		final FutureResult future = new FutureResult(request);
		
		request.future = future;
		request.reference = reference;
		requests.add(request);
		
		return future;
	}
	
	private void work(final WorkRequest request) {
		
		synchronized (request.lock) {
			logger.info("Processing work request", request);
			
			synchronized (workLock) {
				
				if (paused) {
					requests.addFirst(request);
					return;
				}
				
				working.add(request);
			}
			
			final View currentView = channel.getView();
			final ResultListener listener = new ResultListener(request);
			
			request.count = currentView.size();
			request.remotes = new ArrayList<>();
			
			for (final Address address : currentView.getMembers()) {
				logger.debug("Requesting order to " + address);
				final NotifyingFuture<Result> future = dispatcher.<Result>sendMessage(address, new SimilarRequest(request.reference));
				future.setListener(listener);
				request.remotes.add(future);
			}
			
			logger.debug("Requested all the remote orders");
		}
		
	}
	
	private static boolean abort(WorkRequest request) {
		
		synchronized (request.lock) {
			
			if (request.count == 0) {
				return false;
			}
			
			for (final NotifyingFuture<Result> future : request.remotes) {
				future.setListener(null);
				future.cancel(true);
			}
			
			request.remotes = null;
			
			return true;
		}
	}
	
	public void pause() {
		
		if (runner != null) {
			paused = true;
			permit.drainPermits();
			
			abortAll(true);
		}
	}
	
	public void stop() {
		
		running = false;
		if (runner != null) {
			
			if (requests.size() == 0) {
				runner.interrupt();
			}

			
			int waits;
			
			synchronized (workLock) {
				waits = working.size();
				requestReady.drainPermits();
			}
			
			try {
				requestReady.acquire(waits);
			} catch (final InterruptedException e) {
			}

			try {
				runner.join();
			} catch (final InterruptedException e) {
			} finally {
				abortAll(false);
				runner = null;
			}
		}
	}

	private void abortAll(boolean keep) {
		
		Set<WorkRequest> toAbort;
		synchronized (workLock) {
			toAbort = new HashSet<>(working);
			working.clear();
			requestReady.drainPermits();
		}
		
		for (final WorkRequest request : toAbort) {
			abort(request);
			
			if (keep) {
				requests.addFirst(request);
			}
		}

	}

	private final class RequestConsumer implements Runnable {
		
		@Override
		public void run() {
				
			try {
				
				while (running) {
					permit.acquire();
					final WorkRequest request = requests.poll(1, TimeUnit.SECONDS);
					if (request != null) {
						work(request);
					}
					permit.release();
				}
				
			} catch (final InterruptedException e) {
				logger.debug("Killing consumer", e);
			}
		}
	}

	private final class ResultListener implements FutureListener<Result> {

		private final WorkRequest request;

		private ResultListener(WorkRequest request) {
			this.request = request;
		}

		@Override
		public void futureDone(Future<Result> future) {
			
			synchronized (request.lock) {
				
				logger.debug("Need " + request.count + " responses, got one");
				if (--request.count == 0) {
					gotResult();
				}
			}
		}

		private void gotResult() {

			synchronized (workLock) {
				working.remove(request);
				requestReady.release();
			}
			
			logger.debug("Joining results");
			Result result = new Result(request.reference);
			for (final Future<Result> remote : request.remotes) {
				try {
					final Result remoteResult = remote.get();
					if (remoteResult != null) {
						
						for (final Item item : remoteResult.items()) {
							result = result.include(item);
						}
					}
					
				} catch (InterruptedException | ExecutionException e) {
					logger.error("woaaaah things got interesting", e);
					abort(request);
					requests.add(request);
				}
			}
			
			
			try {
				request.future.setResponse(result);
			} catch (final IllegalStateException e) {
				// We're not bothered by this
			}
		}

	}

	private static class WorkRequest {
		
		Object lock = new Object();

		FutureResult future;
		
		Signal reference;
		
		int count;
		
		List<NotifyingFuture<Result>> remotes;

		@Override
		public String toString() {

			return "WorkRequest [reference=" + reference + ", count=" + count + ", remotes="
					+ remotes + "]";
		}
		
	}
	
	private static class FutureResult extends FutureImpl<Result> {

		private final WorkRequest request;
		
		public FutureResult(WorkRequest request) {
			super();
			this.request = request;
		}

		@Override
		protected boolean doCancel() {
			return abort(request);
		}
		
	}

}
