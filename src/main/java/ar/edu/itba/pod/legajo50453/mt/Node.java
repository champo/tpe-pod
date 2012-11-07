/**
 * 
 */
package ar.edu.itba.pod.legajo50453.mt;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
import org.jgroups.util.NotifyingFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ar.edu.itba.pod.api.NodeStats;
import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.SPNode;
import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.api.SignalProcessor;
import ar.edu.itba.pod.legajo50453.message.MessageDispatcher;
import ar.edu.itba.pod.legajo50453.message.PrimarySignal;
import ar.edu.itba.pod.legajo50453.message.SignalData;
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
	
	private final BlockingQueue<Message> inboundMessages;

	private final Thread consumerThread;

	private final SignalStore store;

	private final MessageDispatcher dispatcher;
	
	private View currentView;

	private final Processor processor;

	private final WorkerPool workerPool;

	private boolean degraded;
	
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
		
		try {
			channel.connect(clusterName);
		} catch (final Exception e) {
			logger.error("Failed to connect to cluster", e);
		}
	}

	@Override
	public void exit() throws RemoteException {
		logger.info("Leaving cluster");
		
		processor.stop();
		workerPool.reset();
		
		store.empty();
		recieved.set(0);
			
		currentView = null;
		channel.disconnect();
	}

	@Override
	public NodeStats getStats() throws RemoteException {
	
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
			final Address me = channel.getAddress();
			
			final Address address = view.getMembers().get(rnd.nextInt(view.size()));
			
			if (address.equals(me)) {
				continue;
			}
			
			final Future<Void> response = dispatcher.sendMessage(address, new SignalData(signal, me));
			
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
		
		recieved.incrementAndGet();
		
		try {
			logger.debug("Processing signal", signal);
			if (processor.isRunning()) {
				return processor.process(signal).get();
			} else {
				return workerPool.process(signal);
			}
			
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
			return null;
		}
	}

	private void degrade() {
		logger.info("OH GOD OH GOD WERE ALL GONNA DIE");
		degraded = true;
		
		processor.pause();
		workerPool.reset();
	}
	
	private void normalityRestored() {
		logger.info("We now have normality, whatever that means");
		
		degraded = false;
		processor.start();
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

				if (view.size() > 1) {
					normalityRestored();
				}
				
				return;
			}
			
			final Set<Address> currentSet = new HashSet<>(currentView.getMembers());
			final Set<Address> newSet = new HashSet<>(view.getMembers());
			
			final SetView<Address> removed = Sets.difference(currentSet, newSet);
				
			if (removed.size() > 1) {
				logger.info("I don't have to put up with this :D");
				System.exit(1);
			} else if (removed.size() == 1) {
				final Address change = removed.iterator().next();
				topologyChange(new NodeDisconnectSelector(store, change, channel.getAddress()));
			}
			
			//TODO: Handle joins
		}
	}

	private void topologyChange(SignalSelector selector) {
		
		degrade();
		
		final View view = currentView;
		
		try {
			distributePrimaries(selector, view);
			distributeBackups(selector, view);
			
			consumer.waitForPhaseEnd(view.size());
		} catch (final Exception e) {
			logger.error("Death during topology change", e);
			System.exit(3);
		}
		
		normalityRestored();
	}

	public void distributePrimaries(SignalSelector selector, final View view) throws Exception {

		final Set<SignalData> primaries = selector.selectPrimaries();
		
		final List<NotifyingFuture<Void>> futures = new ArrayList<>();
		for (final SignalData signalData : primaries) {
			
			while (true) {
				final Address address = view.getMembers().get(rnd.nextInt(view.size()));
				
				if (address.equals(channel.getAddress())) {
					continue;
				}
				
				futures.add(dispatcher.<Void>sendMessage(address, new PrimarySignal(signalData)));
			}
		}
		
		for (final NotifyingFuture<Void> future : futures) {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				logger.error("A future failed during a topology change. We bail.", e);
				System.exit(2);
			}
		}

		consumer.waitForPhaseEnd(view.size());
	}
	
	public void distributeBackups(SignalSelector selector, final View view) throws Exception {

		final Set<SignalData> backups = selector.selectBackups();
		
		final List<NotifyingFuture<Void>> futures = new ArrayList<>();
		for (final SignalData signalData : backups) {
			
			while (true) {
				final Address address = view.getMembers().get(rnd.nextInt(view.size()));
				
				if (address.equals(channel.getAddress())) {
					continue;
				}
				
				futures.add(dispatcher.<Void>sendMessage(address, signalData));
			}
		}
		
		for (final NotifyingFuture<Void> future : futures) {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				logger.error("A future failed during a topology change. We bail.", e);
				System.exit(2);
			}
		}
		
		consumer.waitForPhaseEnd(view.size());
	}

}
