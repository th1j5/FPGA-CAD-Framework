package route.circuit.resource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import route.circuit.Circuit;
import route.circuit.architecture.Architecture;
import route.circuit.architecture.BlockCategory;
import route.circuit.architecture.BlockType;
import route.main.Logger;
import route.main.Logger.Location;
import route.main.Logger.Stream;
import route.visual.RouteVisualiser;
import route.visual.Wire;

public class ResourceGraph {
	private final Circuit circuit;
	private final Architecture architecture;
	
	private final int width, height;
    
	private final List<Site> sites;
	private final Site[][] siteArray;
	
	private final List<RouteNode> routeNodes;
	private List<IndexedData> indexedDataList;
	private List<RouteSwitch> switchTypesList;
	
	private final Map<RouteNodeType, List<RouteNode>> routeNodeMap;
	
	private static int SOURCE_COST_INDEX = 0;
	private static int SINK_COST_INDEX = 1;
	private static int OPIN_COST_INDEX = 2;
	private static int IPIN_COST_INDEX = 3;
	
	private static Logger statisticsLogger = new Logger();
	
	static { // initialize logger only once
		statisticsLogger.setLocation(Stream.OUT, Location.FILE);
		statisticsLogger.setLocation(Stream.ERR, Location.FILE);
	}
	
    public ResourceGraph(Circuit circuit) {
    	this.circuit = circuit;
    	this.architecture = this.circuit.getArchitecture();
    	
    	this.width = this.architecture.getWidth();
    	this.height = this.architecture.getHeight();
    	
		this.sites = new ArrayList<>();
		this.siteArray = new Site[this.width+2][this.height+2];
		
		this.routeNodes = new ArrayList<>();
		this.routeNodeMap = new HashMap<>();
		for(RouteNodeType routeNodeType: RouteNodeType.values()){
			List<RouteNode> temp = new ArrayList<>();
			this.routeNodeMap.put(routeNodeType, temp);
		}
    }
    
    public void build(){
        this.createSites();
        
		try {
			this.generateRRG(this.architecture.getRRGFile().getAbsolutePath());
		} catch (IOException e) {
			System.err.println("Problem in generating RRG: " + e.getMessage());
			e.printStackTrace();
		}
		
		this.assignNamesToSourceAndSink();
		this.connectSourceAndSinkToSite();
		
		//this.testRRG();
		
		//this.printRoutingGraph();
    }
    
    public IndexedData get_ipin_indexed_data() {
    	return this.indexedDataList.get(IPIN_COST_INDEX);
    }
    public IndexedData get_opin_indexed_data() {
    	return this.indexedDataList.get(OPIN_COST_INDEX);
    }
    public IndexedData get_source_indexed_data() {
    	return this.indexedDataList.get(SOURCE_COST_INDEX);
    }
    public IndexedData get_sink_indexed_data() {
    	return this.indexedDataList.get(SINK_COST_INDEX);
    }
    public List<IndexedData> getIndexedDataList() {
    	return this.indexedDataList;
    }
    
    private void createSites() {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        int ioCapacity = this.architecture.getIoCapacity();
        int ioHeight = ioType.getHeight();
        
        //IO Sites
        for(int i = 1; i < this.height + 1; i++) {
        	this.addSite(new Site(0, i, ioHeight, ioType, ioCapacity));
            this.addSite(new Site(this.width + 1, i, ioHeight, ioType, ioCapacity));
        }
        for(int i = 1; i < this.width + 1; i++) {
        	this.addSite(new Site(i, 0, ioHeight, ioType, ioCapacity));
            this.addSite(new Site(i, this.height + 1, ioHeight, ioType, ioCapacity));
        }
        
        for(int column = 1; column < this.width + 1; column++) {
            BlockType blockType = this.circuit.getColumnType(column);
            int blockHeight = blockType.getHeight();
            for(int row = 1; row < this.height + 2 - blockHeight; row += blockHeight) {
            	this.addSite(new Site(column, row, blockHeight, blockType, 1));
            }
        }
    }
    public void addSite(Site site) {
    	this.siteArray[site.getColumn()][site.getRow()] = site;
    	this.sites.add(site);
    }
    
