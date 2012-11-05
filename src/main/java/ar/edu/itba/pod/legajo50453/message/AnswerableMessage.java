package ar.edu.itba.pod.legajo50453.message;

import java.io.Serializable;

public class AnswerableMessage implements Serializable {

	private static final long serialVersionUID = 5067895203221119657L;

	private final Serializable payload;
	
	private final long id;

	public AnswerableMessage(long id, Serializable obj) {
		this.id = id;
		this.payload = obj;
	}
	
	public long getId() {
		return id;
	}
	
	public Serializable getPayload() {
		return payload;
	}

}
