package placers.random;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import circuit.HardBlock;
import circuit.PackedCircuit;
import circuit.Clb;
import circuit.Input;
import circuit.Output;

import architecture.Architecture;
import architecture.FourLutSanitized;
import architecture.GridTile;
import architecture.HardBlockSite;
import architecture.HeterogeneousArchitecture;
import architecture.IoSite;
import architecture.Site;
import architecture.SiteType;
import architecture.ClbSite;

public class RandomPlacer
{

	public static void placeCLBs(PackedCircuit c, Architecture a)
	{
		Random rand= new Random();
		placeCLBs(c, a, rand);
	}
	
	public static void placeCLBs(PackedCircuit c, Architecture a, Random rand)
	{
		Set<Site> temp = new HashSet<Site>();
		for (int x=1;x<a.getWidth()+1;x++)
		{
			for (int y=1;y<a.getHeight()+1;y++)
			{
				Site s = a.getSite(x,y,0);
				s.setBlock(null);
				temp.add(s);				
			}
		}
		for(Clb b:c.clbs.values())
		{
			if (b instanceof Clb)
			{
				Site site=(Site) temp.toArray()[rand.nextInt(temp.size())];
				temp.remove(site);
				site.setBlock(b);
				b.setSite(site);
			}	
		}
	}
	
	
	public static void placeRandomIOs(PackedCircuit c, FourLutSanitized a, Random rand)
	{
		//Clear all existing placements
		Set<IoSite> tempIOs = new HashSet<IoSite>();
		for (int x=1;x<a.getWidth()+1;x++)
		{
			GridTile ioTile1 = a.getTile(x, 0);
			for(int i = 0; i < ioTile1.getCapacity(); i++)
			{
				IoSite s = (IoSite)(ioTile1.getSite(i));
				s.setBlock(null);
				tempIOs.add(s);
			}
			GridTile ioTile2 = a.getTile(x,a.getHeight()+1);
			for(int i = 0; i < ioTile2.getCapacity(); i++)
			{
				IoSite t = (IoSite)(ioTile2.getSite(i));
				t.setBlock(null);
				tempIOs.add(t);
			}
		}
		for (int y=1;y<a.getHeight()+1;y++)
		{
			GridTile ioTile1 = a.getTile(0,y);
			for(int i = 0; i < ioTile1.getCapacity(); i++)
			{
				IoSite s = (IoSite)(ioTile1.getSite(i));
				s.setBlock(null);
				tempIOs.add(s);
			}
			GridTile ioTile2 = a.getTile(a.getWidth()+1,y);
			for(int i = 0; i < ioTile2.getCapacity(); i++)
			{
				IoSite t = (IoSite)(ioTile2.getSite(i));
				t.setBlock(null);
				tempIOs.add(t);
			}
		}
		//Place all inputs and outputs
		for(Input in:c.inputs.values())
		{
			if (in instanceof Input)
			{
				IoSite site=(IoSite) tempIOs.toArray()[rand.nextInt(tempIOs.size())];
				site.setBlock(in);
				in.setSite(site);
				tempIOs.remove(site);
			}	
		}
		for(Output out:c.outputs.values())
		{
			if (out instanceof Output)
			{
				IoSite site=(IoSite) tempIOs.toArray()[rand.nextInt(tempIOs.size())];
				site.setBlock(out);
				out.setSite(site);
				tempIOs.remove(site);
			}	
		}
	}
	
	
	public static void placeDeterministicIOs(PackedCircuit c, FourLutSanitized a)
	{
		int index = 0;
		for(Input input:c.inputs.values())
		{
			input.fixed = true;
			IoSite site = a.getIOSite(index);
			site.setBlock(input);
			input.setSite(site);
			index += 8;
			if(index >= a.getHeight()*2 + a.getWidth()*2)
			{
				index = (index % 8) + 1;
			}
		}
		for(Output output:c.outputs.values())
		{
			output.fixed = true;
			IoSite site = a.getIOSite(index);
			site.setBlock(output);
			output.setSite(site);
			index += 8;
			if(index >= a.getHeight()*2 + a.getWidth()*2)
			{
				index = (index % 8) + 1;
			}
		}
	}
	
	public static void placeCLBsandIOs(PackedCircuit c, FourLutSanitized a, Random rand)
	{
		//Random place CLBs
		placeCLBs(c, a, rand);
		
		//Random Place IOs
		placeRandomIOs(c, a, rand);
	}
	