    /**
     * Return the site at coordinate (x, y). If allowNull is false,
     * return the site that overlaps coordinate (x, y) but possibly
     * doesn't start at that position.
     */
    public Site getSite(int column, int row) {
        return this.getSite(column, row, false);
    }
    public Site getSite(int column, int row, boolean allowNull) {
        if(allowNull) {
            return this.siteArray[column][row];
        } else {
            Site site = null;
            int topY = row;
            while(site == null) {
                site = this.siteArray[column][topY];
                topY--;
            }
            
            return site;
        }
    }
    public List<Site> getSites(BlockType blockType) {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        List<Site> sites;
        
        if(blockType.equals(ioType)) {
            int ioCapacity = this.architecture.getIoCapacity();
            sites = new ArrayList<Site>((this.width + this.height) * 2 * ioCapacity);
            
            for(int n = 0; n < ioCapacity; n++) {
                for(int i = 1; i < this.height + 1; i++) {
                    sites.add(this.siteArray[0][i]);
                    sites.add(this.siteArray[this.width + 1][i]);
                }
                
                for(int i = 1; i < this.width + 1; i++) {
                    sites.add(this.siteArray[i][0]);
                    sites.add(this.siteArray[i][this.height + 1]);
                }
            }
        } else {
            List<Integer> columns = this.circuit.getColumnsPerBlockType(blockType);
            int blockHeight = blockType.getHeight();
            sites = new ArrayList<Site>(columns.size() * this.height);
            
            for(Integer column : columns) {
                for(int row = 1; row < this.height + 2 - blockHeight; row += blockHeight) {
                    sites.add(this.siteArray[column][row]);
                }
            }
        }
    
        return sites;
    }
    public List<Site> getSites(){
    	return this.sites;
    }
    
    /******************************
     * GENERATE THE RRG READ FROM * 
     * RRG FILE DUMPED BY VPR     *
     ******************************/
    
