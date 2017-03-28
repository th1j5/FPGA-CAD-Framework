package place.placers.analytical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import place.circuit.block.GlobalBlock;
import place.placers.analytical.AnalyticalAndGradientPlacer.NetBlock;

public class HardblockConnectionLegalizer{

	private double[] linearX, linearY;
    private int[] legalX, legalY;
    
    private Block[] blocks;
    private Net[] nets;
    
    private Bucket[] buckets;
    
    private final int gridWidth, gridHeigth;
    
    private int firstRow, rowRepeat;
    private int firstColumn, columnRepeat;
    private int numRows, numColumns;

	HardblockConnectionLegalizer(
			double[] linearX,
			double[] linearY,
			int[] legalX, 
			int[] legalY, 
			int[] heights,
			int gridWidth,
			int gridHeight,
			List<AnalyticalAndGradientPlacer.Net> placerNets,
			Map<GlobalBlock, NetBlock> blockIndexes){

		this.linearX = linearX;
		this.linearY = linearY;

		this.legalX = legalX;
		this.legalY = legalY;
		
		this.gridWidth = gridWidth;
		this.gridHeigth = gridHeight;

		//Make all objects
		this.blocks = new Block[legalX.length];
		this.nets = new Net[placerNets.size()];

		for(int i = 0; i < placerNets.size(); i++){
			this.nets[i] = new Net(i);
		}
		for(int i = 0; i < legalX.length; i++){
			int offset = (1- heights[i]) / 2;
			this.blocks[i] = new Block(i, offset);
		}
		
		//Connect objects
		for(int i = 0; i < placerNets.size(); i++){
			Net legalizerNet = this.nets[i];
			for(NetBlock block:placerNets.get(i).blocks){
				Block legalizerBlock = this.blocks[block.blockIndex];
				legalizerNet.addBlock(legalizerBlock);
				legalizerBlock.addNet(legalizerNet);
			}
		}
	}
	public void legalizeBlockType(int firstBlockIndex, int lastBlockIndex, int firstColumn, int columnRepeat, int blockHeight){
		boolean printTestOutput = false;

		this.firstRow = 1;
		this.rowRepeat = blockHeight;

		this.firstColumn = firstColumn;
		this.columnRepeat = columnRepeat;

        this.numColumns = (int) Math.floor((this.gridWidth - this.firstColumn) / this.columnRepeat + 1);
        this.numRows = (int) Math.floor(this.gridHeigth / this.rowRepeat);
        
        //Make the buckets for the current hard block type
        this.buckets = new Bucket[this.numColumns];
        for(int b = 0; b < this.numColumns; b++){
			this.buckets[b] = new Bucket(this.numRows, this.rowRepeat, this.firstColumn + b * this.columnRepeat);
		}

        //Update the coordinates of the blocks and fill the buckets
        for(Block block:this.blocks){
        	block.setLinearCoordinates(this.linearX[block.index], this.linearY[block.index] + block.offset);
        	block.setLegalCoordinates(this.legalX[block.index], this.legalY[block.index] + block.offset);
        }
		for(int i = firstBlockIndex; i < lastBlockIndex; i++){
			Block block = this.blocks[i];
			
			int columnIndex = (int) Math.round(Math.max(Math.min((block.linearX - this.firstColumn) / this.columnRepeat, this.numColumns - 1), 0));
			int rowIndex =    (int) Math.round(Math.max(Math.min((block.linearY - this.firstRow)    / this.rowRepeat,    this.numRows - 1)   , 0));
			block.setLegalCoordinates(this.firstColumn + this.columnRepeat * columnIndex, this.firstRow + this.rowRepeat * rowIndex);
			
			this.buckets[columnIndex].addBlock(block);
		}

		//Find buckets with empty positions
		HashSet<Bucket> availableBuckets = new HashSet<Bucket>();
		for(Bucket bucket:this.buckets){
			if(bucket.usedPos() < bucket.numPos()){
				availableBuckets.add(bucket);
			}
		}
		
		if(printTestOutput){
			//Test functionality
	        System.out.print("The system has " + this.buckets.length + " buckets with size " + this.buckets[0].numPos() + ": ");
	        for(Bucket bucket:this.buckets){
	        	System.out.print(bucket.coordinate + " ");
	        }
	        System.out.println();
			System.out.println("Status of the buckets");
			for(Bucket bucket:this.buckets){
				System.out.println("\t" + bucket);
			}
			System.out.println("Largest bucket has " + this.largestBucket().usedPos() + " blocks");
			System.out.println("Buckets with empty positions");
			for(Bucket bucket:availableBuckets){
				System.out.println("\t" + bucket);
			}
		}

		//Distribute blocks along the buckets
		for(Net net:this.nets){
			net.initializeConnectionCost();
		}
		
		Bucket largestBucket = this.largestBucket();
		while(largestBucket.usedPos() > largestBucket.numPos()){

			Block bestBlock = null;
			Bucket bestBucket = null;
			double minimumIncrease = Double.MAX_VALUE;
			
			for(Block block: largestBucket.blocks){
				double currentCost = block.horizontalConnectionCost();
				for(Bucket bucket:availableBuckets){

					block.legalX = bucket.coordinate;
					double newCost = block.horizontalConnectionCost(largestBucket.coordinate, bucket.coordinate);
					block.legalX = largestBucket.coordinate;

					double increase = newCost - currentCost;
					if(increase < minimumIncrease){
						minimumIncrease = increase;
						bestBlock = block;
						bestBucket = bucket;
					}
				}
			}

			largestBucket.removeBlock(bestBlock);
			bestBucket.addBlock(bestBlock);
			
			bestBlock.legalX = bestBucket.coordinate;
			bestBlock.updateHorizontalConnectionCost();
			
			if(bestBucket.usedPos() == bestBucket.numPos()){
				availableBuckets.remove(bestBucket);
			}
			
			largestBucket = this.largestBucket();
		}
		
		if(printTestOutput){
			//Test functionality
			System.out.println("Status of the buckets");
			for(Bucket bucket:this.buckets){
				System.out.println("\t" + bucket);
			}
		}

		//Spread the blocks in the buckets
		for(Bucket bucket:this.buckets){
			bucket.legalize();
		}
		
		//////////////////////////////////////////////////////////////
		/////////////////////////// ANNEAL ///////////////////////////
		//////////////////////////////////////////////////////////////
		
		//Initialize data
		for(Block block:this.blocks){
			if(block.hasSite()){
				block.removeSite();
			}
		}
		Site[][] annealSites = new Site[this.numColumns][this.numRows];
		for(int c = 0; c < this.numColumns; c++){
			int column = this.firstColumn + c * this.columnRepeat;
			for(int r = 0; r < this.numRows; r++){
				int row = this.firstRow + r * this.rowRepeat;
				
				annealSites[c][r] = new Site(column, row);
			}
		}
		
		Block[] annealBlocks = new Block[lastBlockIndex - firstBlockIndex];
		for(int i = firstBlockIndex; i < lastBlockIndex; i++){
			annealBlocks[i - firstBlockIndex] = this.blocks[i];
		}
		for(Block block:annealBlocks){
			int columnIndex = (block.legalX - this.firstColumn) / this.columnRepeat;
			int rowIndex    = (block.legalY - this.firstRow)    / this.rowRepeat;
			
			Site site = annealSites[columnIndex][rowIndex];
			
			site.setBlock(block);
			block.setSite(site);
		}
		
		
		this.anneal(annealBlocks, annealSites);
		
		this.updateLegal();
	}

