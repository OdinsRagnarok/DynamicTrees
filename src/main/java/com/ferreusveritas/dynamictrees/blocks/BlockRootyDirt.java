package com.ferreusveritas.dynamictrees.blocks;

import java.util.Random;

import com.ferreusveritas.dynamictrees.ModBlocks;
import com.ferreusveritas.dynamictrees.ModConstants;
import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.api.backport.BlockAccess;
import com.ferreusveritas.dynamictrees.api.backport.BlockBackport;
import com.ferreusveritas.dynamictrees.api.backport.BlockPos;
import com.ferreusveritas.dynamictrees.api.backport.EnumFacing;
import com.ferreusveritas.dynamictrees.api.backport.EnumHand;
import com.ferreusveritas.dynamictrees.api.backport.IBlockAccess;
import com.ferreusveritas.dynamictrees.api.backport.IBlockState;
import com.ferreusveritas.dynamictrees.api.backport.PropertyInteger;
import com.ferreusveritas.dynamictrees.api.backport.World;
import com.ferreusveritas.dynamictrees.api.cells.Cells;
import com.ferreusveritas.dynamictrees.api.cells.ICell;
import com.ferreusveritas.dynamictrees.api.network.GrowSignal;
import com.ferreusveritas.dynamictrees.api.network.MapSignal;
import com.ferreusveritas.dynamictrees.api.treedata.ITreePart;
import com.ferreusveritas.dynamictrees.renderers.RendererRootyDirt;
import com.ferreusveritas.dynamictrees.renderers.RendererRootyDirt.RenderType;
import com.ferreusveritas.dynamictrees.trees.DynamicTree;
import com.ferreusveritas.dynamictrees.trees.Species;
import com.ferreusveritas.dynamictrees.util.CoordUtils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.Explosion;

public class BlockRootyDirt extends BlockBackport implements ITreePart {
	
	static String name = "rootydirt";
	
	public static final PropertyInteger LIFE = PropertyInteger.create("life", 0, 15, PropertyInteger.Bits.BXXXX);
	
	public BlockRootyDirt() {
		this(name);
	}
	
	public BlockRootyDirt(String name) {
		super(Material.ground);
		setStepSound(soundTypeGrass);
		setDefaultState(getDefaultState().withProperty(LIFE, 15));
		setTickRandomly(true);
		setUnlocalizedNameReg(name);
		setRegistryName(name);
	}
	
	///////////////////////////////////////////
	// BLOCKSTATES
	///////////////////////////////////////////
	
	// N/A in 1.7.10
	
	
	
	///////////////////////////////////////////
	// INTERACTION
	///////////////////////////////////////////
	
