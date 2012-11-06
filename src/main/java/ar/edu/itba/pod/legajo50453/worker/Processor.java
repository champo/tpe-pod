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
			
			final View currentView = channel.getView();
			final ResultListener listener = new ResultListener(request);
			
			request.count = currentView.size();
			request.remotes = new ArrayList<>();
			
			for (final Address address : currentView.getMembers()) {
				final NotifyingFuture<Result> future = dispatcher.<Result>sendMessage(address, new SimilarRequest(request.reference));
				future.setListener(listener);
				request.remotes.add(future);
			}
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
				
				if (--request.count == 0) {
					gotResult();
				}
			}
		}

		private void gotResult() {

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
