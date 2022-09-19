//Written by Amogh Kapalli and Amogh Heroor
import java.util.*;
public class FileTable {

    private Vector<FileTableEntry> table;       //the actual entity of this file table
    private Directory dir;      //the root directory

    public FileTable(Directory directory) {     //constructor
        table = new Vector<FileTableEntry>( );    //instantiate a file (structure) table
        dir = directory;        //recieve a reference to the Directory
    }                           //from the file system

    public synchronized FileTableEntry falloc(String filename, String mode) {
        short iNumber = -1;
        Inode inode = null;

        while (true) {
            //allocate a new file (structure) table entry for this file name
            //allocate/retrieve and register the corresponding inode using dir
            iNumber = (filename.equals("/") ? (short)0 : dir.namei(filename));
            //goes case by case depending on where the flag is and what the mode is
            //depending on the condition the system either waits or sets the iNumber to -1
            if(iNumber>=0){
                inode = new Inode(iNumber);
                if (mode.equals("r")) {
                    if (inode.flag == (short)2) { //flag = read
                        break;
                    }
                    else if (inode.flag == (short)3) { //flag = write
                        try {
                        wait();
                        }
                        catch(InterruptedException e){}
                    }
                    else if (inode.flag == (short)6) { //flag = to be deleted
                        iNumber = -1;
                        return null;
                    }
                }
                else if (mode.equals("w")) {
                    if (inode.flag == (short)3) { // flag = write
                        break;
                    }
                    else if (inode.flag == (short)2) { //flag = read
                        try {
                            wait();
                        }
                        catch(InterruptedException e){}
                    }
                    else if (inode.flag == (short)6) { //flag = to be deleted
                        iNumber = -1;
                        return null;
                    }
                }
            }
        }
        //increment this indoe's count
        inode.count++;
        //immediately write back this inode to the disk
        inode.toDisk(iNumber);
        FileTableEntry e = new FileTableEntry(inode, iNumber, mode);
        table.addElement(e); //create a table entry and register it.
        //return a reference to this file (structure) table entry
        return e;
    }

    public synchronized boolean ffree(FileTableEntry e) {
        //recieve a file table entry reference
        //free this file table entry
        //return true if this file table entry found in my table

        if(e==null){
            return true;
        }
		// the FTE was not found in my table
		if (!table.removeElement(e)){
			return false;			
		}
        //decrements the inode count and sets the flag to not being used as the element is remeoved
        else{
            e.inode.count--;
            if(e.inode.flag==1 ||e.inode.flag==2){
                e.inode.flag = 0;
            }
            else if(e.inode.flag==4 ||e.inode.flag==5){
                e.inode.flag = 3;
            }
            
	    
            //save this corresponding inode to the disk
            e.inode.toDisk(e.iNumber);
            
            //Set e to null
            e = null;
            
            //Wake up
            notify();
            return true;
        }
    }
    //checks for empty
    public synchronized boolean fempty() {
        return table.isEmpty();
    }
    //returns the value of the FTE in the table
    //helper method for the file system
    public FileTableEntry getEntry(int num) {
        return table.get(num);
    }
    //helper method for file system
    //get index number of table location of FTE
    public int getNum(FileTableEntry ent) {
        for(int i = 0; i < table.capacity(); i++) {
            if(table.get(i) == ent)
                return i;
        }
        return -1;
    }

}	