package ar.edu.itba.pod.legajo50453.mt;

import java.util.Set;

import org.jgroups.Address;

import ar.edu.itba.pod.legajo50453.message.SignalData;

public class NodeAddedSelector implements SignalSelector {

	private final SignalStore store;
	
	private final int nodeCount;
	
	private final Address me;

	private final Address recipient;

	public NodeAddedSelector(Address me, Address recipient, SignalStore store, int nodeCount) {
		this.me = me;
		this.recipient = recipient;
		this.store = store;
		this.nodeCount = nodeCount;
	}

	@Override
	public Set<SignalData> selectPrimaries() {
		return store.getRandomPrimaries(store.getPrimaryCount() / nodeCount, me, recipient);
	}

	@Override
	public Set<SignalData> selectBackups() {
		return store.getRandomBackups(store.getBackupCount() / nodeCount, me, recipient);
	}

}
