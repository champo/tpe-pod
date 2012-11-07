package ar.edu.itba.pod.legajo50453.mt;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.legajo50453.message.SignalData;

public class SignalStore {

	private final class SignalComparator implements Comparator<Signal> {
		
		@Override
		public int compare(Signal o1, Signal o2) {
			
			final int diff = o1.hashCode() - o2.hashCode();
			if (diff == 0) {
				
				final byte[] c1 = o1.content();
				final byte[] c2 = o2.content();
				
				for (int i = 0; i < Signal.SIZE; i++) {
					if (c1[i] - c2[i] != 0) {
						return c1[i] - c2[i];
					}
				}
				
				return 0;
			}
			
			return diff;
		}
	}

	private final ConcurrentSkipListSet<Signal> primaries;
	
	private final Set<SignalData> backups;
	
	public SignalStore() {
		
		backups = new HashSet<>();
		
		primaries = new ConcurrentSkipListSet<>(new SignalComparator());
	}

	public void addBackup(SignalData backup) {
		synchronized (backups) {
			backups.add(backup);
		}
	}

	public void empty() {
		primaries.clear();
		backups.clear();
	}

	public long getPrimaryCount() {
		return primaries.size();
	}

	public boolean add(Signal signal) {
		return primaries.add(signal);
	}

	public Set<Signal> getPrimaries() {
		return primaries;
	}
	
	public long getBackupCount() {
		
		synchronized (backups) {
			return backups.size();
		}
	}

}
