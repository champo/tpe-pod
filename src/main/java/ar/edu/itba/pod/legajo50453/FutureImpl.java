/**
 * 
 */
package ar.edu.itba.pod.legajo50453;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jgroups.util.FutureListener;
import org.jgroups.util.NotifyingFuture;

/**
 * @author champo
 * 
 */
public abstract class FutureImpl<T> implements NotifyingFuture<T> {
	
	private final Object lock = new Object();

	private T response;
	
	private final CountDownLatch ready = new CountDownLatch(1);
	
	private boolean aborted;
	
	private boolean cancelled;
	
	private Exception cause;
	
	private FutureListener<T> listener;

	public void setResponse(Object response) {
		
		synchronized (lock) {
			
			if (ready.getCount() == 0 || aborted) {
				throw new IllegalStateException();
			}
			
			try {
				this.response = (T) response;
			} catch (final ClassCastException e) {
				this.response = null;
			}
			
			ready();
		}
	}

	private void ready() {

		ready.countDown();
		if (listener != null) {
			listener.futureDone(this);
		}
	}

	public void aborted(Exception e) {
		
		synchronized (lock) {
			
			if (ready.getCount() != 0) {
				aborted = true;
				cause = e;
			}
			
			ready();
		}
	}

	public void aborted() {
		
		synchronized (lock) {
			aborted(null);
		}
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public boolean isDone() {
		return ready.getCount() == 0;
	}

	@Override
	public T get() throws InterruptedException, ExecutionException {

		synchronized (lock) {
			ready.await();
			
			if (aborted) {
				throw new ExecutionException("The reciepient disconnected before answering", cause);
			}
			
			return response;
		}
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {

		synchronized (lock) {

			if (ready.await(timeout, unit)) {
				return get();
			}
			
			throw new TimeoutException();
		}
	}

	@Override
	public NotifyingFuture<T> setListener(FutureListener<T> listener) {
		
		synchronized (lock) {
			
			if (isDone() && listener != null) {
				listener.futureDone(this);
			}
			this.listener = listener;
			
			return this;
		}
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {

		synchronized (lock) {
			
			if (!mayInterruptIfRunning || isDone()) {
				return false;
			}
			
			cancelled = doCancel();
			if (cancelled) {
				ready();
			}
			
			return cancelled;
		}
	}

	protected abstract boolean doCancel();

}
