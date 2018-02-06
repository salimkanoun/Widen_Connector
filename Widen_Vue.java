/**
Copyright (C) 2017 KANOUN Salim
This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public v.3 License as published by
the Free Software Foundation;
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.SwingWorker;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;

public class Widen_Vue implements PlugIn {
	
	GUI gui = new GUI(this);
	ArrayList<ImagePlus> imagePlusList;

	public static void main(String[] args) {
		Widen_Vue widen= new Widen_Vue();
		widen.gui.pack();
		widen.gui.setLocationRelativeTo(null);
		widen.gui.setSize(widen.gui.getPreferredSize());
		widen.gui.setVisible(true);
	}
	
	protected void getZip(URL url) throws IOException {
		Path folder = Files.createTempDirectory("Widen_");
		File zip=new File(folder.toAbsolutePath()+File.separator+"image.zip");
		SwingWorker<Void, Void> sw = new SwingWorker<Void,Void>(){
		      protected Void doInBackground() throws Exception {
		        saveUrl(zip.getAbsolutePath().toString(), url); 
		        gui.getLabelProgress().setText("Download Done, Unzipping");
		        unzip(folder ,zip);
		        zip.deleteOnExit();
		        recursiveDeleteOnExit(folder);
		        File imageUnizped=new File(folder.toAbsolutePath() + File.separator + "DICOM" );
		        imagePlusList=new ArrayList<ImagePlus>();
		        displayDirectoryContents(imageUnizped);
		        runPetCtViewer();
		        return null;
		      }
		         
		      public void done(){            
		      gui.getLabelProgress().setText("Done");
		      }         
		    };
		//On lance le SwingWorker
		sw.execute();		
		
	}
	
	private void displayDirectoryContents(File unzipedFoler) {
		String[] directories = unzipedFoler.list(new FilenameFilter() {
			  @Override
			  public boolean accept(File current, String name) {
			    return new File(current, name).isDirectory();
			  }
			});
		
		
		if (directories.length>0) {
			for (int i=0 ; i<directories.length; i++) {
				displayDirectoryContents(new File (unzipedFoler+File.separator+directories[i]));
			}
		}
		else {
			FolderOpener folderOpener=new FolderOpener();
			ImagePlus stackImage=folderOpener.openFolder(unzipedFoler.getPath());
			imagePlusList.add(stackImage);
			stackImage.show();
		
		}
	}
	
	private void unzip(Path path, File zip) throws IOException {
		byte[] buffer = new byte[1024];
		
		//get the zip file content
    	ZipInputStream zis;
		zis = new ZipInputStream(new FileInputStream(zip));
		
    	//get the zipped file list entry
    	ZipEntry ze = zis.getNextEntry();
    	
    	while(ze!=null){
     	   	String fileName = ze.getName();

     	    
     	   File newFile = new File(path.toAbsolutePath() + File.separator + "DICOM" +File.separator+ fileName);
            
            if (ze.isDirectory()) {
         	// if the entry is a directory, make the directory
                newFile.mkdirs();
            }
            else {
            	newFile.getParentFile().mkdirs();
                 //create all non exists folders else you will hit FileNotFoundException for compressed folder
                 FileOutputStream fos = new FileOutputStream(newFile);
                 int len;
                 while ((len = zis.read(buffer)) > 0) {
            		fos.write(buffer, 0, len);
                 }

                 fos.close();
                 
            }
            ze = zis.getNextEntry();
     	}
    	

        zis.closeEntry();
    	zis.close();
    	
    	
	}
	
	private void saveUrl(final String filename, final URL url)
	        throws MalformedURLException, IOException {
		URLConnection conn = url.openConnection();
	    int size = conn.getContentLength();
	    BufferedInputStream in = null;
	    FileOutputStream fout = null;
	    try {
	        in = new BufferedInputStream(url.openStream());
	        fout = new FileOutputStream(filename);

	        final byte data[] = new byte[1024];
	        int count;
	        double cumulativeCount=0;
	        double percent;
	        while ((count = in.read(data, 0, 1024)) != -1) {
	            cumulativeCount+=count;
	            percent=(cumulativeCount*100)/size; 
	            gui.getLabelProgress().setText("Downloaded "+String.valueOf(Math.round(percent))+" % ");
				fout.write(data, 0, count);
	        }
	    } finally {
	        if (in != null) {
	            in.close();
	        }
	        if (fout != null) {
	            fout.close();
	        }
	    }
	}
	
	private void recursiveDeleteOnExit(Path path) throws IOException {
		  Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
		    @Override
		    public FileVisitResult visitFile(Path file,
		        BasicFileAttributes attrs) {
		      file.toFile().deleteOnExit();
		      return FileVisitResult.CONTINUE;
		    }
		    @Override
		    public FileVisitResult preVisitDirectory(Path dir,
		        BasicFileAttributes attrs) {
		      dir.toFile().deleteOnExit();
		      return FileVisitResult.CONTINUE;
		    }
		  });
		}
	
	private void runPetCtViewer() {
		String seriesUIDs = ChoosePetCt.buildSeriesUIDs(imagePlusList);
		if( seriesUIDs == null) return;
		if( seriesUIDs.startsWith("2CTs")) seriesUIDs = "";
		IJ.runPlugIn("Pet_Ct_Viewer", seriesUIDs);
		wait4bkgd();
	}
	
	private void wait4bkgd() {
		Integer i = 0, j;
		while( ChoosePetCt.loadingData == 1 || ChoosePetCt.loadingData == 3) {
			mySleep(200);
			i++;
			if( (i % 20) == 0 && ChoosePetCt.loadingData == 1) {
				ImageJ ij = IJ.getInstance();
				if( ij != null) ij.toFront();
				j = i/5;
				IJ.showStatus("Loading data, please wait " + j.toString());
			}
		}
	}
	
	private void mySleep(int msec) {
		try {
			Thread.sleep(msec);
		} catch (Exception e) { ChoosePetCt.stackTrace2Log(e);}
	}


	@Override
	public void run(String arg0) {
		Widen_Vue widen= new Widen_Vue();
		widen.gui.pack();
		widen.gui.setLocationRelativeTo(null);
		widen.gui.setSize(widen.gui.getPreferredSize());
		widen.gui.setVisible(true);
	
	}

}
