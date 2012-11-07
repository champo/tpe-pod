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

					logger.debug("Inbound message", msg);
					
					try {
						final Object object = msg.getObject();
						if (object instanceof AnswerableMessage) {
							handleMessage(msg);
						} else if (object instanceof AnswerMessage) {
							logger.debug("Giving answer to dispatcher", object);
							dispatcher.processResponse(msg.getSrc(), (AnswerMessage) object);
						} else if (object instanceof PhaseReady) {
							phaseCounter.release();
						}
						
					} catch (final RuntimeException e) {
						System.out.println("Caught exception while handling message:\n" + e);
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
			store.addBackup((SignalData) object);
			
			try {
				dispatcher.respondTo(msg.getSrc(), answerable.getId(), null);
			} catch (final Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (object instanceof SimilarRequest) {
			final SimilarRequest request = (SimilarRequest) object;
			workerPool.request(request.getSignal(), new WorkerPool.Ready() {
				
				@Override
				public void result(Result result) {
				
					try {
						logger.debug("Returning process request", result);
						dispatcher.respondTo(msg.getSrc(), answerable.getId(), result);
					} catch (final Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
			});
		} else if (object instanceof PrimarySignal) {
			final PrimarySignal signal = (PrimarySignal) object;
			store.add(signal.getSignalData().getSignal());
			
			//TODO: ACtually send something useful
			final NotifyingFuture<Void> future = dispatcher.sendMessage(signal.getSignalData().getOtherNode(), null);
			future.setListener(new FutureListener<Void>() {
				
				@Override
				public void futureDone(Future<Void> future) {
					try {
						dispatcher.respondTo(msg.getSrc(), answerable.getId(), null);
					} catch (final Exception e) {
						e.printStackTrace();
					}
				}
			});
		}
		
	}

	public void waitForPhaseEnd(int size) throws Exception {
		dispatcher.broadcast(new PhaseReady());
		phaseCounter.acquire(size);
	}

}
