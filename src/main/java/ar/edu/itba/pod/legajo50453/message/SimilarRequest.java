/**
 * 
 */
package ar.edu.itba.pod.legajo50453.message;

import java.io.Serializable;

import ar.edu.itba.pod.api.Signal;

/**
 * @author champo
 *
 */
public class SimilarRequest implements Serializable {

	private static final long serialVersionUID = -2615886564458131472L;
	
	private final Signal signal;
	
	/**
	 * @param signal
	 */
	public SimilarRequest(Signal signal) {
		this.signal = signal;
	}

	/**
	 * @return the signal
	 */
	public Signal getSignal() {
		return signal;
	}

}