	private void generateRRG(String rrgFileName) throws IOException {
		System.out.println("---------------");
		System.out.println("| Process RRG |");
		System.out.println("---------------");
		
		BufferedReader reader = null;
		String line = null;
		String[] words = null;
		
		/*****************************
		 *        Indexed Data       *
		 *****************************/
		
		this.indexedDataList = new ArrayList<>();
		
		reader = new BufferedReader(new FileReader(rrgFileName.replace("rr_graph", "rr_indexed_data")));
		System.out.println("   Read " + rrgFileName.split("/")[rrgFileName.split("/").length - 1].replace("rr_graph", "rr_indexed_data"));
		
		while ((line = reader.readLine()) != null) {
			
			line = line.trim();
			if (line.length() > 0) {
				
				this.indexedDataList.add(new IndexedData(line));
			}
		}
        reader.close();
        
        for (IndexedData data : this.indexedDataList) {
        	if (data.orthoCostIndex != -1) {
        		data.setOrthoData(this.indexedDataList.get(data.orthoCostIndex));
        	}
        }
        
        //for(IndexedData data : indexedDataList) {
        //	System.out.println(data);
        //}
        
		/*****************************
		 *        Switch Types       *
		 *****************************/
		
		this.switchTypesList = new ArrayList<>();
		
		reader = new BufferedReader(new FileReader(rrgFileName.replace("rr_graph", "rr_switch_types")));
		System.out.println("   Read " + rrgFileName.split("/")[rrgFileName.split("/").length - 1].replace("rr_graph", "rr_switch_types"));
		
		while ((line = reader.readLine()) != null) {
			
			line = line.trim();
			if (line.length() > 0) {
				
				this.switchTypesList.add(new RouteSwitch(line));
			}
		}
		
        reader.close();
        
		//for(SwitchType type : switchTypesList) {
		//	System.out.println(type);
		//}
		
		/*****************************
		 *        Route Nodes        *
		 *****************************/
		
		RouteNode routeNode = null;
		String currentPort = null;
		int portIndex = -1;
		IndexedData data = null;
		
		reader = new BufferedReader(new FileReader(rrgFileName.replace("rr_graph", "rr_nodes")));
		System.out.println("   Read " + rrgFileName.split("/")[rrgFileName.split("/").length - 1].replace("rr_graph", "rr_nodes"));
		
		while ((line = reader.readLine()) != null) {
        	
			line = line.trim();
			if(line.length() > 0){
        		
        		words = line.split(";");
        		
        		int index = Integer.parseInt(words[0]);
        		String type = words[1];
        		String name = words[2];
        		int xlow = Integer.parseInt(words[3]);
        		int xhigh = Integer.parseInt(words[4]);
        		int ylow = Integer.parseInt(words[5]);
        		int yhigh = Integer.parseInt(words[6]);
        		int n = Integer.parseInt(words[7]);
        		
        		if(n == 0){//New global block, reset data
        			currentPort = null;
        			portIndex = -1;
        		}
        		
        		int cap = Integer.parseInt(words[8]);
        		float r = Float.parseFloat(words[9]);
        		float c = Float.parseFloat(words[10]);
        		
        		int cost_index = Integer.parseInt(words[11]);
        		data = this.indexedDataList.get(cost_index);
        		
        		int numChildren = Integer.parseInt(words[12]);
        		
        		switch (type) {
        			case "SOURCE":        				
        				//Assertions
        				assert name.equals("-");
        				assert r == 0;
        				assert c == 0;
        				
        				routeNode = new Source(index, xlow, xhigh, ylow, yhigh, n, cap, data, numChildren);
        				
        				break;
        			case "SINK":        				
        				//Assertions
        				assert name.equals("-");
        				assert r == 0;
        				assert c == 0;
        				
        				routeNode = new Sink(index, xlow, xhigh, ylow, yhigh, n, cap, data, numChildren);
        				
        				break;
        			case "IPIN":
        				//Assertions
        				assert cap == 1;
        				assert r == 0;
        				assert c == 0;
        				
        				if(currentPort == null){
        					currentPort = name;
        					portIndex = 0;
        				}else if(!currentPort.equals(name)){
        					currentPort = name;
        					portIndex = 0;
        				}
        				
        				routeNode = new Ipin(index, xlow, xhigh, ylow, yhigh, n, name, portIndex, data, numChildren);
        				
        				portIndex += 1;
        				
        				break;
        			case "OPIN":        				
        				//Assertions
        				assert cap == 1;
        				assert r == 0;
        				assert c == 0;
        				
        				if(currentPort == null){
        					currentPort = name;
        					portIndex = 0;
        				}else if(!currentPort.equals(name)){
        					currentPort = name;
        					portIndex = 0;
        				}
        				
        				routeNode = new Opin(index, xlow, xhigh, ylow, yhigh, n, name, portIndex, data, numChildren);
        				
        				portIndex += 1;
        				
        				break;
        			case "CHANX":        				
        				//Assertions
        				assert name.equals("-");
        				assert cap == 1;
        				
        				routeNode = new Chanx(index, xlow, xhigh, ylow, yhigh, n, r, c, data, numChildren);
        				
        				break;
        			case "CHANY":        				
        				//Assertions
        				assert name.equals("-");
        				assert cap == 1;
        				
        				routeNode = new Chany(index, xlow, xhigh, ylow, yhigh, n, r, c, data, numChildren);
        				
        				break;
        			default:
        				System.out.println("Unknown type: " + type);
        				break;
        		}
        		this.addRouteNode(routeNode);
        	}
		}
		
		reader.close();
		
		/*****************************
		 *         Children          *
		 *****************************/
		reader = new BufferedReader(new FileReader(rrgFileName.replace("rr_graph", "rr_children")));
		System.out.println("   Read " + rrgFileName.split("/")[rrgFileName.split("/").length - 1].replace("rr_graph", "rr_children"));
		
		while ((line = reader.readLine()) != null) {
			
			line = line.trim();
        	if(line.length() > 0){
        		
        		words = line.split(";");
        		
        		RouteNode parent = this.routeNodes.get(Integer.parseInt(words[0]));
        		
        		int numChildren = Integer.parseInt(words[1]);
        		for(int index = 0; index < numChildren; index++) {
        			RouteNode child = this.routeNodes.get(Integer.parseInt(words[index+2]));
        			parent.setChild(index, child);
        		}
        	}
		}
		
		reader.close();
		
		/*****************************
		 *         Switches          *
		 *****************************/
		
		reader = new BufferedReader(new FileReader(rrgFileName.replace("rr_graph", "rr_switches")));
		System.out.println("   Read " + rrgFileName.split("/")[rrgFileName.split("/").length - 1].replace("rr_graph", "rr_switches"));
		
		while ((line = reader.readLine()) != null) {
		
			line = line.trim();
			if(line.length() > 0){
				
				words = line.split(";");
				
				RouteNode parent = this.routeNodes.get(Integer.parseInt(words[0]));
				int numChildren = parent.numChildren;
				
				for(int index = 0; index < numChildren; index++) {
					RouteSwitch routeSwitch = this.switchTypesList.get(Integer.parseInt(words[index+1]));
					parent.setSwitchType(index, routeSwitch);
				}
			}
		}
		
		reader.close();
		
		for(RouteNode node : this.routeNodes) {
			for(int i = 0; i < node.numChildren; i++) {
				RouteNode child = node.children[i];
				RouteSwitch routeSwitch = node.switches[i];
				
				child.setDelay(routeSwitch);
			}
		}
		for(RouteNode node : this.routeNodeMap.get(RouteNodeType.SOURCE)) {
			Source source = (Source) node;
			source.setDelay(null);
		}
		
		System.out.println();
	}
	private void assignNamesToSourceAndSink() {
		for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SOURCE)){
			Source source = (Source) routeNode;
			source.setName();
		}
		
		for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.IPIN)){
			Ipin ipin = (Ipin) routeNode;
			ipin.setSinkName();
		}
	}
    private void connectSourceAndSinkToSite() {
    	for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SOURCE)){
			Source source = (Source) routeNode;
			
			Site site = this.getSite(source.xlow, source.ylow);
			if(site.addSource((Source)routeNode) == false) {
				System.err.println("Unable to add " + routeNode + " as source to " + site);
			}
		}
    	for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SINK)){
			Sink sink = (Sink) routeNode;
			
			Site site = this.getSite(sink.xlow, sink.ylow);
			if(site.addSink((Sink)routeNode) == false) {
				System.err.println("Unable to add " + routeNode + " as sink to " + site);
			}
		}
    }
	
	private void addRouteNode(RouteNode routeNode) {
		assert routeNode.index == this.routeNodes.size();
		
		this.routeNodes.add(routeNode);
		this.routeNodeMap.get(routeNode.type).add(routeNode);
	}
	public List<RouteNode> getRouteNodes() {
		return this.routeNodes;
	}
	public int numRouteNodes() {
		return this.routeNodes.size();
	}
	public int numRouteNodes(RouteNodeType type) {
		if(this.routeNodeMap.containsKey(type)) {
			return this.routeNodeMap.get(type).size();
		} else {
			return 0;
		}
	}
	
	@Override
	public String toString() {
		String s = new String();
		
		s+= "The system has " + this.numRouteNodes() + " rr nodes:\n";
		
		for(RouteNodeType type : RouteNodeType.values()) {
			s += "\t" + type + "\t" + this.numRouteNodes(type) + "\n";
		}
		return s;
	}
	
	/********************
	 * Routing statistics
	 ********************/
	public int totalWireLength() { // total length inside FPGA, each routeNode only counted onces
		int totalWireLength = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					totalWireLength += routeNode.wireLength();
				}
			}
		}
		return totalWireLength;
	}
	public int occupiedTotalWireLength() { // 
		int totalWireLength = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					totalWireLength += routeNode.wireLength() * routeNode.routeNodeData.occupation;
				}
			}
		}
		return totalWireLength;
	}
	public void logCongestionHeatMap(int iteration) {
		statisticsLogger.println(""+iteration); // 1 number on the line means the iteration round 
//		statisticsLogger.println("Heatmap of wires");
//		statisticsLogger.println("index | occupation");
//		statisticsLogger.println("-------------------------");
		for(RouteNode routeNode : this.routeNodes) { 
			//if(routeNode.isWire && routeNode.overUsed()) { // only output overused nodes, not the occupied ones
			if(routeNode.isWire && routeNode.used()) { // output all used nodes
				statisticsLogger.println(routeNode.index + "," + routeNode.routeNodeData.occupation);
			}
		}
	}
	
	public void addRoutingToVisualiser(int iteration, RouteVisualiser visualiser) {
		List<Wire> wires = new ArrayList<>();
		int maxCongestion = 0;
		for (RouteNode routeNode : this.routeNodes) {
			if (routeNode.isWire && routeNode.used()) {
				int occupation = routeNode.routeNodeData.occupation;
				if (occupation > maxCongestion) {
					maxCongestion = occupation;
				}
				Wire wire = new Wire(routeNode.xlow,routeNode.xhigh,routeNode.ylow,routeNode.yhigh,routeNode.routeNodeData.occupation);
				wires.add(wire);
			}
		}
		//we need to sort the wires, such that the lower congested ones are on the bottom, in order to draw higher congestion later
		Collections.sort(wires);
		visualiser.addRouting(iteration, wires, maxCongestion);
	}
	
	public int wireSegmentsUsed() {
		int wireSegmentsUsed = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					wireSegmentsUsed++;
				}
			}
		}
		return wireSegmentsUsed;
	}
	public void sanityCheck() {
		for(Site site:this.getSites()) {
			site.sanityCheck();
		}
	}
	public void printRoutingGraph() {
		for(RouteNode node : this.getRouteNodes()) {
			System.out.println(node);
			for (RouteNode child : node.children) {
				System.out.println("\t" + child);
			}
			System.out.println();
		}
	}

	public void printWireUsage() {
		System.out.println("-------------------------------------------------------------------------------");
		System.out.println("|                              WIRELENGTH STATS                               |");
		System.out.println("-------------------------------------------------------------------------------");
		System.out.println("Total wirelength: " + this.circuit.getResourceGraph().totalWireLength());
		System.out.println("Total occupied wirelength: " + this.circuit.getResourceGraph().occupiedTotalWireLength());
		System.out.println("Wire segments: " + this.circuit.getResourceGraph().wireSegmentsUsed());
		System.out.println("Maximum net length: " + this.circuit.maximumNetLength());
		System.out.println();
		
		int numL4Wires = 0, numL16Wires = 0;
		int numUsedL4Wires = 0, numUsedL16Wires = 0;
		
		int wireLengthL4 = 0, wireLengthL16 = 0;
		int usedWireLengthL4 = 0, usedWireLengthL16 = 0;
		
		
		for(RouteNode node : this.routeNodes) {
			if(node.type == RouteNodeType.CHANX) {
				if (node.indexedData.length == 4) {
					numL4Wires++;
					wireLengthL4 += node.wireLength();
					if (node.used()) {
						numUsedL4Wires++;
						usedWireLengthL4 += node.wireLength();
					}
				} else if (node.indexedData.length == 16) {
					numL16Wires++;
					wireLengthL16 += node.wireLength();
					if (node.used()) {
						numUsedL16Wires++;
						usedWireLengthL16 += node.wireLength();
					}
				} else {
					System.err.println("Unknown Wire-length: " + node.indexedData.length);
				}
			} else if (node.type == RouteNodeType.CHANY) {
				if (node.indexedData.length == 4) {
					numL4Wires++;
					wireLengthL4 += node.wireLength();
					if (node.used()) {
						numUsedL4Wires++;
						usedWireLengthL4 += node.wireLength();
					}
				} else if (node.indexedData.length == 16) {
					numL16Wires++;
					wireLengthL16 += node.wireLength();
					if (node.used()) {
						numUsedL16Wires++;
						usedWireLengthL16 += node.wireLength();
					}
				} else {
					System.err.println("Unknown Wire-length: " + node.indexedData.length);
				}
			}
		}
		double averageLengthOfL4Wires = (double)wireLengthL4 / numL4Wires;
		double averageLengthOfL16Wires = (double)wireLengthL16 / numL16Wires;
		
		System.out.printf("Length 4  (%5.2f) wires: %8d of %8d | %5.2f%% => Wire-length: %8d\n",
				averageLengthOfL4Wires, 
				numUsedL4Wires, 
				numL4Wires, 
				100.0 * numUsedL4Wires/numL4Wires, 
				usedWireLengthL4);
		System.out.printf("Length 16 (%5.2f) wires: %8d of %8d | %5.2f%% => Wire-length: %8d\n", 
				averageLengthOfL16Wires,
				numUsedL16Wires,
				numL16Wires,
				100.0 * numUsedL16Wires/numL16Wires,
				usedWireLengthL16);
		System.out.println("-------------------------------------------------------------------------------");
		System.out.println();
		
		System.out.println("-------------------------------------------------------------------------------");
		System.out.printf("L4 Wirelength: %8d\n", usedWireLengthL4);
		System.out.printf("L16 Wirelength: %8d\n", usedWireLengthL16);
		System.out.printf("L4 Usage: %5.2f\n", 100.0 * numUsedL4Wires/numL4Wires);
		System.out.printf("L16 Usage: %5.2f\n", 100.0 * numUsedL16Wires/numL16Wires);
		System.out.println("-------------------------------------------------------------------------------");
	}
}