	public static void placeCLBsandFixedIOs(PackedCircuit c, FourLutSanitized a, Random rand)
	{
		//Random place CLBs
		placeCLBs(c, a, rand);

		//Deterministic place IOs
		placeDeterministicIOs(c, a);
	}
	
	public static void placeCLBsandFixedIOs(PackedCircuit c, Architecture a, Random rand)
	{
		if(a instanceof FourLutSanitized) {
			FourLutSanitized arch = (FourLutSanitized) a;
			
			//Random place CLBs
			placeCLBs(c, arch, rand);

			//Deterministic place IOs
			placeDeterministicIOs(c, arch);
			
			
		} else if(a instanceof HeterogeneousArchitecture) {
			HeterogeneousArchitecture arch = (HeterogeneousArchitecture) a;
			
			//Random place CLBs and HBs
			placeClbsAndHbs(c, arch, rand);
			
			//Deterministic place IOs
			placeFixedIOs(c, arch);
		}
	}
	
	public static void placeClbsAndHbs(PackedCircuit c, HeterogeneousArchitecture a, Random rand)
	{
		//Initialize data structures
		ArrayList<ClbSite> clbSites = new ArrayList<>();
		ArrayList<ArrayList<HardBlockSite>> hardBlockSites = new ArrayList<>();
		ArrayList<String> hardBlockTypes = new ArrayList<>();
		for(int x = 1; x < a.getWidth() + 1; x++)
		{
			for(int y = 1; y < a.getHeight() + 1; y++)
			{
				Site s = a.getSite(x, y, 0);
				if(s.getType() == SiteType.CLB)
				{
					s.setBlock(null);
					clbSites.add((ClbSite)s);
				}
				else //Must be a hardBlock
				{
					s.setBlock(null);
					String typeName = ((HardBlockSite)s).getTypeName();
					int curIndex = 0;
					boolean found = false;
					for(String name: hardBlockTypes)
					{
						if(name.contains(typeName))
						{
							hardBlockSites.get(curIndex).add((HardBlockSite)s);
							found = true;
							break;
						}
						curIndex++;
					}
					if(!found)
					{
						hardBlockTypes.add(typeName);
						ArrayList <HardBlockSite> newList = new ArrayList<>();
						newList.add((HardBlockSite)s);
						hardBlockSites.add(newList);
					}
				}
			}
		}
		
		//Random place CLBs
		for(Clb clb: c.clbs.values())
		{
			int randomIndex = rand.nextInt(clbSites.size());
			ClbSite site = clbSites.get(randomIndex);
			clbSites.remove(randomIndex);
			site.setBlock(clb);
			clb.setSite(site);
		}
				
		//Random place hardBlocks
		for(Vector<HardBlock> hbVector: c.getHardBlocks())
		{
			String curTypeName = hbVector.get(0).getTypeName();
			int curIndex = 0;
			for(String name: hardBlockTypes)
			{
				if(name.contains(curTypeName))
				{
					break;
				}
				curIndex++;
			}
			for(HardBlock hardBlock: hbVector)
			{
				int randomIndex = rand.nextInt(hardBlockSites.get(curIndex).size());
				HardBlockSite site = hardBlockSites.get(curIndex).get(randomIndex);
				hardBlockSites.get(curIndex).remove(randomIndex);
				site.setBlock(hardBlock);
				hardBlock.setSite(site);
			}
		}
	}
	
	public static void placeFixedIOs(PackedCircuit c, Architecture a)
	{
		//Deterministic place IOs
		int index = 0;
		ArrayList<IoSite> IOSites = a.getIOSites();
		int nbSites = IOSites.size();
		for(Input input:c.inputs.values())
		{
			input.fixed = true;
			//input.fixed = false;
			IoSite site = IOSites.get(index);
			site.setBlock(input);
			input.setSite(site);
			index += 8;
			if(index >= nbSites)
			{
				index = (index % 8) + 1;
			}
		}
		for(Output output:c.outputs.values())
		{
			output.fixed = true;
			//output.fixed = false;
			IoSite site = IOSites.get(index);
			site.setBlock(output);
			output.setSite(site);
			index += 8;
			if(index >= nbSites)
			{
				index = (index % 8) + 1;
			}
		}
	}
	
	
}