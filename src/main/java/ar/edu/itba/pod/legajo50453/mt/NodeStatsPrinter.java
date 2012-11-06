/**
 * 
 */
package ar.edu.itba.pod.legajo50453.mt;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import ar.edu.itba.pod.api.NodeStats;

/**
 * @author champo
 *
 */
public class NodeStatsPrinter {
	
	private final NodeStats stats;
	
	public NodeStatsPrinter(NodeStats stats) {
		super();
		this.stats = stats;
	}

	@Override
	public String toString() {
		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		stats.print(new PrintStream(outputStream));
		
		return new String(outputStream.toByteArray());
	}

}
