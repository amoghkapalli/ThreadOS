//Written by Amogh Kapalli
public class Directory { 
    private static int maxChars = 30; // max characters of each file name 

    // Directory entries 
    private int fsizes[];        // each element stores a different file size. 
    private char fnames[][];    // each element stores a different file name. 

    public Directory( int maxInumber ) { // directory constructor 
       fsizes = new int[maxInumber];     // maxInumber = max files 
       for ( int i = 0; i < maxInumber; i++ )  
           fsizes[i] = 0;                 // all file size initialized to 0 
       fnames = new char[maxInumber][maxChars]; 
       String root = "/";                // entry(inode) 0 is "/" 
       fsizes[0] = root.length( );        // fsize[0] is the size of "/". 
       root.getChars( 0, fsizes[0], fnames[0], 0 ); // fnames[0] includes "/" 
    } 
    public void bytes2directory( byte data[] ) { 
        // assumes data[] contains directory information retrieved from disk                                                      
        // initialize the directory fsizes[] and fnames[] with this data[]                                                        
        int offset = 0; 
        for ( int i = 0; i < fsizes.length; i++, offset += 4 ) 
            fsizes[i] = SysLib.bytes2int( data, offset ); 
 
        for ( int i = 0; i < fnames.length; i++, offset += maxChars * 2 ) { 
            String fname = new String( data, offset, maxChars * 2 ); 
            fname.getChars( 0, fsizes[i], fnames[i], 0 ); 
        } 
    } 
 
    public byte[] directory2bytes( ) { 
        // converts and return directory information into a plain byte array                                                      
        // this byte array will be written back to disk                                                                           
        byte[] data = new byte[fsizes.length * 4 + fnames.length * maxChars * 2]; 
        int offset = 0; 
        for ( int i = 0; i < fsizes.length; i++, offset += 4 ) 
            SysLib.int2bytes( fsizes[i], data, offset ); 
 
        for ( int i = 0; i < fnames.length; i++, offset += maxChars * 2 ) { 
            String tableEntry = new String( fnames[i], 0, fsizes[i] ); 
            byte[] bytes = tableEntry.getBytes( ); 
            System.arraycopy( bytes, 0, data, offset, bytes.length ); 
        } 
        return data; 
    } 
 
    public short ialloc( String filename ) { 
        // filename is the one of a file to be created. 
        // allocates a new inode number for this filename
        if(filename.length()>30){
            return -1;
        }

        for(int i = 0; i < fsizes.length; i++) {
            if(fsizes[i] == 0) {
                //Because maxChar is the maximum length that you can put inside
                //the fname, get the minimum of those two to put it in.
                fsizes[i] = Math.min(filename.length(), maxChars);
                
                //Take the filename string and break into char and put it 
                //inside fnames.
                filename.getChars(0, fsizes[i], fnames[i], 0);
                return (short)i;
            }
        }
        //If you cannot add it anywhere, return -1.
        return -1;
    } 
 
    public boolean ifree( short iNumber ) { 
        // deallocates this inumber (inode number) 
         // the corresponding file will be deleted. 
        if ((iNumber >= 0) && (iNumber <= fsizes.length)) {
            // checks if there is something allocated at the index
            if (fsizes[iNumber] < 0) {
                return false;
            }
            fsizes[iNumber] = 0;
            return true;
        }
        return false;
    } 
 
    public short namei( String filename ) { 
        // returns the inumber corresponding to this filename   
        for (int i = 0; i < fsizes.length; i++) {
            //Create a temporary string to compare filename and fnames[i]
            //fnames[i] being the string, 0 being the offset, fsize[i] is length.	
            String temp = new String(fnames[i], 0, fsizes[i]);
            
            //If the filename and temp is the same then return the index.
            if (fsizes[i] > 0 && filename.equals(temp)) {
                return (short) i;
            }
        }
        return -1;
    }
}