	private Bucket largestBucket(){
		Bucket result = this.buckets[0];
		for(Bucket bucket:this.buckets){
			if(bucket.usedPos() > result.usedPos()){
				result = bucket;
			}
		}
		return result;
	}
	
    /////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////// ANNEAL ///////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////
	private void anneal(Block[] annealBlocks, Site[][] annealSites){
		int numBlocks = annealBlocks.length;
		int numColumns = annealSites.length;
		int numRows = annealSites[0].length;

		Random random = new Random(100);
		
		double temperature = 3000;

		int temperatureSteps = 500;
		int stepsPerTemperature = numBlocks;
		
		for(int temperatureStep = 0; temperatureStep < temperatureSteps; temperatureStep++){
			
			temperature *= 0.98;
			
			for(int i = 0; i < stepsPerTemperature; i++){
				
				Block block1 = annealBlocks[random.nextInt(numBlocks)];
				Site site1 = block1.getSite();
				
				Site site2 = annealSites[random.nextInt(numColumns)][random.nextInt(numRows)];
				while(site1.equals(site2)){
					site2 = annealSites[random.nextInt(numColumns)][random.nextInt(numRows)];
				}
				Block block2 = site2.getBlock();
				
				double oldCost = block1.horizontalConnectionCost() + block1.verticalConnectionCost();
				if(block2 != null){
					oldCost += block2.horizontalConnectionCost();
					oldCost += block2.verticalConnectionCost();
				}
				
				block1.legalX = site2.column;
				block1.legalY = site2.row;
				if(block2 != null){
					block2.legalX = site1.column;
					block2.legalY = site1.row;
				}

				double newCost = block1.horizontalConnectionCost(site1.column, site2.column) + block1.verticalConnectionCost(site1.row, site2.row);
				
				if(block2 != null){
					newCost += block2.horizontalConnectionCost(site2.column, site1.column);
					newCost += block2.verticalConnectionCost(site2.row, site1.row);
				}
				
				double deltaCost = newCost - oldCost;
				
 				if(deltaCost <= 0 || random.nextDouble() < Math.exp(-deltaCost / temperature)) {
 					site1.removeBlock();
 					block1.removeSite();
 					
 					if(block2 != null){
 						site2.removeBlock();
 						block2.removeSite();
 					}
 					
 					site2.setBlock(block1);
 					block1.setSite(site2);
 
 					if(block2 != null){
 						site1.setBlock(block2);
 						block2.setSite(site1);
 					}
 					
 					block1.updateHorizontalConnectionCost();
 					block1.updateVerticalConnectionCost();
 					
 					if(block2 != null){
 						block2.updateHorizontalConnectionCost();
 						block2.updateVerticalConnectionCost();
 					}
 				}else{
 					block1.legalX = site1.column;
 					block1.legalY = site1.row;
 					
 					if(block2 != null){
 						block2.legalX = site2.column;
 						block2.legalY = site2.row;
 					}
 				}
			}
		}
	}
	
