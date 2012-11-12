package ar.edu.itba.pod.legajo50453.mt;

import java.util.Set;

import org.jgroups.Address;
import org.jgroups.View;

import ar.edu.itba.pod.legajo50453.message.SignalData;

public class NodeAddedSelector implements DistributionSelector {

	private final SignalStore store;
	
	private final Address me;

	private final Address recipient;

	private final View view;
	
	public NodeAddedSelector(Address me, Address recipient, View view, SignalStore store) {
		this.me = me;
		this.recipient = recipient;
		this.view = view;
		this.store = store;
	}

	@Override
	public Set<SignalData> selectPrimaries() {
		final int count = (int) Math.ceil((double) store.getPrimaryCount() / (double) view.size()); 
		return store.getRandomPrimaries(count, me, recipient);
	}

	@Override
	public Set<SignalData> selectBackups() {
		final int count = (int) Math.ceil((double) store.getBackupCount() / (double) view.size());
		return store.getRandomBackups(count, me, recipient);
	}
	
	@Override
	public Address getDestinationAddress() {
		return recipient;
	}

}
