/**
 * 
 */
package ar.edu.itba.pod.legajo50453.mt;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import ar.edu.itba.pod.api.NodeStats;
import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.Result.Item;
import ar.edu.itba.pod.api.SPNode;
import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.api.SignalProcessor;

/**
 * @author champo
 *
 */
public class MultiThreadSignalProcessor implements SignalProcessor, SPNode {
	
	private ExecutorService pool;
	
	private Set<Signal> signals = new HashSet<>();
	
	private boolean suicide;
	
	private AtomicInteger recieved = new AtomicInteger();
	
	public MultiThreadSignalProcessor(int threads) {
		pool = Executors.newFixedThreadPool(threads);
	}

	@Override
	public void join(String clusterName) throws RemoteException {
		suicide = false;
	}

	@Override
	public void exit() throws RemoteException {
		suicide = true;
		
		synchronized (signals) {
			signals.clear();
		}
		recieved.set(0);
	}

	@Override
	public NodeStats getStats() throws RemoteException {
		return new NodeStats("foo", recieved.get(), signals.size(), 0, false);
	}

	@Override
	public void add(Signal signal) throws RemoteException {
		
		synchronized (signals) {
			signals.add(signal);
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
		
		List<Future<Item>> futures;
		
		synchronized (signals) {
			futures = new ArrayList<Future<Item>>(signals.size());
			for (Signal reference : signals) {
				Future<Item> future = pool.submit(new WorkRequest(reference, signal));
				futures.add(future);
			}
		}
		
		Result result = new Result(signal);
		for (Future<Item> future : futures) {
			try {
				result = result.include(future.get());
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}

}