    private void updateLegal(){
    	for(Block block:this.blocks){
    		this.legalX[block.index] = block.legalX;
    		this.legalY[block.index] = block.legalY - block.offset;
    	}
    }
	
    /////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////// BLOCK ////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////
	class Block{
		final int index, offset;
		
		double linearX, linearY;
		int legalX, legalY;

		final List<Net> nets;
		
		private Site site;
		
		Block(int index, int offset){
			this.index = index;
			this.offset = offset;
			
			this.nets = new ArrayList<Net>();
			
			this.site = null;
		}
		void setLinearCoordinates(double linearX, double linearY){
			this.linearX = linearX;
			this.linearY = linearY;
		}
		void setLegalCoordinates(int legalX, int legalY){
			this.legalX = legalX;
			this.legalY = legalY;
		}
		
		void addNet(Net net){
			if(!this.nets.contains(net)){
				this.nets.add(net);
			}
		}
		
		//// Connection cost ////
		void initializeConnectionCost(){
			for(Net net:this.nets){
				net.initializeConnectionCost();
			}
		}
		
		// Horizontal
		void updateHorizontalConnectionCost(){
			for(Net net:this.nets){
				net.updateHorizontalConnectionCost();
			}
		}
		double horizontalConnectionCost(){
			double cost = 0.0;
			
			for(Net net:this.nets){
				cost += net.horizontalConnectionCost();
			}
			
			return cost;
		}
		double horizontalConnectionCost(int oldX, int newX){
			double cost = 0.0;
			
			for(Net net:this.nets){
				cost += net.horizontalConnectionCost(oldX, newX);
			}
			
			return cost;
		}
		
		// Vertical
		void updateVerticalConnectionCost(){
			for(Net net:this.nets){
				net.updateVerticalConnectionCost();
			}
		}
		double verticalConnectionCost(){
			double cost = 0.0;
			
			for(Net net:this.nets){
				cost += net.verticalConnectionCost();
			}
			
			return cost;
		}
		double verticalConnectionCost(int oldY, int newY){
			double cost = 0.0;
			
			for(Net net:this.nets){
				cost += net.verticalConnectionCost(oldY, newY);
			}
			
			return cost;
		}
		
