package ar.edu.itba.pod.legajo50453.mt;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jgroups.Message;

import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.legajo50453.message.AnswerMessage;
import ar.edu.itba.pod.legajo50453.message.AnswerableMessage;
import ar.edu.itba.pod.legajo50453.message.BackupSignal;
import ar.edu.itba.pod.legajo50453.message.MessageDispatcher;
import ar.edu.itba.pod.legajo50453.message.SimilarRequest;
import ar.edu.itba.pod.legajo50453.worker.Processor;

public class MessageConsumer implements Runnable {
	
	private final BlockingQueue<Message> queue;
	
	private final SignalStore store;
	
	private final MessageDispatcher dispatcher;
	
	private final Processor processor;
	
	public MessageConsumer(BlockingQueue<Message> queue, SignalStore store, MessageDispatcher dispatcher, Processor processor) {
		super();
		this.queue = queue;
		this.store = store;
		this.dispatcher = dispatcher;
		this.processor = processor;
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
			processor.request(request.getSignal(), new Processor.Ready() {
				
				@Override
				public void result(Result result) {
				
					try {
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
