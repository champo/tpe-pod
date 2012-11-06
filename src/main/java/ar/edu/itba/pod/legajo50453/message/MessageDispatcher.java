package ar.edu.itba.pod.legajo50453.message;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.util.NotifyingFuture;

import ar.edu.itba.pod.legajo50453.FutureImpl;

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
				future.aborted();
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
			
			future.aborted(e);
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

	public class ResponseFuture<T> extends FutureImpl<T> {
		
		private final long id;

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
		
		public boolean doCancel() {
			return cancelFuture(this);
		}

	}

}
