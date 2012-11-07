package ar.edu.itba.pod.legajo50453.message;

import java.io.Serializable;

import org.jgroups.Address;

import ar.edu.itba.pod.api.Signal;

public class SignalData implements Serializable {

	private static final long serialVersionUID = 6453745424128393102L;

	private final Signal signal;
	
	private final Address other;

	public SignalData(Signal signal, Address other) {
		this.signal = signal;
		this.other = other;
	}
	
	public Signal getSignal() {
		return signal;
	}
	
	public Address getOtherNode() {
		return other;
	}
	
}
