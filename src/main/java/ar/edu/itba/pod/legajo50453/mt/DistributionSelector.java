package ar.edu.itba.pod.legajo50453.mt;

import java.util.Set;

import org.jgroups.Address;

import ar.edu.itba.pod.legajo50453.message.SignalData;

public interface DistributionSelector {
	
	public Set<SignalData> selectPrimaries();
	
	public Set<SignalData> selectBackups();
	
	public Address getDestinationAddress();

}
