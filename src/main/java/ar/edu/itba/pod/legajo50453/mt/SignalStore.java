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
		
		backups.add(data.getSignal());
		addKnown(data);
		
		writeLock.unlock();
	}

	public void addPrimary(SignalData data) {
		
		writeLock.lock();
		
		primaries.add(data.getSignal());
		addKnown(data);
		
		writeLock.unlock();
	}
	
	@GuardedBy("writeLock")
	private void addKnown(SignalData data) {
		
		if (data.getOtherNode() != null) {
			knownSignals.put(data.getOtherNode(), data.getSignal());
		}
	}

	public void empty() {
		
		writeLock.lock();
		
		primaries.clear();
		backups.clear();
		knownSignals.clear();
		
		writeLock.unlock();
	}

	public long getPrimaryCount() {
		
		readLock.lock();
		final int size = primaries.size();
		readLock.unlock();
		
		return size;
	}

	public boolean add(Signal signal) {
		
		writeLock.lock();
		final boolean added = primaries.add(signal);
		writeLock.unlock();
		
		return added;
	}

	public Set<Signal> getPrimaries() {
		
		writeLock.lock();
		final Set<Signal> signals = new HashSet<>(primaries);
		writeLock.unlock();
		
		return signals;
	}
	
	public long getBackupCount() {
		
		readLock.lock();
		final int size = backups.size();
		readLock.unlock();
		
		return size;
	}

	public void updateKnownSignal(Address original, Signal signal, Address destination) {
		
		writeLock.lock();
		
		knownSignals.remove(original, signal);
		knownSignals.put(destination, signal);
		
		writeLock.unlock();
		
	}

	public KnownNodeSignals getKnownSignalsFor(Address node, Address me) {
		
		writeLock.lock();
		final Collection<Signal> signals = knownSignals.removeAll(node);
		writeLock.unlock();

		final Set<SignalData> amPrimary = new HashSet<>();
		final Set<SignalData> amBackup = new HashSet<>();
		
		readLock.lock();
		
		for (final Signal signal : signals) {
			
			final SignalData data = new SignalData(signal, me);
			if (primaries.contains(signal)) {
				amPrimary.add(data);
			} else {
				amBackup.add(data);
			}
			
		}
		
		readLock.unlock();
		
		return new KnownNodeSignals(amBackup, amPrimary);
	}

	

}
