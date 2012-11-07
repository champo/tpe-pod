package ar.edu.itba.pod.legajo50453.mt;

import java.util.Set;

import ar.edu.itba.pod.legajo50453.message.SignalData;

public interface SignalSelector {
	
	public Set<SignalData> selectPrimaries();
	
	public Set<SignalData> selectBackups();

}
