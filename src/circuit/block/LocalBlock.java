package circuit.block;

import circuit.architecture.BlockType;
import circuit.architecture.PortType;
import circuit.pin.LocalPin;


public class LocalBlock extends AbstractBlock {
    
    
    private AbstractBlock parent;
    
    public LocalBlock(String name, BlockType type, int index, AbstractBlock parent) {
        super(name, type, index);
        
        this.parent = parent;
    }
    
    @Override
    public AbstractBlock getParent() {
        return this.parent;
    }
    
    @Override
    public LocalPin createPin(PortType portType, int index) {
        return new LocalPin(this, portType, index);
    }
}
