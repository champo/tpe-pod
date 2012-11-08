package ar.edu.itba.pod.legajo50453.mt;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.jgroups.Message;
import org.jgroups.util.FutureListener;
import org.jgroups.util.NotifyingFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.legajo50453.message.AnswerMessage;
import ar.edu.itba.pod.legajo50453.message.AnswerableMessage;
import ar.edu.itba.pod.legajo50453.message.MessageDispatcher;
import ar.edu.itba.pod.legajo50453.message.PhaseReady;
import ar.edu.itba.pod.legajo50453.message.PrimarySignal;
import ar.edu.itba.pod.legajo50453.message.SignalData;
import ar.edu.itba.pod.legajo50453.message.SignalStored;
import ar.edu.itba.pod.legajo50453.message.SimilarRequest;
import ar.edu.itba.pod.legajo50453.worker.WorkerPool;

public class MessageConsumer implements Runnable {
	
	final static Logger logger = LoggerFactory.getLogger(MessageConsumer.class);
	
	private final BlockingQueue<Message> queue;
	
	private final SignalStore store;
	
	private final MessageDispatcher dispatcher;
	
	private final WorkerPool workerPool;

	private final Semaphore phaseCounter = new Semaphore(0);
	
	public MessageConsumer(BlockingQueue<Message> queue, SignalStore store, MessageDispatcher dispatcher, WorkerPool processor) {
		super();
		this.queue = queue;
		this.store = store;
		this.dispatcher = dispatcher;
		this.workerPool = processor;
	}

	@Override
	public void run() {

		try {
			
			while (true) {
				final Message msg = queue.poll(1, TimeUnit.SECONDS);
				if (msg != null) {

					try {
						final Object object = msg.getObject();
						if (object instanceof AnswerableMessage) {
							handleMessage(msg);
						} else if (object instanceof AnswerMessage) {
							dispatcher.processResponse(msg.getSrc(), (AnswerMessage) object);
						} else if (object instanceof PhaseReady) {
							logger.debug("Got phase counter, increasing from {} by one", phaseCounter.availablePermits());
							phaseCounter.release();
						}
						
					} catch (final RuntimeException e) {
						logger.error("Caught exception while handling message:", e);
					}
				}
			}
			
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
		
	}

	private void handleMessage(final Message msg) {
		
		final AnswerableMessage answerable = (AnswerableMessage) msg.getObject();
		final Object object = answerable.getPayload();
		if (object instanceof SignalData) {
			final SignalData data = (SignalData) object;
			
			logger.debug("Storing backup I got from {}", msg.getSrc());
			store.addBackup(data);
			
			notifyStore(msg, answerable, data);

		} else if (object instanceof SimilarRequest) {
			final SimilarRequest request = (SimilarRequest) object;
			workerPool.request(request.getSignal(), new WorkerPool.Ready() {
				
				@Override
				public void result(Result result) {
				
					try {
						logger.debug("Returning process request {}", result);
						dispatcher.respondTo(msg.getSrc(), answerable.getId(), result);
					} catch (final Exception e) {
						logger.error("Failed to send process request result", e);
					}
					
				}
			});
		} else if (object instanceof PrimarySignal) {
			final PrimarySignal signal = (PrimarySignal) object;
			final SignalData data = signal.getSignalData();
			
			logger.debug("Storing primary gotten from {}", msg.getSrc());
			store.addPrimary(data);
			
			notifyStore(msg, answerable, data);
		} else if (object instanceof SignalStored) {
			final SignalStored stored = (SignalStored) object;
			
			logger.debug("Updating known signal position (src {})", msg.getSrc());
			store.updateKnownSignal(stored.getOriginal(), stored.getSignal(), msg.getSrc());
			ack(msg, answerable);
		}
		
	}

	private void notifyStore(final Message msg, final AnswerableMessage answerable, final SignalData data) {
		
		if (data.getOtherNode() == null) {
			logger.debug("No other node, skiping notification");
			ack(msg, answerable);
			return;
		}
		
		logger.debug("Notifying {} of signal stored", data.getOtherNode());
		final SignalStored stored = new SignalStored(data.getSignal(), msg.getSrc());
		final NotifyingFuture<Void> future = dispatcher.sendMessage(data.getOtherNode(), stored);
		future.setListener(new FutureListener<Void>() {
			
			@Override
			public void futureDone(Future<Void> future) {
				ack(msg, answerable);
			}
		});
	}

	public void waitForPhaseEnd(int viewSize) throws Exception {
		logger.debug("Waiting for phase end... (view size {}, have {})", viewSize, phaseCounter.availablePermits());
		dispatcher.broadcast(new PhaseReady());
		phaseCounter.acquire(viewSize - 1);
	}

	private void ack(final Message msg, final AnswerableMessage answerable) {
		try {
			dispatcher.respondTo(msg.getSrc(), answerable.getId(), null);
		} catch (final Exception e) {
			logger.error("Failed to ACK to message", e);
		}
	}

}
