package route.visual;

import java.util.List;

import route.circuit.Circuit;

class Routing {
	
	private int iteration;
	private Circuit circuit;
	private List<Wire> wires;
	private int maxCongestion;
	
	Routing(int iteration, Circuit circuit) {
		this.initialiseData(iteration, circuit);
	}
	
	Routing(int iteration, Circuit circuit, List<Wire> wires, int maxCongestion) {
		this.initialiseData(iteration, circuit);
		this.wires = wires;
		this.maxCongestion = maxCongestion;
	}
	
	private void initialiseData(int iteration, Circuit circuit) {
		this.iteration = iteration;
		this.circuit = circuit;
	}
	
	public int getIteration() {
		return this.iteration;
	}
	
	public int getMaxCongestion() {
		return this.maxCongestion;
	}

	public List<Wire> getWires() {
		return this.wires;
	}
	
	public int getWidth() {
		return this.circuit.getWidth();
	}
	
	public int getHeight() {
		return this.circuit.getHeight();
	}
}