		//Displacement cost
		double displacementCost(){
			return (this.linearX - this.legalX) * (this.linearX - this.legalX) + (this.linearY - this.legalY) * (this.linearY - this.legalY);
		}
		
    	public String toString(){
    		return String.format("%.2f  %.2f", this.linearX, this.linearY);
    	}
    	
    	//Site
    	void setSite(Site site){
    		if(this.site == null){
    			this.site = site;
    		}else{
    			System.out.println("This block already has a site");
    		}
    	}
    	Site getSite(){
    		return this.site;
    	}
    	void removeSite(){
    		if(this.site != null){
    			this.site = null;
    		}else{
    			System.out.println("This block has no site");
    		}
    	}
    	boolean hasSite(){
    		return this.site != null;
    	}
	}
    public static class BlockComparator {
        public static Comparator<Block> HORIZONTAL = new Comparator<Block>() {
            @Override
            public int compare(Block b1, Block b2) {
                return Double.compare(b1.linearX, b2.linearX);
            }
        };
        public static Comparator<Block> VERTICAL = new Comparator<Block>() {
            @Override
            public int compare(Block b1, Block b2) {
                return Double.compare(b1.linearY, b2.linearY);
            }
        };
    }
    
    /////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////// NET //////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////
	class Net{
		final int index;
		final List<Block> blocks;

		double netWeight;
		int minX, maxX;
		int minY, maxY;
		
		int tempMinX, tempMaxX;
		int tempMinY, tempMaxY;

		Net(int index){
			this.index = index;
			this.blocks = new ArrayList<Block>();
		}

		void addBlock(Block block){
			this.blocks.add(block);
		}

		//// Connection cost ////
		void initializeConnectionCost(){
			this.netWeight = AnalyticalAndGradientPlacer.getWeight(this.blocks.size());
			
			this.minX = this.getMinX();
			this.maxX = this.getMaxX();
			
			this.minY = this.getMinY();
			this.maxY = this.getMaxY();
		}
		
		// Horizontal
		double horizontalConnectionCost(){
			return (this.maxX - this.minX + 1) * this.netWeight;
		}
		double horizontalConnectionCost(int oldX, int newX){
            this.tempMinX = this.updateMinX(oldX, newX, this.minX);
            this.tempMaxX = this.updateMaxX(oldX, newX, this.maxX);
            return (this.tempMaxX - this.tempMinX + 1) * this.netWeight;
		}
		void updateHorizontalConnectionCost(){
			this.minX = this.tempMinX;
			this.maxX = this.tempMaxX;
		}
		int updateMinX(int oldX, int newX, int minX){
			if(newX <= minX){
            	return newX;
			}else if(oldX == minX){
            	return this.getMinX();
            }else{
            	return minX;
            }
		}
		int updateMaxX(int oldX, int newX, int maxX){
            if(newX >= maxX){
            	return newX;
            }else if(oldX == maxX){
            	return this.getMaxX();
            }else{
            	return maxX;
            }
		}
		int getMinX(){
			Block initialBlock = this.blocks.get(0);
	        int minX = initialBlock.legalX;
	        for(Block block:this.blocks){
	            if(block.legalX < minX) {
	                minX = block.legalX;
	            }
	        }
	        return minX;
		}
		int getMaxX(){
			Block initialBlock = this.blocks.get(0);
	        int maxX = initialBlock.legalX;
	        for(Block block:this.blocks){
	            if(block.legalX > maxX) {
	                maxX = block.legalX;
	            }
	        }
	        return maxX;
		}
		
		
		// Vertical
		double verticalConnectionCost(){
			return (this.maxY - this.minY + 1) * this.netWeight;
		}
		double verticalConnectionCost(int oldY, int newY){
            this.tempMinY = this.updateMinY(oldY, newY, this.minY);
            this.tempMaxY = this.updateMaxY(oldY, newY, this.maxY);
            return (this.tempMaxY - this.tempMinY + 1) * this.netWeight;
		}
		void updateVerticalConnectionCost(){
			this.minY = this.tempMinY;
			this.maxY = this.tempMaxY;
		}
		int updateMinY(int oldY, int newY, int minY){
			if(newY <= minY){
            	return newY;
			}else if(oldY == minY){
            	return this.getMinY();
            }else{
            	return minY;
            }
		}
		int updateMaxY(int oldY, int newY, int maxY){
            if(newY >= maxY){
            	return newY;
            }else if(oldY == maxY){
            	return this.getMaxY();
            }else{
            	return maxY;
            }
		}
		int getMinY(){
			Block initialBlock = this.blocks.get(0);
	        int minY = initialBlock.legalY;
	        for(Block block:this.blocks){
	            if(block.legalY < minY) {
	                minY = block.legalY;
	            }
	        }
	        return minY;
		}
		int getMaxY(){
			Block initialBlock = this.blocks.get(0);
	        int maxY = initialBlock.legalY;
	        for(Block block:this.blocks){
	            if(block.legalY > maxY) {
	                maxY = block.legalY;
	            }
	        }
	        return maxY;
		}
	}
	
