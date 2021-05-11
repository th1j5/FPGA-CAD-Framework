package route.visual;

public class Wire {
    private short x1, x2, y1, y2;
    private int occupation;

    public Wire(short x1, short x2, short y1, short y2, int occupation) {
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.occupation = occupation;
    }

	short getX1() {
        return this.x1;
    }
    short getX2() {
        return this.x2;
    }
    short getY1() {
        return this.y1;
    }
    short getY2() {
        return this.y2;
    }
    
    int getOccupation() {
    	return this.occupation;
    }
    

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f)", this.x1, this.y1);
    }
}