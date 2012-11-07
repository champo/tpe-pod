package ar.edu.itba.pod.legajo50453.mt;

import java.util.Set;

import ar.edu.itba.pod.legajo50453.message.SignalData;

public class NodeAddedSelector implements SignalSelector {

	private final SignalStore store;
	private final int nodeCount;

	public NodeAddedSelector(SignalStore store, int nodeCount) {
		this.store = store;
		this.nodeCount = nodeCount;
	}

	@Override
	public Set<SignalData> selectPrimaries() {
		return store.getRandomPrimaries(store.getPrimaryCount() / nodeCount);
	}

	@Override
	public Set<SignalData> selectBackups() {
		// TODO Auto-generated method stub
		return null;
	}

}
