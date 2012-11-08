package ar.edu.itba.pod.legajo50453.mt;

import java.util.Set;

import org.jgroups.Address;

import ar.edu.itba.pod.legajo50453.message.SignalData;

public class NodeAddedSelector implements SignalSelector {

	private final SignalStore store;
	
	private final int nodeCount;
	
	private final Address me;

	public NodeAddedSelector(Address me, SignalStore store, int nodeCount) {
		this.me = me;
		this.store = store;
		this.nodeCount = nodeCount;
	}

	@Override
	public Set<SignalData> selectPrimaries() {
		return store.getRandomPrimaries(store.getPrimaryCount() / nodeCount, me);
	}

	@Override
	public Set<SignalData> selectBackups() {
		return store.getRandomBackups(store.getBackupCount() / nodeCount, me);
	}

}
