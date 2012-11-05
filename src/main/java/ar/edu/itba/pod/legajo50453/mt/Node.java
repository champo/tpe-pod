/**
 * 
 */
package ar.edu.itba.pod.legajo50453.mt;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;

import ar.edu.itba.pod.api.NodeStats;
import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.Result.Item;
import ar.edu.itba.pod.api.SPNode;
import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.api.SignalProcessor;
import ar.edu.itba.pod.legajo50453.message.BackupSignal;
import ar.edu.itba.pod.legajo50453.message.MessageDispatcher;
import ar.edu.itba.pod.legajo50453.message.SimilarRequest;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/**
 * @author champo
 *
 */
public class Node implements SignalProcessor, SPNode {

	private final MessageConsumer consumer;
	
	private final AtomicInteger recieved = new AtomicInteger();
	
	private final Channel channel;
	
	private final Random rnd = new Random();
	
	private boolean suicide;
	
	private int stableNodes;

	private final BlockingQueue<Message> inboundMessages;

	private final Thread consumerThread;

	private final SignalStore store;

	private final MessageDispatcher dispatcher;
	
	private View currentView;

	private final Processor processor;
	
	public Node(int threads) throws Exception {

		channel = new JChannel("jgroups.xml");
		channel.setReceiver(new MessageReciever());
		
		inboundMessages = new LinkedBlockingDeque<>();
		dispatcher = new MessageDispatcher(channel);
		
		store = new SignalStore();
		processor = new Processor(threads, store);
		
		consumer = new MessageConsumer(inboundMessages, store, dispatcher, processor);
		consumerThread = new Thread(consumer);
		consumerThread.start();
		
	}

	@Override
	public void join(String clusterName) throws RemoteException {
		suicide = false;
		
		try {
			channel.connect(clusterName);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void exit() throws RemoteException {
		suicide = true;
		
		processor.stop();
		store.empty();
		recieved.set(0);
		channel.disconnect();
	}

	@Override
	public NodeStats getStats() throws RemoteException {
		
		boolean degraded = true;
		if (channel.isConnected()) {
			
			final int nodes = channel.getView().size();
			
			if (nodes > 1 && nodes == stableNodes) {
				degraded = false;
			}
		}
		
		return new NodeStats(channel.getName(), recieved.get(), store.getPrimaryCount(), store.getBackupCount(), degraded);
	}

	@Override
	public void add(Signal signal) throws RemoteException {
		
		if (store.add(signal) && currentView.size() > 1) {
			// The signal is new enough, let's back it up
			sendBackup(signal);
		}
			
	}

	private void sendBackup(Signal signal) {

		boolean success = false;
		
		while (!success) {
			
			final View view = channel.getView();
			final Address address = view.getMembers().get(rnd.nextInt(view.size()));
			
			if (address.equals(channel.getAddress())) {
				continue;
			}
			
			final Future<Void> response = dispatcher.sendMessage(address, new BackupSignal(signal, channel.getAddress()));
			
			try {
				response.get();
				success = true;
			} catch (InterruptedException | ExecutionException e) {
				continue;
			}
		}
		
	}

	@Override
	public Result findSimilarTo(Signal signal) throws RemoteException {
		
		if (null == signal) {
			throw new IllegalArgumentException();
		}
		
		if (suicide) {
			return new Result(signal);
		}
		
		recieved.incrementAndGet();
		
		final List<Future<Result>> remoteResults = new ArrayList<>();
		for (final Address address : currentView.getMembers()) {
			
			if (address != channel.getAddress()) {
				remoteResults.add(dispatcher.<Result>sendMessage(address, new SimilarRequest(signal)));
			}
		}
		
		Result result = processor.process(signal);
		
		for (final Future<Result> future : remoteResults) {
			try {
				final Result remoteResult = future.get();
				if (remoteResult != null) {
					
					for (final Item item : remoteResult.items()) {
						result = result.include(item);
					}
				}
				
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	
	
	private final class MessageReciever extends ReceiverAdapter {
		
		@Override
		public void receive(Message msg) {
			inboundMessages.add(msg);
		}
		
		@Override
		public void viewAccepted(View view) {
			
			if (currentView == null) {
				currentView = view;
				return;
			}
			
			final SetView<Address> difference = Sets.symmetricDifference(new HashSet<>(currentView.getMembers()), new HashSet<>(view.getMembers()));
			currentView = view;
			if (difference.size() > 1) {
				// WERE ALL DOOMED
				throw new RuntimeException();
			}
			
			if (difference.size() == 0) {
				return;
			}
			
			final Address change = difference.iterator().next();
			if (view.containsMember(change)) {
				// TODO: Handle adding nodes
				stableNodes = 0;
			} else {
				dispatcher.nodeDisconnected(change);
				//TODO: Handle rebalacing
			}
		}
	}

}
