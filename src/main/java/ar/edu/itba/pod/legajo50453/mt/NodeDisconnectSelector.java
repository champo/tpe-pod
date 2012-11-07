/**
 * 
 */
package ar.edu.itba.pod.legajo50453.mt;

import java.util.HashSet;
import java.util.Set;

import org.jgroups.Address;

import ar.edu.itba.pod.legajo50453.message.SignalData;

/**
 * @author champo
 *
 */
public class NodeDisconnectSelector implements SignalSelector {

	private final SignalStore store;
	
	private final Address address;

	public NodeDisconnectSelector(SignalStore store, Address address) {
		this.store = store;
		this.address = address;
	}

	@Override
	public Set<SignalData> selectPrimaries() {
		return new HashSet<>();
	}

	@Override
	public Set<SignalData> selectBackups() {
		return new HashSet<>();
	}

}
