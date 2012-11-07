package ar.edu.itba.pod.legajo50453.message;

import java.io.Serializable;

import org.jgroups.Address;

import ar.edu.itba.pod.api.Signal;

public class SignalStored implements Serializable {

	private static final long serialVersionUID = 4517179389790942428L;
	
	private final Signal signal;

	private final Address original;
	
	public SignalStored(Signal signal, Address original) {
		this.signal = signal;
		this.original = original;
	}

	public Signal getSignal() {
		return signal;
	}
	
	public Address getOriginal() {
		return original;
	}
	
}
