/**
 * 
 */
package ar.edu.itba.pod.legajo50453.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
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

	private final MessageDispatcher dispatcher;
	
	private final BlockingQueue<WorkRequest> requests;

	private final Channel channel;
	
	private final Thread runner;

	public Processor(Channel channel, MessageDispatcher dispatcher) {
		this.dispatcher = dispatcher;
		this.requests = new LinkedBlockingQueue<>();
		this.channel = channel;
		this.runner = new Thread(new Runnable() {
			
			@Override
			public void run() {
					
				try {
					while (true) {
						final WorkRequest request = requests.poll(1, TimeUnit.SECONDS);
						if (request != null) {
							work(request);
						}
					}
					
				} catch (final InterruptedException e) {
					e.printStackTrace();
				}
			}
		});
		
		this.runner.start();
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
	
	public void stop() {
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
			
			request.future.setResponse(result);
		}

	}
	
	private static void abort(WorkRequest request) {
		
		synchronized (request.lock) {
			
			for (final NotifyingFuture<Result> future : request.remotes) {
				future.setListener(null);
				future.cancel(true);
			}
			
			request.remotes = null;
		}
	}

	private static class WorkRequest {
		
		Object lock = new Object();

		FutureResult future;
		
		Signal reference;
		
		int count;
		
		List<NotifyingFuture<Result>> remotes;

		/**
		 * {@inheritDoc}
		 */
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
			abort(request);
			return true;
		}
		
	}

}
