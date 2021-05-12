package route.visual;

import route.main.Logger;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.JComponent;
import javax.swing.JPanel;

public class RoutePanel extends JPanel {
	private static final long serialVersionUID = -2621200118414334047L;
	
	private Logger logger;
	
	private final Color gridColorLight = new Color(150, 150, 150);
    private final Color gridColorDark = new Color(0, 0, 0);
    
    private transient Routing routing;
    
    
    private int interspacing;
    private int circuitWidth;
    private int circuitHeight;
    private int left, top, right, bottom;
    
    private boolean mouseEnabled = false;
    
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
    
    @Override
    public void paintComponent(Graphics g) {
    	super.paintComponent(g);
    	
    	if (this.routing != null) {
			this.setDimensions();
			this.drawGrid(g); //too distracting
			this.drawWires(g);
			if (this.mouseEnabled)this.drawBlockInformation(g);
    	}
    }
    
    private void drawLine(Graphics g, double x1, double y1, double x2, double y2) {
    	g.drawLine((int)Math.round(x1), (int)Math.round(y1), (int)Math.round(x2), (int)Math.round(y2));
    }
    
    private void drawThickLine(Graphics g, double x1, double y1, double x2, double y2) {
    	Graphics2D g2d = (Graphics2D) g;
    	Stroke defaultstroke;
    	defaultstroke = g2d.getStroke();
    	g2d.setStroke(new BasicStroke(6.0F));
    	g2d.drawLine((int)Math.round(x1), (int)Math.round(y1), (int)Math.round(x2), (int)Math.round(y2));
    	g2d.setStroke(defaultstroke);
    }
    
    private void drawString(Graphics g, String s, double x, double y){
    	g.drawString(s, (int)Math.round(x), (int)Math.round(y));
    }
    
    private void setDimensions() {
    	int maxWidth = this.getWidth();
    	int maxHeight = this.getHeight();
    	
    	int circuitWidth = this.routing.getWidth() + 1;
    	int circuitHeight = this.routing.getHeight() + 1;
    	
    	this.interspacing = Math.min((maxWidth - 1) / circuitWidth, (maxHeight - 1) / circuitHeight);
    
    	int width = circuitWidth * this.interspacing+2;
    	int height = circuitHeight * this.interspacing+2;
    	
    	this.left = (maxWidth - width) / 2;
    	this.top = (maxHeight - height) / 2;
    	
    	this.right = this.left + this.interspacing * circuitWidth;
    	this.bottom = this.top + this.interspacing * circuitHeight;
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
    	this.drawString(g, String.format("Max occupancy: %d", routing.getMaxCongestion()), this.right, this.top);
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
  			this.drawWireCoordinate(this.x, this.y, g);
  		}
  		
  		public void drawWireCoordinate(int x, int y, Graphics g){
  			int xlength = this.panel.right - this.panel.left;
  			int ylength = this.panel.top - this.panel.bottom;
  	    	int coorX = (int)(x-this.panel.left)*this.panel.circuitWidth/xlength;
  	    	int coorY = (int)(y-this.panel.top)*this.panel.circuitHeight/ylength;
  	    	if(this.onScreen(coorX, coorY)){
  	    		String s = "[" + coorX + "," + coorY + "]";
  	        	int fontSize = 20;
  	      		g.setFont(new Font("TimesRoman", Font.BOLD, fontSize));
  	    		g.setColor(Color.BLUE);
  	    		g.drawString(s, x, y);
  	    	}
  	    }

  	    public boolean onScreen(int x, int y){
  	    	return (x > 0 && y > 0 && x < this.panel.routing.getWidth()+2 && y < this.panel.routing.getHeight()+2);
  	    }
  	}
  	
}