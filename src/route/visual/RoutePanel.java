package route.visual;

import route.circuit.architecture.BlockCategory;
import route.circuit.block.GlobalBlock;
import route.main.Logger;
import route.circuit.resource.RouteNode;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Line2D;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;

public class RoutePanel extends JPanel {
	private static final long serialVersionUID = -2621200118414334047L;
	
	private Logger logger;
	
	private final Color gridColorLight = new Color(150, 150, 150);
    private final Color gridColorDark = new Color(0, 0, 0);
    private final Color clbColor = new Color(255, 0, 0, 50);
    private final Color macroColor = new Color(100, 0, 0, 50);
    private final Color ioColor = new Color(255, 255, 0, 50);
    private final Color dspColor = new Color(0, 255, 0, 50);
    private final Color m9kColor = new Color(0, 0, 255, 50);
    private final Color m144kColor = new Color(0, 255, 255, 50);
    private final Color hardBlockColor = new Color(0, 0, 0, 50);
    
    private transient Routing routing;
    
    
    private int blockSize;
    private int circuitWidth;
    private int circuitHeight;
    private int left, top, right, bottom;
    
    private boolean mouseEnabled = false, plotEnabled = false;
    
    RoutePanel(Logger logger) {
    	this.logger = logger;
    }
    
    void setRouting(Routing routing) {
    	this.routing = routing;
    	circuitWidth = this.routing.getWidth();
    	circuitHeight = this.routing.getHeight();
    	
    	super.repaint();
    }
    
    void setMouseEnabled(boolean mouseEnabled) {
    	this.mouseEnabled = mouseEnabled;
    }
    
    void setPlotEnabled(boolean plotEnabled) { //plotEnabled, double[] bbCost) {
    	this.plotEnabled = plotEnabled;
    	//this.bbCost = bbCost;
    }
    
    @Override
    public void paintComponent(Graphics g) {
    	super.paintComponent(g);
    	
    	if (this.routing != null) {
    		if (!this.plotEnabled) {
    			this.setDimensions();
    			this.drawGrid(g); //too distracting
    			this.drawWires(g);
    			//if (this.mouseEnabled)this.drawBlockInformation(g);
    		} else {
    			this.drawPlot(g);
    		}
    	}
    }
    
    private void drawPlot(Graphics g) {
    	int iteration = this.routing.getIteration();
    	
    	//set boundaries of plot
    	double alpha = 0.2;
    	double left = this.getWidth() * alpha;
    	double top = this.getHeight() * alpha;
    	double right = this.getWidth() * (1-alpha);
    	double bottom = this.getHeight() * (1-alpha);
    	
    	//double maxbbcost = 0.0; for (double bbCost:this.bbCost){maxbbcost = Math.max(bbCost, maxbbcost);}
    	
    	g.setColor(Color.BLACK);
    	FontMetrics metrics = g.getFontMetrics();
    	this.drawLine(g, left, bottom+1, right + metrics.getHeight()/2, bottom+1);
    	this.drawLine(g, left, bottom, right + metrics.getHeight()/2, bottom);
    	this.drawLine(g, left-1, top - metrics.getHeight()/2, left-1, bottom);
    	this.drawLine(g, left, top - metrics.getHeight()/2, left, bottom);
    	
    }
    
    private void drawLine(Graphics g, double x1, double y1, double x2, double y2) {
    	g.drawLine((int)Math.round(x1), (int)Math.round(y1), (int)Math.round(x2), (int)Math.round(y2));
    }
    
    private void drawString(Graphics g, String s, double x, double y){
    	g.drawString(s, (int)Math.round(x), (int)Math.round(y));
    }
    
    private void setDimensions() {
    	int maxWidth = this.getWidth();
    	int maxHeight = this.getHeight();
    	
    	int circuitWidth = this.routing.getWidth() + 1;
    	int circuitHeight = this.routing.getHeight() + 1;
    	
    	this.blockSize = Math.min((maxWidth - 1) / circuitWidth, (maxHeight - 1) / circuitHeight);
    
    	int width = circuitWidth * this.blockSize+2;
    	int height = circuitHeight * this.blockSize+2;
    	
    	this.left = (maxWidth - width) / 2;
    	this.top = (maxHeight - height) / 2;
    	
    	this.right = this.left + this.blockSize * circuitWidth;
    	this.bottom = this.top + this.blockSize * circuitHeight;
    }
    
    private void drawGrid(Graphics g) {
    	g.setColor(this.gridColorLight);
    	
    	int xlength = this.right - this.left;
    	
    	for (int x = 0; x <= this.circuitWidth; x += 1) {
    		if (x == 0 || x == this.circuitWidth) {
    			g.drawLine(this.left+x*xlength/this.circuitWidth, this.top + xlength/this.circuitWidth, this.left+x*xlength/this.circuitWidth, this.bottom - xlength/this.circuitWidth);
    		} else if (x % 10 == 1) {
    			g.setColor(this.gridColorDark);
    			g.drawLine(this.left+x*xlength/this.circuitWidth, this.top, this.left+x*xlength/this.circuitWidth, this.bottom);
    			g.setColor(this.gridColorLight);
    		} else {
    			g.drawLine(this.left+x*xlength/this.circuitWidth, this.top, this.left+x*xlength/this.circuitWidth, this.bottom);
    		}	
    	}
    	
    	int ylength = this.top - this.bottom;
    	for (int y = 0; y <= this.circuitHeight; y += 1) {
    		if (y == 0 || y == this.circuitHeight) {
    			g.drawLine(this.left - ylength/this.circuitHeight, this.bottom+y*ylength/this.circuitHeight, this.right + ylength/this.circuitHeight, this.bottom+y*ylength/this.circuitHeight);
    		} else if (y % 10 == 1) {
    			g.setColor(this.gridColorDark);
    			g.drawLine(this.left, this.bottom+y*ylength/this.circuitHeight, this.right, this.bottom+y*ylength/this.circuitHeight);
    			g.setColor(this.gridColorLight);
    		} else {
    			g.drawLine(this.left, this.bottom+y*ylength/this.circuitHeight, this.right, this.bottom+y*ylength/this.circuitHeight);
    		}
    	}
    }
    
