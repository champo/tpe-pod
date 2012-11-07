/**
 * 
 */
package ar.edu.itba.pod.legajo50453.mt;

import java.rmi.RemoteException;
import java.util.HashSet;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ar.edu.itba.pod.api.NodeStats;
import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.SPNode;
import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.api.SignalProcessor;
import ar.edu.itba.pod.legajo50453.message.BackupSignal;
import ar.edu.itba.pod.legajo50453.message.MessageDispatcher;
import ar.edu.itba.pod.legajo50453.worker.Processor;
import ar.edu.itba.pod.legajo50453.worker.WorkerPool;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/**
 * @author champo
 *
 */
public class Node implements SignalProcessor, SPNode {
	
	final static Logger logger = LoggerFactory.getLogger(Node.class);

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

	private final WorkerPool workerPool;

	public Node(int threads) throws Exception {

		channel = new JChannel("jgroups.xml");
		channel.setReceiver(new MessageReciever());
		
		inboundMessages = new LinkedBlockingDeque<>();
		dispatcher = new MessageDispatcher(channel);
		
		store = new SignalStore();
		processor = new Processor(channel, dispatcher);
		
		workerPool = new WorkerPool(threads, store);
		consumer = new MessageConsumer(inboundMessages, store, dispatcher, workerPool);
		consumerThread = new Thread(consumer);
		consumerThread.start();
		
	}

	@Override
	public void join(String clusterName) throws RemoteException {
		logger.info("Joining cluster " + clusterName);
		suicide = false;
		
		processor.start();
		try {
			channel.connect(clusterName);
		} catch (final Exception e) {
			logger.error("Failed to connect to cluster", e);
		}
	}

	@Override
	public void exit() throws RemoteException {

		logger.info("Leaving cluster");
		suicide = true;
		
		processor.stop();
		store.empty();
		recieved.set(0);
			
		currentView = null;
		channel.disconnect();
	}

	@Override
	public NodeStats getStats() throws RemoteException {
		
		boolean degraded = true;
			
		final View view = currentView;
		if (view != null) {

			final int nodes = view.size();

			if (nodes > 1 && nodes == stableNodes) {
				degraded = false;
			}
		}
		
		final NodeStats stats = new NodeStats(channel.getName(), recieved.get(), store.getPrimaryCount(), store.getBackupCount(), degraded);
		logger.debug("getStats(): ", new NodeStatsPrinter(stats));
		return stats;
	}

	@Override
	public void add(Signal signal) throws RemoteException {
		
		logger.debug("Adding signal", signal);
		//TODO: Randomize the primary add
		if (store.add(signal)) {

			final View view = currentView;
			if (view != null && view.size() > 1) {
				// The signal is new enough, let's back it up
				sendBackup(signal);
			}
		}
			
	}

	private void sendBackup(Signal signal) {

		boolean success = false;
		
		while (!success && channel.isConnected()) {
			
			final View view = currentView;
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
		
		try {
			logger.debug("Processing signal", signal);
			if (channel.isConnected()) {
				return processor.process(signal).get();
			} else {
				return workerPool.process(signal);
			}
			
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
			return null;
		}
	}

	
	private final class MessageReciever extends ReceiverAdapter {
		
		@Override
		public void receive(Message msg) {
			inboundMessages.add(msg);
		}
		
		@Override
		public void viewAccepted(View view) {
			
			logger.debug("A new view is in town");
			
			if (currentView == null) {
				currentView = view;
				return;
			}
			
			final SetView<Address> difference = Sets.symmetricDifference(new HashSet<>(currentView.getMembers()), new HashSet<>(view.getMembers()));
			
			logger.info("View difference", difference);
			currentView = view;
			if (difference.size() > 1) {
				// WERE ALL DOOMED
				logger.error("Got more than one difference, humped");
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
