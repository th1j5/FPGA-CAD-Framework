package circuit.architecture;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.Logger;

class BlockTypeData implements Serializable {
    /**
     * This is a singleton class. It should be serialized explicitly!
     */

    private static final long serialVersionUID = 4923006752028503183L;

    // Stuff that provides singleton functionality
    private static BlockTypeData instance = new BlockTypeData();
    static BlockTypeData getInstance() {
        return BlockTypeData.instance;
    }
    static void setInstance(BlockTypeData instance) {
        BlockTypeData.instance = instance;
    }



    private Map<String, Integer> types = new HashMap<String, Integer>();
    private List<String> typeNames = new ArrayList<String>();

    private List<BlockCategory> categories = new ArrayList<BlockCategory>();
    private List<List<BlockType>> blockTypesPerCategory = new ArrayList<List<BlockType>>();

    private List<Integer> heights = new ArrayList<Integer>();
    private List<Integer> columnStarts = new ArrayList<Integer>();
    private List<Integer> columnRepeats = new ArrayList<Integer>();

    private List<Boolean> clocked = new ArrayList<Boolean>();


    private List<Map<String, Integer>> modes = new ArrayList<Map<String, Integer>>();
    private List<List<String>> modeNames = new ArrayList<List<String>>();
    private List<List<Map<String, Integer>>> children = new ArrayList<List<Map<String, Integer>>>();

    private List<List<List<Integer>>> childStarts = new ArrayList<List<List<Integer>>>();
    private List<List<List<Integer>>> childEnds = new ArrayList<List<List<Integer>>>();
    private List<List<Integer>> numChildren = new ArrayList<List<Integer>>();



    BlockTypeData() {
        for(int i = 0; i < BlockCategory.values().length; i++) {
            this.blockTypesPerCategory.add(new ArrayList<BlockType>());
        }
    }


    void addType(String typeName, String categoryName, int height, int start, int repeat, boolean clocked, Map<String, Integer> inputs, Map<String, Integer> outputs) {
        int typeIndex = this.typeNames.size();
        this.typeNames.add(typeName);
        this.types.put(typeName, typeIndex);

        BlockCategory category = this.getCategoryFromString(categoryName);
        this.categories.add(category);
        this.blockTypesPerCategory.get(category.ordinal()).add(new BlockType(typeName));

        this.heights.add(height);
        this.columnStarts.add(start);
        this.columnRepeats.add(repeat);

        this.clocked.add(clocked);

        this.modeNames.add(new ArrayList<String>());
        this.modes.add(new HashMap<String, Integer>());
        this.children.add(new ArrayList<Map<String, Integer>>());

        PortTypeData.getInstance().setNumInputPorts(typeIndex, inputs.size());
        PortTypeData.getInstance().addPorts(typeIndex, inputs);
        PortTypeData.getInstance().addPorts(typeIndex, outputs);
    }

    private BlockCategory getCategoryFromString(String categoryName) {
        switch(categoryName) {
        case "io":
            return BlockCategory.IO;

        case "clb":
            return BlockCategory.CLB;

        case "hardblock":
            return BlockCategory.HARDBLOCK;

        case "local":
            return BlockCategory.LOCAL;

        case "leaf":
            return BlockCategory.LEAF;

        default:
            return null;
        }
    }


    void addMode(String typeName, String modeName, Map<String, Integer> children) {
        int typeIndex = this.types.get(typeName);

        int modeIndex = this.modeNames.get(typeIndex).size();
        this.modeNames.get(typeIndex).add(modeName);
        this.modes.get(typeIndex).put(modeName, modeIndex);

        this.children.get(typeIndex).add(children);
    }



    /**
     * This method should be called after all block types and modes have been added.
     * It caches some data.
     */
    void postProcess() {
        this.cacheChildren();
        PortTypeData.getInstance().postProcess();
    }

