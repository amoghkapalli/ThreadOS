//Written by Amogh Kapalli
public class FileSystem {
    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;
	//constructor instantiating superblock, directory and filetable
    public FileSystem(int blocks) {
		byte[] dirData;
		superblock = new SuperBlock(blocks);
		directory = new Directory(this.superblock.totalInodes);
		filetable = new FileTable(directory);
		// read the "/" file from disk
		int dirOpen = open("/", "r");
		int sizeDirectory = fsize(dirOpen);
		if (sizeDirectory > 0) {
			dirData = new byte[sizeDirectory];
			//reads data from directory
			read(openDir, dirData);
			directory.bytes2directory(dirData);
		}
		//closes
		close(dirOpen);
	}
	
	//opens file with filename in the specific mode
	public int open( String filename, String mode ) {
		// must provide valid filename and mode
		if (filename.equals("") || mode.equals("")){
			return -1;
		}
		//creates FTE and allocates it with the filename and mode
		FileTableEntry ftEnt=filetable.falloc(filename, mode);
		//checking if mode is in write and if so all blocks are deleted and checking if process is successful
		if(mode.equals("w")){
			if(deallocAllBlocks(ftEnt)==false){
				return -1;
			}
		}
	   	return this.filetable.getNum(ftEnt);
	}

	private boolean deallocAllBlocks(FileTableEntry ftEnt) {
        // check if inode is being used
        if (ftEnt.inode.count < 0 || ftEnt == null) {
            return false;
        }

        // Deallocate the indirect blocks,
        byte[] data;
		int indirectData = ftEnt.inode.indirect;

		if (indirectData != -1) {
				data = new byte[Disk.blockSize];
				SysLib.rawread(indirectData, data);
				ftEnt.inode.indirect = -1;
			
		} else {
			data = null;
		}
        
        if (data != null){
            byte offset = 0;
            // Get all the block that is pointed to by the indirect block
            short blockID = SysLib.bytes2short(data, offset);
            // And make it free. 
            while (blockID != -1)
            {
                superblock.returnBlock(blockID);
                blockID = SysLib.bytes2short(data, offset);
            }
        }
		for (int i = 0; i < 11; i++) {
            if (ftEnt.inode.direct[i] != -1) {
                // deallocate direct blocks if the block hasn't been deallocated yet     
                this.superblock.returnBlock(ftEnt.inode.direct[i]);
                // set the block to deallocated                                          
                ftEnt.inode.direct[i] = -1;
            }
        }

        // Write the inode to disk
        ftEnt.inode.toDisk(ftEnt.iNumber);
        return true;
    }
	//calling the superblocks format method
	public int format(int files){
        if (files > 0){
            this.superblock.format(files);
	    	directory = new Directory(superblock.totalInodes);
            filetable = new FileTable(directory);
            return 0;
        }
        return -1;
    }

	public int read( int fd, byte[] buffer ) {
		FileTableEntry ftEnt = this.filetable.getEntry(fd);
		if(ftEnt == null|| ftEnt.inode == null || ftEnt.mode.equals("w") || ftEnt.mode.equals("a")){
			return -1;
		}
		if(buffer==null){
			return -1;
		}
		int bufferLen = buffer.length;
		int trackRead = 0;
		int sizeLeft = 0;
		synchronized(ftEnt) {
			while (ftEnt.seekPtr < fsize(fd) && bufferLen > 0) {
                int blockNum = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
		
				if (blockNum == -1) 
					return -1;
				else if(blockNum < 0 || blockNum > superblock.totalBlocks)
					break;

				byte[] tempBlock = new byte[Disk.blockSize];
				SysLib.rawread(blockNum, tempBlock);
				
				int offset = ftEnt.seekPtr % Disk.blockSize;
				int remainingBlocks = Disk.blockSize - offset;
				int remaining = fsize(fd) - ftEnt.seekPtr;
				
				int smallerBlockData = Math.min(remainingBlocks, bufferLen);
				sizeLeft = Math.min(smallerBlockData, remaining);
				// finding out how many bytes remaining that need to be read.  
				System.arraycopy(tempBlock, offset, buffer, trackRead, sizeLeft);
				
				// Update bytes read so far
				bufferLen -= sizeLeft;
				// Update seek pointer
				ftEnt.seekPtr += sizeLeft;
				// Update bytes left to read
				trackRead += sizeLeft;
			}
			return -1;
		}
	}


