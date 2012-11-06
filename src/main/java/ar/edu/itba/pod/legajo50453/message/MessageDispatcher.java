package ar.edu.itba.pod.legajo50453.message;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.util.FutureListener;
import org.jgroups.util.NotifyingFuture;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class MessageDispatcher {

	private final AtomicLong idGenerator = new AtomicLong(0);
	
	private final Map<Long, ResponseFuture<?>> futures;
	
	private final Multimap<Address, ResponseFuture<?>> addressToFuture;
	
	private final Channel channel;
	
	public MessageDispatcher(Channel channel) {
		super();
		this.channel = channel;
		this.futures = new ConcurrentHashMap<Long, MessageDispatcher.ResponseFuture<?>>();
		this.addressToFuture = Multimaps.synchronizedMultimap(HashMultimap.<Address, ResponseFuture<?>>create());
	}
	
	public void nodeDisconnected(Address address) {
		final Collection<ResponseFuture<?>> brokenFutures = addressToFuture.removeAll(address);
		if (brokenFutures != null) {
			for (final ResponseFuture<?> future : brokenFutures) {
				futures.remove(future.getId());
				future.nodeDisconnected();
			}
		}
	}

	public <T> NotifyingFuture<T> sendMessage(Address address, Serializable obj) {
		
		final long id = idGenerator.getAndIncrement();
		final ResponseFuture<T> future = new ResponseFuture<T>(id, address);
		
		addressToFuture.put(address, future);
		futures.put(id, future);
		
		try {
			channel.send(address, new AnswerableMessage(id, obj));
		} catch (final Exception e) {
			addressToFuture.remove(address, future);
			futures.remove(id);
			
			future.nodeDisconnected(e);
		}
		
		return future;
	}
	
	public void processResponse(Address origin, AnswerMessage response) {
		
		final ResponseFuture<?> future = futures.remove(response.getId());
		if (future != null) {
			addressToFuture.remove(origin, future);
			future.setResponse(response.getPayload());
		}
	}
	
	public void respondTo(Address address, long id, Serializable payload) throws Exception {
		channel.send(address, new AnswerMessage(id, payload));
	}
	
	private <T> boolean cancelFuture(ResponseFuture<T> future) {
		if (futures.remove(future.getId()) != null) {
			addressToFuture.remove(future.getAddress(), future);
			return true;
		}
		
		return false;
	}

	private class ResponseFuture<T> implements NotifyingFuture<T> {
		
		private T response;
		
		private final CountDownLatch ready = new CountDownLatch(1);
		
		private boolean disconnected;
		
		private boolean cancelled;
		
		private Exception cause;
		
		private final long id;

		private FutureListener<T> listener;

		private final Address address;
		
		private ResponseFuture(long id, Address address) {
			super();
			this.id = id;
			this.address = address;
		}

		private Address getAddress() {
			return address;
		}

		private long getId() {
			return id;
		}
		
		private void setResponse(Serializable response) {
			
			if (ready.getCount() == 0 || disconnected) {
				throw new IllegalStateException();
			}
			
			try {
				this.response = (T) response;
			} catch (final ClassCastException e) {
				this.response = null;
			}
			
			ready.countDown();
			if (listener != null) {
				listener.futureDone(this);
			}
		}
		
		private void nodeDisconnected(Exception e) {
			
			if (ready.getCount() != 0) {
				disconnected = true;
				cause = e;
			}
			
			ready.countDown();
		}

		private void nodeDisconnected() {
			
			if (ready.getCount() != 0) {
				disconnected = true;
			}
			ready.countDown();
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			
			if (!mayInterruptIfRunning || isDone()) {
				return false;
			}

			return cancelFuture(this);
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
			
			ready.await();
			
			if (disconnected) {
				throw new ExecutionException("The reciepient disconnected before answering", cause);
			}
			
			return response;
		}

		@Override
		public T get(long timeout, TimeUnit unit) throws InterruptedException,
				ExecutionException, TimeoutException {
			
			if (ready.await(timeout, unit)) {
				return get();
			}
			
			throw new TimeoutException();
		}

		@Override
		public NotifyingFuture<T> setListener(FutureListener<T> listener) {
			
			if (isDone()) {
				listener.futureDone(this);
			}
			this.listener = listener;
			
			return this;
		}

	}

}
