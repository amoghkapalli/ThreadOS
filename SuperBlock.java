//Written by Amogh Kapalli
import java.util.*;
class SuperBlock {
    private final int defaultInodeBlocks = 64;
    public int totalBlocks;
    public int totalInodes;
    public int freeList;

    public SuperBlock(int diskSize) {
        // read the superblock from the disk
        // check disk contents are valid
        // if invalid, call format ( )
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.rawread(0, superBlock);
        totalBlocks = SysLib.bytes2int(superBlock, 0);
        totalInodes = SysLib.bytes2int(superBlock, 4);
        freeList = SysLib.bytes2int(superBlock, 8);

        if (totalBlocks == diskSize && totalInodes > 0 && freeList >= 2) {
			return;
		}
		else {
			totalBlocks = diskSize;
			format(defaultInodeBlocks);
		}

    }

    void sync() {
        // write back in-memory superblock to disk: SysLib.rawwrite(0, superblock);
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, superBlock, 0);
		SysLib.int2bytes(totalInodes, superBlock, 4);
		SysLib.int2bytes(freeList, superBlock, 8);
		SysLib.rawwrite(0, superBlock);
    }

    void format(int files) {
        //initialze the superblock
        //initialize each inode and immediately write it back to disk
        //initialize free blocks
        totalInodes = files;
        for(int i = 0; i < totalInodes; i++) {
            Inode temp = new Inode();
            temp.flag = 0;
            temp.toDisk((short) i);
        }

        freeList = 2 + (totalInodes / 16);
        for (int i = freeList; i < totalBlocks; i++) {
            byte[] info = new byte[Disk.blockSize];
            SysLib.int2bytes(i + 1, info, 0);
            SysLib.rawwrite(i, info);
        }
    }

    public int getFreeBlock() {
        if (freeList < 0 || freeList > totalBlocks){
			return -1;			
		}
        //block to be freed
        int freeBlock = freeList;
        //empty
        byte[] blockInfo = new byte[Disk.blockSize];
        // gets the content of the freeList block
        SysLib.rawread(freeList, blockInfo);
        SysLib.int2bytes(0, blockInfo, 0);
        SysLib.rawwrite(freeBlock, blockInfo);
        this.freeList = SysLib.bytes2int(blockInfo, 0);
        return freeBlock;
        
    }

    public boolean returnBlock(int oldBlockNumber) {
        // return this old block to the free list. The list can be a stack
        byte[] list = new byte[Disk.blockSize];
        if (oldBlockNumber < 0 || oldBlockNumber > totalBlocks){
			return false;
		}
        SysLib.int2bytes(freeList, list, 0);
        // write the content of the given block to new block
		SysLib.rawwrite(oldBlockNumber, list);
        freeList = oldBlockNumber;
        return true;
    }
}