	@Override
	public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
		updateTree(world, pos, random, false);
	}
	
	public EnumFacing getTrunkDirection(IBlockAccess access, BlockPos rootPos) {
		return EnumFacing.UP; 
	}
	
	/**
	 * 
	 * @param world
	 * @param rootPos
	 * @param random
	 * @return false if tree was not found
	 */
	public boolean updateTree(World world, BlockPos rootPos, Random random, boolean rapid) {
		
		Species species = getSpecies(world, rootPos);
		boolean viable = false;
		
		if(species != null) {
			BlockPos treePos = rootPos.offset(getTrunkDirection(world, rootPos));
			ITreePart treeBase = TreeHelper.getTreePart(world, treePos);
			
			if(treeBase != null && CoordUtils.isSurroundedByLoadedChunks(world, rootPos)) {
				viable = species.update(world, this, rootPos, getSoilLife(world, rootPos), treeBase, treePos, random, rapid);
			}
		}
		
		if(!viable) {
			world.setBlockState(rootPos, getDecayBlockState(world, rootPos), 3);
		}
		
		return viable;
	}
	
	/**
	 * This is the state the rooty dirt returns to once it no longer supports a tree structure.
	 * 
	 * @param access
	 * @param pos The position of the {@link BlockRootyDirt}
	 * @return
	 */
	public IBlockState getDecayBlockState(IBlockAccess access, BlockPos pos) {
		return ModBlocks.blockStates.dirt;
	}
	
	@Override
	public Item getItemDropped(int metadata, Random rand, int fortune) {
		return Item.getItemFromBlock(Blocks.dirt);
	}
	
	@Override
	public float getBlockHardness(IBlockState state, World world, BlockPos pos) {
		return 20.0f;//Encourage proper tool usage and discourage bypassing tree felling by digging the root from under the tree
	};
	
	@Override
	protected boolean canSilkHarvest() {
		return false;
	}
	
	@Override
	public boolean hasComparatorInputOverride() {
		return true;
	}
	
	@Override
	public int getComparatorInputOverride(World world, BlockPos pos, int side) {
		return getSoilLife(world, pos);
	}
	
	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, ItemStack heldItem, EnumFacing facing, float hitX, float hitY, float hitZ) {
		DynamicTree tree = getTree(world, pos);
		
		if(tree != null) {
			return tree.onTreeActivated(world, pos, state, player, hand, heldItem, facing, hitX, hitY, hitZ);
		}
		
		return false;
	}
	
	public void destroyTree(World world, BlockPos pos) {
		BlockBranch branch = TreeHelper.getBranch(world, pos.up());
		if(branch != null) {
			branch.destroyEntireTree(world, pos.up());
		}
	}
	
	@Override
	public void onBlockHarvested(World world, BlockPos pos, int localMeta, EntityPlayer player) {
		destroyTree(world, pos);
	}
	
	@Override
	public void onBlockExploded(World world, BlockPos pos, Explosion explosion) {
		destroyTree(world, pos);
	}
	
	public int getSoilLife(IBlockAccess blockAccess, BlockPos pos) {
		return blockAccess.getBlockMetadata(pos);
	}
	
	public void setSoilLife(World world, BlockPos pos, int life) {
		world.real().setBlockMetadataWithNotify(pos.getX(), pos.getY(), pos.getZ(), MathHelper.clamp_int(life, 0, 15), 3);
		world.real().func_147453_f(pos.getX(), pos.getY(), pos.getZ(), this);//Notify all neighbors of NSEWUD neighbors
	}
	
	public boolean fertilize(World world, BlockPos pos, int amount) {
		int soilLife = getSoilLife(world, pos);
		if((soilLife == 0 && amount < 0) || (soilLife == 15 && amount > 0)) {
			return false;//Already maxed out
		}
		setSoilLife(world, pos, soilLife + amount);
		return true;
	}
	
	@Override
	public ICell getHydrationCell(IBlockAccess blockAccess, BlockPos pos, IBlockState blockState, EnumFacing dir, DynamicTree leavesTree) {
		return Cells.nullCell;
	}
	
	@Override
	public GrowSignal growSignal(World world, BlockPos pos, GrowSignal signal) {
		return signal;
	}
	
	@Override
	public int getRadiusForConnection(IBlockAccess blockAccess, BlockPos pos, BlockBranch from, int fromRadius) {
		return 8;
	}
	
	@Override
	public int probabilityForBlock(IBlockAccess blockAccess, BlockPos pos, BlockBranch from) {
		return 0;
	}
	
	@Override
	public int getRadius(IBlockAccess blockAccess, BlockPos pos) {
		return 0;
	}
	
	public MapSignal startAnalysis(World world, BlockPos rootPos, MapSignal signal) {
		EnumFacing dir = getTrunkDirection(world, rootPos);
		BlockPos treePos = rootPos.offset(dir);
		
		TreeHelper.getSafeTreePart(world, treePos).analyse(world, treePos, null, signal);
		
		return signal;
	}
	
	@Override
	public MapSignal analyse(World world, BlockPos pos, EnumFacing fromDir, MapSignal signal) {
		signal.run(world, this, pos, fromDir);//Run inspector of choice
		
		signal.root = pos;
		signal.found = true;
		
		return signal;
	}
	
	@Override
	public int branchSupport(IBlockAccess blockAccess, BlockBranch branch, BlockPos pos, EnumFacing dir, int radius) {
		return dir == EnumFacing.DOWN ? 0x11 : 0;
	}
	
	/**
	 * Rooty Dirt can report whatever {@link DynamicTree} species it wants to be.  By default we'll just 
	 * make it report whatever {@link DynamicTree} the above {@link BlockBranch} says it is.
	 */
	@Override
	public DynamicTree getTree(IBlockAccess blockAccess, BlockPos pos) {
		BlockPos treePos = pos.offset(getTrunkDirection(blockAccess, pos));
		return TreeHelper.isBranch(blockAccess, treePos) ? TreeHelper.getBranch(blockAccess, treePos).getTree(blockAccess, treePos) : null;
	}
	
	public Species getSpecies(IBlockAccess blockAccess, BlockPos pos) {
		BlockPos treePos = pos.offset(getTrunkDirection(blockAccess, pos));
		return TreeHelper.isBranch(blockAccess, treePos) ? TreeHelper.getBranch(blockAccess, treePos).getTree(blockAccess, treePos).getSpeciesForLocation(blockAccess, treePos) : null;
	}
	
	@Override
	public int getMobilityFlag() {
		return 2;
	}
	
	///////////////////////////////////////////
	// RENDERING
	///////////////////////////////////////////
	
	public IIcon dirtIcon;
	public IIcon grassIcon;
	public IIcon myceliumIcon;
	public IIcon podzolIcon;
		
	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(net.minecraft.world.IBlockAccess blockAccess, int x, int y, int z, int side) {
		if(RendererRootyDirt.renderPass == 1) {//First Pass
			switch(side) {
				case 0: return dirtIcon;//Bottom
				case 1: switch(RendererRootyDirt.renderType) {//Top
					case GRASS: return Blocks.grass.getIcon(side, 0);
					case MYCELIUM: return Blocks.mycelium.getIcon(side, 0);
					case PODZOL: return Blocks.dirt.getIcon(side, 2);
					default: return Blocks.dirt.getIcon(side, 0);
					}
				default: switch(RendererRootyDirt.renderType) {//All other sides
					case GRASS: return grassIcon;
					case MYCELIUM: return myceliumIcon;
					case PODZOL: return podzolIcon;
					default: return dirtIcon;
				}
			}
		} else {//Second Pass
			if(RendererRootyDirt.renderType == RenderType.GRASS) {
				if(side == 1) {//Top
					return Blocks.grass.getIcon(side, 0);
				} else if(side != 0) {//NSWE
					return BlockGrass.getIconSideOverlay();
				}
			}
		}

		return dirtIcon;//Everything else
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int metadata) {
		if(side == 1) {
			return Blocks.dirt.getIcon(side, 0);
		}
		return dirtIcon;
	}

	public RenderType getRenderType(BlockAccess blockAccess, int x, int y, int z) {
		
		final int dMap[] = {0, -1, 1};
		
		for(int depth = 0; depth < 3; depth++) {
			for(EnumFacing d: EnumFacing.HORIZONTALS) {
				BlockPos pos = new BlockPos(x + d.getFrontOffsetX(), y + dMap[depth], z + d.getFrontOffsetZ());
				IBlockState mimic = blockAccess.getBlockState(pos);

				if(mimic.equals(Blocks.grass)) {
					return RenderType.GRASS;
				} else if(mimic.equals(Blocks.mycelium)) {
					return RenderType.MYCELIUM;
				} else if(mimic.equals(Blocks.dirt, 2)) {
					return RenderType.PODZOL;
				}
			}
		}
		
		return RenderType.DIRT;//Default to plain old dirt
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public boolean shouldSideBeRendered(net.minecraft.world.IBlockAccess access, int x, int y, int z, int side) {
		
		if(super.shouldSideBeRendered(access, x, y, z, side)) {
			if(RendererRootyDirt.renderPass == 1) {//First Pass
				if(RendererRootyDirt.renderType == RenderType.GRASS) {
					return side != 1;//Don't render top of grass block on first pass	
				}
				return true;//Render all sides of dirt, mycelium and podzol block on first pass
			} else {//Second Pass
				if(RendererRootyDirt.renderType == RenderType.GRASS) {
					return side != 0;//Don't render bottom of grass block on second pass	
				}
				return false;//Render nothing for dirt, mycelium and podzol block on second pass
			}
		}

		return false;
	}

	
	@Override
	@SideOnly(Side.CLIENT)
	public int colorMultiplier(net.minecraft.world.IBlockAccess blockAccess, int x, int y, int z) {
		if(RendererRootyDirt.renderType == RenderType.GRASS && RendererRootyDirt.renderPass == 2) {
			return Blocks.grass.colorMultiplier(blockAccess, x, y, z);
		} else {
			return super.colorMultiplier(blockAccess, x, y, z);
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void registerBlockIcons(IIconRegister register) {
		dirtIcon = register.registerIcon(ModConstants.MODID + ":" + "rootydirt-dirt");
		grassIcon = register.registerIcon(ModConstants.MODID + ":" + "rootydirt-grass");
		myceliumIcon = register.registerIcon(ModConstants.MODID + ":" + "rootydirt-mycelium");
		podzolIcon = register.registerIcon(ModConstants.MODID + ":" + "rootydirt-podzol");
	}

	@Override
	public int getRenderType() {
		return RendererRootyDirt.renderId;
	}
	
	///////////////////////////////////////////
	// ISSITS
	///////////////////////////////////////////
	
	@Override
	public boolean isRootNode() {
		return true;
	}
	
	@Override
	public boolean isBranch() {
		return false;
	}
	
}