    /////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////// BUCKET ////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////
	class Bucket {
	    private final int numPos;
	    private final int blockSize;
		private final int gridSize;
		
		private final int firstRow, rowRepeat, numRows;

		private final int coordinate;
		
		private Cluster firstCluster;
		
		private List<Block> blocks;

	    Bucket(int numPos, int blockSize, int coordinate){
	    	this.numPos = numPos;
	    	this.blockSize = blockSize;
	    	this.gridSize = this.numPos * this.blockSize;
	    	
	    	this.firstRow = 1;
	    	this.rowRepeat = this.blockSize;
	    	this.numRows = (int) Math.floor(this.gridSize / this.rowRepeat);
	    	
	    	this.coordinate = coordinate;
	    	
	    	this.firstCluster = null;
	    	
	    	this.blocks = new LinkedList<Block>();
	    }

		void addBlock(Block block){
			this.blocks.add(block);
		}
		void removeBlock(Block block){
			this.blocks.remove(block);
		}
		
		double displacementCost(){
			double cost = 0.0;
			for(Block block:this.blocks){
				cost += block.displacementCost();
			}
			return cost;
		}
		
		int numPos(){
			return this.numPos;
		}
		int usedPos(){
			return this.blocks.size();
		}
		boolean isEmpty(){
			return this.blocks.isEmpty();
		}
		
		void legalize(){
			if(this.blocks.size() < 2){
				return;
			}else{
				Collections.sort(this.blocks, BlockComparator.VERTICAL);
				
				for(Block block:this.blocks){
					int rowIndex = (int) Math.round(Math.max(Math.min((block.linearY - this.firstRow) / this.rowRepeat, this.numRows - 1), 0));
					block.setLegalCoordinates(this.coordinate, 1 + this.blockSize * rowIndex);
				}
				
		    	//Make the chain of clusters
				Cluster previousCluster = null;
				for(Block block:this.blocks){
					Cluster cluster = new Cluster(block, this.blockSize, this.gridSize);
					
					if(previousCluster == null){
						this.firstCluster = cluster;
					}else{
			    		cluster.previous = previousCluster;
			    		previousCluster.next = cluster;
					}
					previousCluster = cluster;
				}
		    	
		    	while(this.overlap()){
		        	Cluster cluster = this.firstCluster;
		        	do{
		        		cluster.absorbNeighbour();
		        		
		        		cluster = cluster.next;
		        	}while(cluster != null);
		    	}
			}
		}
		boolean overlap(){
			Cluster cluster = this.firstCluster;
			do{
				if(this.overlap(cluster)){
	    			return true;
	    		}	
				cluster = cluster.next;
			}while(cluster != null);
			return false;
		}
		boolean overlap(Cluster cluster){
			if(cluster.previous != null){
				if(cluster.previous.right > cluster.left){
					return true;
				}
			}
			if(cluster.next != null){
				if(cluster.next.left < cluster.right){
					return true;
				}
			}
			return false;
		}
	    void printBlocks(){
	    	System.out.println("Print blocks:");
	    	for(Block block:this.blocks){
	    		System.out.println("\t" + block);
	    	}
	    	System.out.println();
	    }
	    void printClusters(){
			System.out.println("Print clusters:");
	    	Cluster cluster = this.firstCluster;
	    	do{
	    		System.out.println("\t" + cluster);
	    		cluster = cluster.next;
	    	}while(cluster != null);
	    	System.out.println();
	    }
		public String toString(){
			return this.blocks.size() + " | " + this.numPos;
		}
	}

