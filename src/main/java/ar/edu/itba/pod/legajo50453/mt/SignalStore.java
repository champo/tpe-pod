package ar.edu.itba.pod.legajo50453.mt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import net.jcip.annotations.GuardedBy;

import org.jgroups.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.legajo50453.message.SignalData;
import ar.edu.itba.pod.legajo50453.mt.NodeDisconnectSelector.KnownNodeSignals;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class SignalStore {
	
	final static Logger logger = LoggerFactory.getLogger(MessageConsumer.class);
	
	final static Random rnd = new Random();

	@GuardedBy("lock")
	private final Set<Signal> primaries;
	
	@GuardedBy("lock")
	private final Set<Signal> backups;
	
	@GuardedBy("lock")
	private final ArrayListMultimap<Address, Signal> knownSignals;
	
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

	
	public Set<SignalData> getRandomPrimaries(long count, Address me, Address recipient) {
		
		writeLock.lock();
		
		try {
			final Set<SignalData> result = new HashSet<>();
			final List<Address> keys = new ArrayList<>(knownSignals.keySet());
			
			for (int i = 0; i < count;) {
				
				final Address address = keys.get(rnd.nextInt(keys.size()));
				if (address.equals(recipient)) {
					continue;
				}
				
				final List<Signal> signals = knownSignals.get(address);
				
				final int size = signals.size();
				if (size > 0) {
					final int index = rnd.nextInt(size);
					final Signal signal = signals.get(index);
					
					if (primaries.contains(signal)) {
						result.add(new SignalData(signal, address));
						
						signals.remove(index);
						primaries.remove(signal);
						
						i++;
					}
				}
			}
			
			return result;
		} finally {
			writeLock.unlock();
		}
	}
	
	public Set<SignalData> getRandomBackups(long count, Address me, Address recipient) {
		
		writeLock.lock();
		
		try {
			final Set<SignalData> result = new HashSet<>();
			final List<Address> keys = new ArrayList<>(knownSignals.keySet());
			
			int available = knownSignals.size();
			if (knownSignals.containsKey(recipient)) {
				available -= knownSignals.get(recipient).size();
			}
			
			if (available < count) {
				count = available;
			}
			
			for (int i = 0; i < count;) {
				
				final Address address = keys.get(rnd.nextInt(keys.size()));
				if (address.equals(recipient)) {
					continue;
				}
				
				final List<Signal> signals = knownSignals.get(address);
				
				final int size = signals.size();
				if (size > 0) {
					final int index = rnd.nextInt(size);
					final Signal signal = signals.get(index);
					
					if (backups.contains(signal)) {
						result.add(new SignalData(signal, address));
						
						signals.remove(index);
						backups.remove(signal);
						
						i++;
					}
				}
				
			}
			
			return result;
			
		} finally {
			writeLock.unlock();
		}
	}

	public void logDebug() {
		
		readLock.lock();
		try {
			
			final SetView<Signal> intersection = Sets.intersection(primaries, backups);
			if (intersection.size() > 0) {
				logger.error("Have a non-empty intersection between primaries and backups... {}", intersection);
			}
			
			final Set<Signal> allKnown = new HashSet<>(knownSignals.values());

			final SetView<Signal> first = Sets.difference(primaries, allKnown);
			if (first.size() > 0) {
				logger.error("I have primaries I dont know where the backup is: {}", first);
			}
			
			final SetView<Signal> second = Sets.difference(backups, allKnown);
			if (second.size() > 0) {
				logger.error("I have backups I dont know where the primary is: {}", second);
			}
			
			final SetView<Signal> shouldBeEmpty = Sets.symmetricDifference(allKnown, Sets.union(primaries, backups));
			if (shouldBeEmpty.size() != 0) {
				logger.error("I know about signals I shouldnt know about: {}", shouldBeEmpty);
			}
			
		} finally {
			readLock.unlock();
		}
	}

	public void makeBackups(Address me) {
		
		writeLock.lock();
		try {
			
			for (final Signal signal : backups) {
				knownSignals.put(me, signal);
			}
			
		} finally {
			writeLock.unlock();
		}
	}

	

}
