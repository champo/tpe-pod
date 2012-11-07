/**
 * 
 */
package ar.edu.itba.pod.legajo50453.message;

import java.io.Serializable;


/**
 * @author champo
 *
 */
public class PrimarySignal implements Serializable {

	private static final long serialVersionUID = 6815093081466846999L;
	
	private final SignalData signalData;

	public PrimarySignal(SignalData signalData) {
		this.signalData = signalData;
	}

	public SignalData getSignalData() {
		return signalData;
	}

}
