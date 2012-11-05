/**
 * 
 */
package ar.edu.itba.pod.legajo50453;

import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import ar.edu.itba.pod.legajo50453.mt.Node;

/**
 * @author champo
 *
 */
public class Server {

	public static void main(String[] args) {
		
		if (args.length != 2) {
			System.out.println("Come on bro, give me the right args");
			return;
		}
		
		int port;
		int threads;
		
		try {
			port = Integer.parseInt(args[0]);
			threads = Integer.parseInt(args[1]);
		} catch (NumberFormatException e) {
			System.out.println("Invalid args, really?");
			return;
		}
		
		try {
			Registry registry = LocateRegistry.createRegistry(port);

			Node processor = new Node(threads);
			UnicastRemoteObject.exportObject(processor, port);
			
			registry.bind("SPNode", processor);
			registry.bind("SignalProcessor", processor);
			
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (AlreadyBoundException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

}
