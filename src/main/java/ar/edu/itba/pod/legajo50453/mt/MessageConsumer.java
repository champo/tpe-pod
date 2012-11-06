package ar.edu.itba.pod.legajo50453.mt;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jgroups.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.legajo50453.message.AnswerMessage;
import ar.edu.itba.pod.legajo50453.message.AnswerableMessage;
import ar.edu.itba.pod.legajo50453.message.BackupSignal;
import ar.edu.itba.pod.legajo50453.message.MessageDispatcher;
import ar.edu.itba.pod.legajo50453.message.SimilarRequest;
import ar.edu.itba.pod.legajo50453.worker.WorkerPool;

public class MessageConsumer implements Runnable {
	
	final static Logger logger = LoggerFactory.getLogger(MessageConsumer.class);
	
	private final BlockingQueue<Message> queue;
	
	private final SignalStore store;
	
	private final MessageDispatcher dispatcher;
	
	private final WorkerPool workerPool;
	
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
		if (object instanceof BackupSignal) {
			store.addBackup((BackupSignal) object);
			
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
		}
		
	}
	
	
	

}
