/**
 * 
 */
package ar.edu.itba.pod.legajo50453.mt;

import java.util.Random;
import java.util.Set;

import org.jgroups.Address;
import org.jgroups.View;

import ar.edu.itba.pod.legajo50453.message.SignalData;

/**
 * @author champo
 *
 */
public class NodeDisconnectSelector implements DistributionSelector {

	private final KnownNodeSignals signals;
	
	private final View view;
	
	private final Random rnd = new Random();

	private final Address me;

	public NodeDisconnectSelector(View view, SignalStore store, Address node, Address me) {
		this.view = view;
		this.me = me;
		this.signals = store.getKnownSignalsFor(node, me);
	}

	@Override
	public Set<SignalData> selectPrimaries() {
		return signals.primaries;
	}

	@Override
	public Set<SignalData> selectBackups() {
		return signals.backups;
	}
	
	public static final class KnownNodeSignals {
		
		private final Set<SignalData> primaries;
		
		private final Set<SignalData> backups;

		public KnownNodeSignals(Set<SignalData> primaries, Set<SignalData> backups) {
			this.primaries = primaries;
			this.backups = backups;
		}
		
	}
	
	@Override
	public Address getDestinationAddress() {
		
		while (true) {
			final Address address = view.getMembers().get(rnd.nextInt(view.size()));
			
			if (me.equals(address)) {
				continue;
			}
			
			return address;
		}
	}

}