    private void cacheChildren() {
        int numTypes = this.types.size();
        for(int typeIndex = 0; typeIndex < numTypes; typeIndex++) {
            List<Map<String, Integer>> typeChildren = this.children.get(typeIndex);
            List<List<Integer>> typeChildStarts = new ArrayList<List<Integer>>();
            List<List<Integer>> typeChildEnds = new ArrayList<List<Integer>>();
            List<Integer> typeNumChildren = new ArrayList<Integer>();

            int numModes = this.modeNames.get(typeIndex).size();
            for(int modeIndex = 0; modeIndex < numModes; modeIndex++) {
                typeChildStarts.add(new ArrayList<Integer>(Collections.nCopies(numTypes, (Integer) null)));
                typeChildEnds.add(new ArrayList<Integer>(Collections.nCopies(numTypes, (Integer) null)));
                int numChildren = 0;

                for(Map.Entry<String, Integer> childEntry : typeChildren.get(modeIndex).entrySet()) {
                    String childName = childEntry.getKey();
                    int childTypeIndex = this.types.get(childName);
                    int childCount = childEntry.getValue();

                    typeChildStarts.get(modeIndex).set(childTypeIndex, numChildren);
                    numChildren += childCount;
                    typeChildEnds.get(modeIndex).set(childTypeIndex, numChildren);
                }

                typeNumChildren.add(numChildren);
            }

            this.childStarts.add(typeChildStarts);
            this.childEnds.add(typeChildEnds);
            this.numChildren.add(typeNumChildren);
        }
    }



    int getTypeIndex(String typeName) {
        Integer typeIndex = this.types.get(typeName);
        if(typeIndex == null) {
            Logger.raise("Invalid block type: " + typeName);
        }
        return typeIndex;
    }

    int getModeIndex(int typeIndex, String argumentModeName) {
        String modeName = (argumentModeName == null) ? "" : argumentModeName;
        Integer modeIndex = this.modes.get(typeIndex).get(modeName);

        if(modeIndex == null) {
            Logger.raise("Invalid mode type for block type " + this.getName(typeIndex) + ": " + modeName);
        }
        return modeIndex;
    }



    public List<BlockType> getBlockTypes(BlockCategory category) {
        return this.blockTypesPerCategory.get(category.ordinal());
    }

    public List<BlockType> getGlobalBlockTypes() {
        List<BlockType> types = new ArrayList<BlockType>();
        types.addAll(this.getBlockTypes(BlockCategory.IO));
        types.addAll(this.getBlockTypes(BlockCategory.CLB));
        types.addAll(this.getBlockTypes(BlockCategory.HARDBLOCK));

        return types;
    }



    String getName(int typeIndex) {
        return this.typeNames.get(typeIndex);
    }
    BlockCategory getCategory(int typeIndex) {
        return this.categories.get(typeIndex);
    }
    boolean isGlobal(int typeIndex) {
        BlockCategory category = this.getCategory(typeIndex);
        return category != BlockCategory.LOCAL && category != BlockCategory.LEAF;
    }
    boolean isLeaf(int typeIndex) {
        BlockCategory category = this.getCategory(typeIndex);
        return category == BlockCategory.LEAF;
    }

    int getHeight(int typeIndex) {
        return this.heights.get(typeIndex);
    }
    int getStart(int typeIndex) {
        return this.columnStarts.get(typeIndex);
    }
    int getRepeat(int typeIndex) {
        return this.columnRepeats.get(typeIndex);
    }

    String getModeName(int typeIndex, int modeIndex) {
        return this.modeNames.get(typeIndex).get(modeIndex);
    }
    Map<String, Integer> getChildren(int typeIndex, int modeIndex) {
        return this.children.get(typeIndex).get(modeIndex);
    }

    boolean isClocked(int typeIndex) {
        return this.clocked.get(typeIndex);
    }

    int getNumChildren(int typeIndex, int modeIndex) {
        return this.numChildren.get(typeIndex).get(modeIndex);
    }
    int[] getChildRange(int typeIndex, int modeIndex, int childTypeIndex) {
        int childStart = this.childStarts.get(typeIndex).get(modeIndex).get(childTypeIndex);
        int childEnd = this.childEnds.get(typeIndex).get(modeIndex).get(childTypeIndex);

        int[] childRange = {childStart, childEnd};
        return childRange;
    }
}