    private void drawWires(Graphics g) {
    	for (Wire wireEntry : this.routing.getWires()) {
    		this.drawWire(wireEntry, g, routing.getMaxCongestion());
    	}
    	this.drawString(g, String.format("Max congestion: %d", routing.getMaxCongestion()), this.right, this.top);
    }
    
    private void drawThickLine(Graphics g, double x1, double y1, double x2, double y2) {
    	Graphics2D g2d = (Graphics2D) g;
    	Stroke defaultstroke;
    	defaultstroke = g2d.getStroke();
    	g2d.setStroke(new BasicStroke(6.0F));
    	g2d.drawLine((int)Math.round(x1), (int)Math.round(y1), (int)Math.round(x2), (int)Math.round(y2));
    	g2d.setStroke(defaultstroke);
    }
    
    private void drawWire(Wire wire, Graphics g, int maxCongestion) {
    	int congestion = wire.getOccupation();
    	
    	Color congestionColour;
    	if (maxCongestion > 1) {
    		// sometimes happens that a wire of usage 0 slips in somehow, draw invisible wire
    		if (congestion == 0) {
    			congestionColour = new Color(0,0,0,0);
    		} else {
    			// factor should be mapped to 0..255 (low congestion..high congestion)
    			int colourRedFactor = (int)Math.floor((congestion-1)*205/(maxCongestion-1));
        		congestionColour = new Color(25+colourRedFactor, 230-colourRedFactor, 50);
    		}
    	// if maxCongestion is 1, would get division by 0, just use green.
    	} else {
    		congestionColour = new Color(25,230,50);
    	}
    	
    	g.setColor(congestionColour);
    	
    	int xlength = this.right - this.left;
    	int ylength = this.top - this.bottom;
    	double x1 = this.left + wire.getX1()*xlength/this.circuitWidth;
    	double x2 = this.left + wire.getX2()*xlength/this.circuitWidth;
    	double y1 = this.bottom + wire.getY1()*ylength/this.circuitHeight;
    	double y2 = this.bottom + wire.getY2()*ylength/this.circuitHeight;

    	this.drawThickLine(g, x1, y1, x2, y2);
    }
    
    //let's just deactivate mouseComponent for a while, since it doesn't work and isn't high-priority
    /*
    private void drawBlockInformation(Graphics g) {
    	final MouseLabelComponent mouseLabel = new MouseLabelComponent(this);
        //add component to panel
        this.add(mouseLabel);
        mouseLabel.setBounds(0, 0, this.getWidth(), this.getHeight());
        this.addMouseMotionListener(new MouseMotionAdapter(){
        	public void mouseMoved (MouseEvent me){
        		mouseLabel.x = me.getX();
        		mouseLabel.y = me.getY();
        	}
        });
    }
    
  	private class MouseLabelComponent extends JComponent{
  	  	//x , y are from mouseLocation relative to real screen/JPanel
  	  	//coorX, coorY are FPGA's
  		private static final long serialVersionUID = 1L;
  		private int x;
  		private int y;
  		private RoutePanel panel;
  			
  		public MouseLabelComponent(RoutePanel panel){
  			this.panel = panel;
  		}
  			
  		protected void paintComponent(Graphics g){
  			this.drawBlockCoordinate(this.x, this.y, g);
  		}
  		
  		public void drawBlockCoordinate(int x, int y, Graphics g){
  	    	int coorX = (int)(x-this.panel.left)/this.panel.blockSize;
  	    	int coorY = (int)(y-this.panel.top)/this.panel.blockSize;
  	    	if(this.onScreen(coorX, coorY)){
  	    		String s = "[" + coorX + "," + coorY + "]";
  	    		GlobalBlock globalBlock = this.getGlobalBlock(coorX, coorY);
  	    		if(globalBlock != null){
  	    			s += " " + globalBlock.getName();
  	    		}
  	        	int fontSize = 20;
  	      		g.setFont(new Font("TimesRoman", Font.BOLD, fontSize));
  	    		g.setColor(Color.BLUE);
  	    		g.drawString(s, x, y);
  	    	}
  	    }
  	  	public GlobalBlock getGlobalBlock(int x, int y){
  	        for(Map.Entry<GlobalBlock, Coordinate> blockEntry : this.panel.placement.blocks()) {
  	        	Coordinate blockCoor = blockEntry.getValue();
  	        	
  	        	if(Math.abs(blockCoor.getX() - x) < 0.25 && Math.abs(blockCoor.getY() - y) < 0.25){
  	        		return blockEntry.getKey();
  	        	}
  	        }
  	        return null;      
  	    }
  	    public boolean onScreen(int x, int y){
  	    	return (x > 0 && y > 0 && x < this.panel.placement.getWidth()+2 && y < this.panel.placement.getHeight()+2);
  	    }
  	}
  	*/
}