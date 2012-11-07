/**
 * 
 */
package ar.edu.itba.pod.legajo50453.mt;

import java.util.Set;

import org.jgroups.Address;

import ar.edu.itba.pod.legajo50453.message.SignalData;

/**
 * @author champo
 *
 */
public class NodeDisconnectSelector implements SignalSelector {

	private final KnownNodeSignals signals;

	public NodeDisconnectSelector(SignalStore store, Address node, Address me) {
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

}
