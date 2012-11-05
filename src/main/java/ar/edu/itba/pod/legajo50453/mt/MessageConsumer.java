package ar.edu.itba.pod.legajo50453.mt;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jgroups.Message;

import ar.edu.itba.pod.legajo50453.message.AnswerMessage;
import ar.edu.itba.pod.legajo50453.message.AnswerableMessage;
import ar.edu.itba.pod.legajo50453.message.BackupSignal;
import ar.edu.itba.pod.legajo50453.message.MessageDispatcher;

public class MessageConsumer implements Runnable {
	
	private final BlockingQueue<Message> queue;
	
	private final SignalStore store;
	
	private final MessageDispatcher dispatcher;
	
	public MessageConsumer(BlockingQueue<Message> queue, SignalStore store, MessageDispatcher dispatcher) {
		super();
		this.queue = queue;
		this.store = store;
		this.dispatcher = dispatcher;
	}

	@Override
	public void run() {

		try {
			
			while (true) {
				final Message msg = queue.poll(1, TimeUnit.SECONDS);
				if (msg != null) {
					
					
					final Object object = msg.getObject();
					if (object instanceof AnswerableMessage) {
						handleMessage(msg);
					} else if (object instanceof AnswerMessage) {
						dispatcher.processResponse(msg.getSrc(), (AnswerMessage) object);
					}
				}
			}
			
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
		
	}

	private void handleMessage(Message msg) {
		
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
		}
		
	}
	
	
	

}