	public int write( int fd, byte[] buffer ) {	
		FileTableEntry ftEnt = this.filetable.getEntry(fd);
        // Validate that we are in write mode                                                           
        if (ftEnt.mode.equals( "r") || ftEnt == null || buffer == null){
            return -1;
        }
		int readBytes = 0; // already being read                                                         
		int bufferLength = buffer.length;
		int seekPtr = 0;

		synchronized (ftEnt) {
			// Continue writing when we still have length in the buffer                   
			// true as long as we have bytes left to read  
			if (ftEnt.mode.equals("a")) {
				seekPtr = seek(ftEnt, 0, 2);
			} else {
				seekPtr = ftEnt.seekPtr;
			}
			while (bufferLength > 0) {
				int currBlock = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
				if (currBlock == -1) {// invalid block                                                 
					// Get the next free block   
					short availableFreeblock = (short) superblock.getFreeBlock();
					// Update the block with the inode                                              
					int result = ftEnt.inode.registerTargetBlock(ftEnt.seekPtr, availableFreeblock);

					// Depending on the return value, we will have different ways to handle
					// When result = -3 , it means that the indirect block is unavaible albe            
					// So we need to make use of it
					if (result == -3) {
						// Indirect is unassigned, attempt to assign it                                
						short nextFreeBlock = (short) superblock.getFreeBlock();
						if (!ftEnt.inode.registerIndexBlock(nextFreeBlock)){
							return -1; //error    
						}
						//Attempt to update it                                                        
						if (ftEnt.inode.registerTargetBlock(ftEnt.seekPtr, availableFreeblock) != 0) {
							return -1; //error                                                           
						}
                    }                  
                    else if (result == -1 || result == -2) {
						return -1;
                    }
		    		currBlock = availableFreeblock;
                }

				if (currBlock >= superblock.totalBlocks) {
					ftEnt.inode.flag = 4;
					break;
				}

				// Create a temporary block to copy into 
				byte[] tempData = new byte[Disk.blockSize];

				// Read the contents of the current block into the temp block
				SysLib.rawread(currBlock, tempData);
				// Get the current pointer offset (zero means new block)                                        
				int offset = ftEnt.seekPtr % Disk.blockSize;

				int remaining = Disk.blockSize - offset;
				int smaller = Math.min(remaining, bufferLength);

				System.arraycopy(buffer, readBytes, tempData, offset, smaller);
				SysLib.rawwrite(currBlock, tempData);
				// Update the seek pointer poingting to the next location               
				ftEnt.seekPtr += smaller;
				readBytes += smaller;
				// Decrement the size meaning that we have used this much space in writing                       
				bufferLength -= smaller;
				// If we reached the end of the inode                                                            
				if (ftEnt.seekPtr > ftEnt.inode.length){
				// Set the pointer to the end of the inode            
					ftEnt.inode.length = ftEnt.seekPtr;
        		}
        	}
			// if the file size increased, update Inode
			if (seekPtr > ftEnt.inode.length) {
				ftEnt.inode.length = seekPtr;
			}

			// set new seekPtr
			seek(ftEnt, readBytes, 1);

			// set flag
			if (ftEnt.inode.flag != 4) {
			ftEnt.inode.flag = 1;
			}
			// Save the inode to disk                                                           
			ftEnt.inode.toDisk(ftEnt.iNumber);  
		}
		return readBytes;
    }

	public boolean close(int fte) {
		FileTableEntry ftEnt = this.filetable.getEntry(fte);
		
		if (ftEnt == null)
			return false;
		deallocAllBlocks(ftEnt);
		ftEnt.count--;
        if (ftEnt.count <= 0){
            return filetable.ffree(ftEnt);
		}
        return true;
	}

	
	int seek(FileTableEntry ftEnt, int offset, int whence) {
		//FileTableEntry ftEnt = this.filetable.getEntry(fd);
		int fd=this.filetable.getNum(ftEnt);
		if (ftEnt == null) {
			return -1;
		}
		synchronized(ftEnt){
			if (whence != 0 && whence != 1 && whence != 2) {
				return -1;
			}
			if(whence==0){
				if (offset <= fsize(fd) && offset >= 0){
					ftEnt.seekPtr = offset;
				}
			}
			if(whence==1){
				if (offset+ftEnt.seekPtr <= fsize(fd) && offset >= 0){
					ftEnt.seekPtr += offset;
				}
			}
			if(whence==2){
				if (fsize(fd) + offset >=0){
					ftEnt.seekPtr = fsize(fd) + offset;
				}
			}
			if(ftEnt.seekPtr<0){
				ftEnt.seekPtr=0;
			}
		}
		return ftEnt.seekPtr;
	}
	public boolean delete(int fte) {
		FileTableEntry ftEnt = this.filetable.getEntry(fte);
		short iNum = ftEnt.iNumber;
		return close(fte) && directory.ifree(iNum);
    }
	public int fsize(int fte) {
		FileTableEntry ftEnt = this.filetable.getEntry(fte);
		// return -1 if fte is null
		if (ftEnt == null){
			return -1;
		}
		return ftEnt.inode.length;
	}
}