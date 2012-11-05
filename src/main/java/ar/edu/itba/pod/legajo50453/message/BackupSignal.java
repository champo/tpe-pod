package ar.edu.itba.pod.legajo50453.message;

import java.io.Serializable;

import org.jgroups.Address;

import ar.edu.itba.pod.api.Signal;

public class BackupSignal implements Serializable {

	private static final long serialVersionUID = 6453745424128393102L;

	private final Signal signal;
	
	private final Address primary;

	public BackupSignal(Signal signal, Address primary) {
		this.signal = signal;
		this.primary = primary;
	}
	
	public Signal getSignal() {
		return signal;
	}
	
	public Address getPrimary() {
		return primary;
	}

}
