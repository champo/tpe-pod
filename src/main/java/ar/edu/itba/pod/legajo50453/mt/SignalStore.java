package ar.edu.itba.pod.legajo50453.mt;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import net.jcip.annotations.GuardedBy;

import org.jgroups.Address;

import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.legajo50453.message.SignalData;
import ar.edu.itba.pod.legajo50453.mt.NodeDisconnectSelector.KnownNodeSignals;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class SignalStore {

	@GuardedBy("lock")
	private final Set<Signal> primaries;
	
	@GuardedBy("lock")
	private final Set<Signal> backups;
	
	@GuardedBy("lock")
	private final Multimap<Address, Signal> knownSignals;

	private final ReentrantReadWriteLock lock;

	private final ReadLock readLock;

	private final WriteLock writeLock;
	
	public SignalStore() {
		backups = new HashSet<>();
		primaries = new HashSet<>();
		knownSignals = ArrayListMultimap.<Address, Signal>create();
		lock = new ReentrantReadWriteLock();
		
		readLock = lock.readLock();
		writeLock = lock.writeLock();
	}

	public void addBackup(SignalData data) {
		
		writeLock.lock();
		
		try {
			backups.add(data.getSignal());
			addKnown(data);
		} finally {
			writeLock.unlock();
		}
	}

	public void addPrimary(SignalData data) {
		
		writeLock.lock();
		
		try {
			primaries.add(data.getSignal());
			addKnown(data);
		} finally {
			writeLock.unlock();
		}
	}
	
	@GuardedBy("writeLock")
	private void addKnown(SignalData data) {
		
		if (data.getOtherNode() != null) {
			knownSignals.put(data.getOtherNode(), data.getSignal());
		}
	}

	public void empty() {
		
		writeLock.lock();
		
		try {
			primaries.clear();
			backups.clear();
			knownSignals.clear();
		} finally {
			writeLock.unlock();
		}
	}

	public long getPrimaryCount() {
		
		readLock.lock();
		try {
			return primaries.size();
		} finally {
			readLock.unlock();
		}
	}

	public boolean add(Signal signal) {
		
		writeLock.lock();
		
		try {
			return primaries.add(signal);
		} finally {
			writeLock.unlock();
		}
	}

	public Set<Signal> getPrimaries() {
		
		writeLock.lock();
		
		try {
			return new HashSet<>(primaries);
		} finally {
			writeLock.unlock();
		}
		
	}
	
	public long getBackupCount() {
		
		readLock.lock();
		
		try {
			return backups.size();
		} finally {
			readLock.unlock();
		}
	}

	public void updateKnownSignal(Address original, Signal signal, Address destination) {
		
		writeLock.lock();
		
		try {
			knownSignals.remove(original, signal);
			knownSignals.put(destination, signal);
		} finally {
			writeLock.unlock();
		}
		
	}

	public KnownNodeSignals getKnownSignalsFor(Address node, Address me) {
		
		writeLock.lock();
		
		final Collection<Signal> signals;
		try {
			signals = knownSignals.removeAll(node);
		} finally {
			writeLock.unlock();
		}

		final Set<SignalData> amPrimary = new HashSet<>();
		final Set<SignalData> amBackup = new HashSet<>();
		
		readLock.lock();
		
		try {
			for (final Signal signal : signals) {
				
				final SignalData data = new SignalData(signal, me);
				if (primaries.contains(signal)) {
					amPrimary.add(data);
				} else {
					amBackup.add(data);
				}
				
			}
		} finally {
			readLock.unlock();
		}
		
		return new KnownNodeSignals(amBackup, amPrimary);
	}

	
	public Set<SignalData> getRandomPrimaries(long count) {
		return null;
	}

	

}
