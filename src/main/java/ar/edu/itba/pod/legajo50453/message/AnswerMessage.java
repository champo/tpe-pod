package ar.edu.itba.pod.legajo50453.message;

import java.io.Serializable;

public class AnswerMessage implements Serializable {

	private static final long serialVersionUID = 4676276011580393753L;

	private final long id;
	
	private final Serializable payload;

	public AnswerMessage(long id, Serializable payload) {
		super();
		this.id = id;
		this.payload = payload;
	}
	
	public long getId() {
		return id;
	}
	
	public Serializable getPayload() {
		return payload;
	}
	
}