    /////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// CLUSTER ////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////
	class Cluster {
		private Cluster previous;
		private Cluster next;
    	
		private int left, right;
    	
		private final List<Block> blocks;
    	
		private final int blockSize, gridSize;
    	
    	Cluster(Block block, int blockSize, int gridSize){
    		this.blocks = new ArrayList<Block>();
    		this.blocks.add(block);
    		
    		this.blockSize = blockSize;
    		this.gridSize = gridSize;

    		this.left = block.legalY;
    		this.right = block.legalY + this.blockSize;

    		this.previous = null;
    		this.next = null;
    	}
    	void absorbNeighbour(){
    		if(this.next != null){
            	if(this.right > this.next.left){
        			Cluster c1 = this;
        			Cluster c2 = this.next;
        			
        			for(Block block:c2.blocks){
        				block.legalY = this.right;
        				c1.blocks.add(block);
        				this.right += this.blockSize;
        			}
        			
            		if(this.next.next != null){
            			Cluster c3 = this.next.next;
            			c1.next = c3;
            			c3.previous = c1;
            		}else{
            			c1.next = null;
            		}
            		
            		c2 = null;
        			this.minimumCostShift();
        			
        			this.absorbNeighbour();
    			}
    		}
    	}
    	
    	private void minimumCostShift(){
    		this.fitInColumn();
    		
    		double cost = this.displacementCost();
    		while(true){
        		this.decrease();
        		double localCost = this.displacementCost();
        		if(localCost < cost){
        			cost = localCost;
        		}else{
        			this.increase();
        			break;
        		}
    		}
    		while(true){
        		this.increase();
        		double localCost = this.displacementCost();
        		if(localCost < cost){
        			cost = localCost;
        		}else{
        			this.decrease();
        			break;
        		}
    		}
    		
    		this.fitInColumn();
    	}
    	void fitInColumn(){
    		while(this.right > this.gridSize + 1){
    			this.decrease();
    		}
    		while(this.left < 1){
    			this.increase();
    		}
    	}
    	double displacementCost(){
    		double cost = 0.0;
    		for(Block block:this.blocks){
    			cost += block.displacementCost();
    		}
    		return cost;
    	}
    	void increase(){
    		this.left += this.blockSize;
    		this.right += this.blockSize;
    		for(Block block:this.blocks){
    			block.legalY += this.blockSize;
    		}
    	}
    	void decrease(){
    		this.left -= this.blockSize;
    		this.right -= this.blockSize;
    		for(Block block:this.blocks){
    			block.legalY -= this.blockSize;
    		}
    	}
    	
    	public String toString(){
    		String result = new String();
    		if(this.blocks.size() == 1){
    			result += "1 block | ";
    		}else{
    			result += this.blocks.size() + " blocks| ";
    		}
    		result += "left = " + this.left + " | ";
    		result += "right = " + this.right + " | ";
    		for(Block block:this.blocks){
    			result += block + " ";
    		}
    		return result;
    	}
    }
	
    /////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////// SITE //////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////
	public class Site {

	    private int column, row;
	    private Block block;

	    public Site(int column, int row) {
	        this.column = column;
	        this.row = row;
	        
	        this.block = null;
	    }

	    public int getColumn() {
	        return this.column;
	    }
	    public int getRow() {
	        return this.row;
	    }
	    
	    public void removeBlock(){
	    	if(this.block != null){
	    		this.block = null;
	    	}else{
	    		System.out.println("This site has no block");
	    	}
	    }
	    public void setBlock(Block block){
	    	if(this.block == null){
	    		this.block = block;
	    	}else{
	    		System.out.println("This site already has a block");
	    	}
	    }
	    public Block getBlock(){
	        return this.block;
	    }
	}
}