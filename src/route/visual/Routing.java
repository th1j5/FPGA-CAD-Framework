package route.visual;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

import route.circuit.Circuit;
import route.circuit.block.GlobalBlock;
import route.circuit.resource.RouteNode;

class Routing {
	
	private String name; //really needed?
	private int iteration;
	private Circuit circuit;
	private List<RouteNode> routeNodeList;
	private int maxCongestion;
	//private int numWires;
	
	Routing(int iteration, Circuit circuit) {
		this.initialiseData(iteration, circuit);
		
		//do some stuff
	}
	
	Routing(int iteration, Circuit circuit, List<RouteNode> routeNodeList, int maxCongestion) {
		this.initialiseData(iteration, circuit);
		this.routeNodeList = routeNodeList;
		this.maxCongestion = maxCongestion;
	}
	
	//some other constructors
	
	private void initialiseData(int iteration, Circuit circuit) {
		this.name = circuit.getName();
		this.iteration = iteration;
		this.circuit = circuit;
	}
	
	public int getIteration() {
		return this.iteration;
	}
	
	public int getMaxCongestion() {
		return this.maxCongestion;
	}

	public List<RouteNode> getWires() {
		return this.routeNodeList;
	}
	
	public int getWidth() {
		return this.circuit.getWidth();
	}
	
	public int getHeight() {
		return this.circuit.getHeight();
	}
}