package stegers;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Component;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.*;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import stegers.chord;
import stegers.contour;
import stegers.convol;
import stegers.correctionx;
import stegers.correctx;
import stegers.crossref;
import stegers.doublepoint;
import stegers.junction;
import stegers.offset;
import stegers.position;
import stegers.region;
import stegers.contour_class;
import stegers.ThresholdSal;
import stegers.junc_pnt;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Arrow;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;


import ij.io.FileSaver;
import ij.io.Opener;



public class Steger_source implements Runnable {
	
////........................................... Constants......................................................... ////
	
/** The pixel boundaries need to be enlarged slightly, as it can happens for neighboring pixels a and b that pixel a says 
 * a maximum lies within pixel b and vice versa.  This presents no problem since linking algorithm will take care of this.  **/
	//String str= "/Users/winja/Desktop/SIV_WORK/CurveTrace/mt_trace/data/def.jpg" ;
//	String str= "/Users/winja/Desktop/SIV_WORK/CurveTrace1/mt_trace/data/Arya.png" ;
	String str;
	public static double PIXEL_BOUNDARY = 0.6;
	public static double SIGMA = 1.8;
	public static boolean MODE_LIGHT = true;                 /** Extract bright lines if true and extract dart lines otherwise*/
	public static boolean extend_lines = true;
	public static boolean split_lines = true;
	public static boolean correct_pos = true;
	public static boolean compute_width = true;
	public static boolean DEBUG_MODE = false;
	public static boolean DEBUG_show_subpixel = false;
	public static boolean DEBUG_show_tracing = false;
	public static boolean DEBUG_show_extensions = false;
	public static boolean DEBUG_show_eigenvector = false;
	public static boolean DEBUG_show_eigenvalue = false;
	public static boolean DEBUG_show_junctions = false;
	public static int nRoiLinesType = 1;                         /** Type of lines added to RoiManager and Overlay*/
	public static int nRoiLinesColor = 0;                        /** Color of lines added to RoiManager and Overlay*/
	public static int nMinNumberOfNodes = 60;                     /** Minimum number of nodes that line can have. Shorter lines will be removed */
	public static double angledev = 10;   
	//int nRoiColor;
	
	/** Max angular diff of neighboring line points allowed during linking. If all feasible neighbors have a larger angular diff the
	   line is stopped at that point. */
	public static double MAX_ANGLE_DIFFERENCE = Math.PI/18.0;
	
	/** Max length by which a line is possibly extended in order to find a junction with another line. */
	//public static double MAX_LINE_EXTENSION = SIGMA*2.5;
	public static double MAX_LINE_EXTENSION = SIGMA*2.5;
	public static double MAX_LINE_WIDTH = (2.5*SIGMA);                    /** Maximum search line width. **/
	
	/** for very narrow lines the facet model width detection scheme sometimes extracts the line width too narrow. Since correction 
	 	function has a very steep slope in that area, this will lead to lines of almost zero width, especially since bilinear interpolation
	   in correct.c will tend to overcorrect.  Therefore it is wise to make the extracted line width slightly larger before correction.  **/
	public static double LINE_WIDTH_COMPENSATION =1.05;
	public static double MIN_LINE_WIDTH = 0.1;                   /** Minimum line width allowed (used for outlier check in fix_locations()) */
	public static double MAX_CONTRAST=275.0;                     /** Maximum contrast allowed (used for outlier check in fix_locations()) */
	
	
	public long width;                                                           /** Image width */
	public long height;                                                           /** Image height */
	int nFrame = 0;                                                              /** current slice (frame) */
	int nStackSize;                                                               /** total number of frames in image */
	int nCurrentSlice;                                                             /** current slice in stack */
	ResultsTable ptable = ResultsTable.getResultsTable();                         /** Results table */
	int nContNumTotal = 0, g;                                                        /** Total number of contours found */
	boolean bClearResults = false;                                               /** whether results table had been cleaned*/
	
	
	HashMap<Integer, ArrayList<Integer>> matchPair= new HashMap<Integer, ArrayList<Integer>>();
	
	/** This table contains the 3 appropriate neighbor pixels that the linking algo must examine.
 	It is indexed by the octant the current line angle lies in, e.g., 0 if the angle in degrees lies within [-22.5,22.5]. */
	public static int dirtab[][][] = {{ {  1, 0 }, {  1,-1 }, {  1, 1 } },  { {  1, 1 }, {  1, 0 }, {  0, 1 } },
							  { {  0, 1 }, {  1, 1 }, { -1, 1 } }, { { -1, 1 }, {  0, 1 }, { -1, 0 } }, 
							  { { -1, 0 }, { -1, 1 }, { -1,-1 } }, { { -1,-1 }, { -1, 0 }, {  0,-1 } },
							  { {  0,-1 }, { -1,-1 }, {  1,-1 } }, { {  1,-1 }, {  0,-1 }, {  1, 0 } }  };

	/** This table contains the two neighbor pixels that the linking algorithm
   should examine and mark as processed in case there are double responses. */
	public static int cleartab[][][] = { { {  0, 1 }, {  0,-1 } }, { { -1, 1 }, {  1,-1 } },
								 { { -1, 0 }, {  1, 0 } }, { { -1,-1 }, {  1, 1 } },
								 { {  0,-1 }, {  0, 1 } }, { {  1,-1 }, { -1, 1 } },
								 { {  1, 0 }, { -1, 0 } }, { {  1, 1 }, { -1,-1 } } };
	
	public  ImagePlus imp; 	                    /** main image window */
	public ImageProcessor ip;                   /** currently active processor */
	RoiManager roi_manager;                    /** link to roi manager */
	Overlay image_overlay;                     /** overlay of main image */
	ArrayList<contour> cont;                   /** Array of all found contours */
	ArrayList<junction> junc;              		/** Array containing junctions */
	ArrayList<junc_pnt>jun;
	ArrayList<crossref> cross;

	ArrayList<ArrayList<Integer> > contourList =  new ArrayList<ArrayList<Integer> >();
	
	ArrayList<pointcontour> tcont;
	ArrayList<pointcontour> pointtmcont;
	ArrayList<pointcontour> slicecontour;
	double low, high;                          /** saliency lower and upper threshold */
	double[] eigval;                           /**Largest eigenvalue of image */
	double[] normx, normy;                     /** eigenvector x, y component of image */
	double[] gradx, grady;                     /** x, y derivative of image */
	double[] posx, posy;                       /** subpixel resolved position of maxima in x, y*/
	int[] ismax;                                /** thresholded array */
	short[] label;
	long [] indx;
	int rows=1, columns=5;
	int a=2, b=20,c=100;
    static double PI = 3.14159265;
    File directory;
	String homeDir;
	int overlap=200;
	int sliceN=-1;
	 int subimage_Width ,subimage_Height;
////.............................................  Main  --------------------	
	public static void main(String[] args) {
		Steger_source r1= new Steger_source();       // TODO Auto-generated method stub
		Thread t1 =new Thread(r1);    
	    t1.start();                                 // this will call run() method  
	}
	
/**........................Run Method...............................*/	
	public void run() {
		System.out.println("Running");             // TODO Auto-generated method stub
		 homeDir = System.getProperty("user.home");
	        directory = new File(homeDir + File.separator + ".sub-images");
	        directory.mkdir();
		
			
		   pyj();
		int maxattempt=10;
	       int attempt=0;
	       boolean success = false;
		 while(!success && attempt < maxattempt) { 
		try {
			
		//	pyj();
			
			JFileChooser chooser = new JFileChooser();
			chooser.addChoosableFileFilter(new FileNameExtensionFilter("Image files",new String[] { "png", "jpg","jpeg", "jpeg" }));
			int returnVal = chooser.showOpenDialog(null);
			if(returnVal == JFileChooser.APPROVE_OPTION) {
			   System.out.println("You chose to open this directory: " +
			        chooser.getSelectedFile().getAbsolutePath());
			   str=  chooser.getSelectedFile().getAbsolutePath();
			}
			
			int current_img = 0;
		       
		   
		    BufferedImage image = ImageIO.read(new File(str));  
	 
			String slice = JOptionPane.showInputDialog("Enter no. of slice ");
		    columns = Integer.parseInt(slice);
			
			
			
		    subimage_Width = image.getWidth() / columns;
	        subimage_Height = image.getHeight() / rows;
	      //  System.out.println(subimage_Width+"width pixels size");     	
	     
	        BufferedImage imgs[] = new BufferedImage[columns];	
			/*
	        for (int i = 0; i < rows; i++)
	        {
	            for (int j = 0; j < columns; j++)
	            {   int src_first_x,src_first_y, width;
	                // Creating sub image
	                imgs[current_img] = new BufferedImage(subimage_Width, subimage_Height, image.getType());
	                Graphics2D img_creator = imgs[current_img].createGraphics();

	                if(i==0) {
	                	 width = subimage_Width;
	                	 src_first_x = (subimage_Width * j);
	 	                 src_first_y = subimage_Height * i;

	                }
	                else {
	                    width = subimage_Width + overlap;
	                // coordinates of source image
	                   src_first_x = (subimage_Width * j) ;
	                   src_first_y = (subimage_Height * i)- overlap;;
	                }
	                // coordinates of sub-image
	                int dst_corner_x = subimage_Width * j + subimage_Width;
	                int dst_corner_y = subimage_Height * i + subimage_Height;
	                
	                img_creator.drawImage(image, 0, 0, width, subimage_Height, src_first_x, src_first_y, dst_corner_x, dst_corner_y, null);
	                current_img++;
	            }
	        }*/
	        overlap = (int)(subimage_Width  * 0.04);     //4% of subimage overlap
	        for (int i = 0; i < rows; i++) {
	            for (int j = 0; j < columns; j++) {
	                int src_first_x = subimage_Width * j -overlap;
	                int src_first_y = subimage_Height * i ; // subtract the overlap from the starting row coordinate

	                // Check if the calculated starting row coordinate is negative, if so, set it to 0
	                if (src_first_x < 0) {
	                    src_first_x = 0;
	                }

	                int dst_corner_x = subimage_Width * j + subimage_Width;
	                int dst_corner_y = subimage_Height * i + subimage_Height;

	                // Update the subimage width to include the overlapping portion
	                int subimage_Width_overlap = dst_corner_x - src_first_x;
	                
	                BufferedImage subImage = new BufferedImage(subimage_Width_overlap, subimage_Height, image.getType());
	                Graphics2D img_creator = subImage.createGraphics();

	                img_creator.drawImage(image, 0, 0, subimage_Width_overlap, subimage_Height, src_first_x, src_first_y, dst_corner_x, dst_corner_y, null);
	                imgs[current_img] = subImage;
	                current_img++;
	            }
	        }
	        
	        
	        //writing sub-images into image files
	        for (int i = 0; i < columns; i++)
	        {
	            File outputFile = new File(directory.getPath() + File.separator + "img" + i + ".jpg");
	            try {
					ImageIO.write(imgs[i], "jpg", outputFile);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        }
	        System.out.println("Sub-images have been created.");
	        System.out.println("Directory path: " + directory.getPath());
		
		for (int i = 0; i < rows; i++)
        {
            for (int j = 0; j < columns; j++)
            {
		
////......  1. Getting active image processor and parameters ----------------////
		
		if(!get_image_parameters(j))  return;
		
////......  2. Showing parameters dialog -----------------------------------////
		
		if(!show_parameters_dialog()) return;
		
		
////......   3. Computing points and Threshold levels ......................////
		
		compute_line_points();
		
		if(!get_threshold_levels())  return ;
		
////......  4. Creating overlay ............................................////
//		Overlay image_overlay = imp.getOverlay();
//
//		// If the overlay is null, create a new overlay
//		if (image_overlay == null) {
//		    image_overlay = new Overlay();
//		}
		image_overlay = new Overlay();
		IJ.showStatus("Finding lines...");
		IJ.showProgress(0, nStackSize);
		sliceN++;
////........    5. Main operation starts ....................................////
			
	/** set ismax[l] to... 2 if ev[l] is larger than high, 1 if ev[l] is larger than low, 0 otherwise. **/
		for(nFrame=0; nFrame<nStackSize; nFrame++){
			//imp.setSliceWithoutUpdate(nFrame+1);
			//ip = imp.getProcessor();
			//compute_line_points();
			getismax(low,high);                                               //move it to compute contours function
			
			if(DEBUG_show_subpixel)  add_subpixelxy();                        // sub-pixel
			
			compute_contours();                                               // compute contours	
			re_compute_contour();
			if(compute_width)  compute_line_width(gradx,grady);			      // line-width
			
			if(DEBUG_show_eigenvector)  add_eigenvectors();                   // eigen-vectors
			
			if(DEBUG_show_junctions)  add_junctions();                        // junctions	
			//blocksplit();
			matchPair.clear();
			show_contours();                                                 //  overlaying contours
			{
			pointcontour c1= slicecontour.get((int)17);
			pointcontour c2= slicecontour.get((int)2);
			Float x1=   c1.col.get((int)(0)); 
			Float x2=  c1.col.get((int)(1)); 
			 
			Float y1=   c1.row.get((int)(0)); 
			Float y2=  c1.row.get((int)(1)); 
			int nop = (int)c2.num;
			Float x3=   c2.col.get(nop-1); 
			Float x4=  c2.col.get(nop-2); 
			 
			Float y3=   c2.row.get(nop-1); 
			Float y4=  c2.row.get(nop-2);
			 double langle=  angleBetween2Lines(x1,y1,x2,y2,x3,y3,x4,y4);
			
			 double [] p0 =new double[2]; double []p1 =new double[2];
			 double []p2 =new double[2]; 
			 p1[0]= x1.doubleValue();
	  		 p1[1]= y1.doubleValue();
	  			
	  		p0[0]= x3.doubleValue();
	  		p0[1]= y3.doubleValue();
	  			
	  		p2[0]= x4.doubleValue();
	  		p2[1]= y4.doubleValue();
			double pangle =Math.toDegrees(computeAngle(p0,p1,p2));
			 System.out.println("test contour 18 and 3 :"+langle +" & "+pangle);
			
			}
//			  imp.setOverlay(image_overlay);
//			  imp.updateAndRepaintWindow();
//			  imp.show();	
			nContNumTotal+= cont.size();
			if (nContNumTotal>0 && !this.bClearResults){
				ptable = ResultsTable.getResultsTable();
				ptable.setPrecision(5);
				ptable.reset();                                               // erase results table
				this.bClearResults = true;
			}
			if (nContNumTotal>0)                                            //add lines characteristics to results table
				add_results_table();    
			IJ.showProgress(nFrame, nStackSize);                          //add lines characteristics to results table
				
		}
		
////.........   6. output results.........................................////
		
		IJ.showProgress(nStackSize, nStackSize);
		IJ.showStatus("Done.");
		ptable.show("Results");
		imp.setPosition(nCurrentSlice);
		imp.draw();
		imp.setActivated();		
		success=true;
            }
        }
	}
		 catch(OutOfMemoryError e) {
	    	  attempt++;
	    	  JFrame f= new JFrame();
	    	  JOptionPane.showMessageDialog(f, "Number of slices are not sufficient for Memory. Please Choose file again and increase slices");
	      } catch (IOException e1) {
			// TODO Auto-generated catch block
	    	  JFrame f= new JFrame();
	    	  JOptionPane.showMessageDialog(f, "Error!!! Please choose file again...");
			e1.printStackTrace();
		}
	 }
	}
	
	
	

	
	
	/////-----------computing angle between 3consequtive points---------------------////
	//[pjb: 3Dec 2022]Find the angle between three points. P0 is center point
	
	public static double computeAngle(double[] p0, double[] p1, double[] p2) {
        double[] v0 = Steger_source.createVector(p0, p1);
        double[] v1 = Steger_source.createVector(p0, p2);

        double dotProduct = Steger_source.computeDotProduct(v0, v1);

        double length1 = Steger_source.length(v0);
        double length2 = Steger_source.length(v1);

        double denominator = length1 * length2;

        double product = denominator != 0.0 ? dotProduct / denominator
                : 0.0;

        double angle = Math.acos(product);

        return angle;
    }
	
	public static double computeDotProduct(double[] v0, double[] v1) {
        return v0[0] * v1[0] + v0[1] * v1[1] ;
    }
	 public static double length(double[] v) {
	        return Math.sqrt(v[0] * v[0] + v[1] * v[1]);
	    }
	/**
     * Construct the vector specified by two points.
     *
     * @param p0, p1 Points the construct vector between [x,y].
     * @return v Vector from p0 to p1 [x,y].
     */
	public static double[] createVector(double[] p0, double[] p1) {
        double v[] = { p1[0] - p0[0], p1[1] - p0[1] };
        return v;
    }
	
	
////......................computing contours........................////
	
/** This function links the line points into lines. The input to this function are the response of the filter, 
 * i.e., second directional derivative along (nx[l],ny[l]), contained in eigval[l] and sub-pixel position of each line point, 
 * contained in (px[l],py[l]). The parameters low and high are the hysteresis thresholds for the linking, while width and height 
 * are dimensions of five float-images. Linked lines are returned in result, and number of lines detected is returned in num_result. * */	
	public void compute_contours(){		
		int i, k, l, pos, nextpos, nexti, it, num_line, num_add, octant, last_octant, m=0, j=0;
		long num_pnt, num_cont, num_junc,    x, y,        begin, end,         indx_max, maxx, maxy,        nextx, nexty , area=0;
		double nx, ny, mx, my, max, px, py, nextpx, nextpy, alpha, nextalpha, beta, last_beta, end_angle = 0, end_resp = 0;
		double diff, mindiff, diff1, diff2, dist, mindist, dx, dy, s, t, gx, gy, length, response;
		boolean nextismax, add_ext;
		
		contour tmp_cont;
		doublepoint closestpnt;
		OvalRoi resolved_p;
		contour_class cls;
		region seg = new region();
		ArrayList<offset> line;
		ArrayList<Float> row,col,angle,resp; 
		ArrayList<Float> extx,exty, trow, tcol, tangle, tresp;  
		ArrayList<chord> rl = new ArrayList<chord>();
		cross=new ArrayList<crossref>();	
		
/** Image label contains information on the pixels that have been processed by the linking algorithm. */	
		label = new short[(int) (width*height)];
/** Image indx is index into table of all pixels that possibly could be starting points for new lines.It is used to quickly determine next starting point of a line. */
		indx = new long[(int) (width*height)];
		
		
		
	
		// Select all pixels that can be starting points for lines. 		  
		seg = threshold(ismax, 2);
		
		for (i=0; i<seg.num; i++)
			area += seg.rl.get(i).ce - seg.rl.get(i).cb + 1;
		
		// Create the index of possible starting points. 
		cross = new ArrayList<crossref>();					
		rl = seg.rl;
		for (i=0; i<seg.num; i++) {
			x = rl.get(i).r;
			for (y=rl.get(i).cb; y<=rl.get(i).ce; y++) {
				pos = (int) LINCOOR(x,y,width);
				cross.add(new crossref((short)x,(short)y,Math.abs(eigval[pos]), false));
			}
		}
		
		Collections.sort(cross);
		
		for (i=0;i<area;i++)
		    indx[(int) LINCOOR(cross.get(i).x,cross.get(i).y,width)] = i+1;
		

		
		
		
	/*	
		
		for(i=0;i<height;i++) {
			for(j=0;j<width;j++) {
				if(ismax[(int) LINCOOR(i,j,width)] >=1) {
					cross.add(new crossref((short)i,(short)j,Math.abs(eigval[(int) LINCOOR(i,j,width)]), false));
					indx[(int) LINCOOR(cross.get((int)area).x,cross.get((int)area).y,width)] = ++area;
				}
			}
		}*/
		
		num_cont = 0;
		num_junc = 0;
		cont = new ArrayList<contour>();
		junc = new ArrayList<junction>();
		
//// .....................Linking points.............................................////
		
		
		////  .................... start here...................................
		indx_max = 0;
		for (;;) {
			cls = contour_class.cont_no_junc;                             // Contour class unknown at this point; therefore assume both ends free. 
		    while (indx_max < area && cross.get((int) indx_max).done)   indx_max++;
		     
		    if (indx_max == area) break;                                  // Stop if no feasible starting point exists.
		    
		    max = cross.get((int) indx_max).value;                       //  Selecting  first point 
		    maxx = cross.get((int) indx_max).x;
		    maxy = cross.get((int) indx_max).y;
		    
		    if (max == 0.0)  break;
		    row = new ArrayList<Float>();
		    col = new ArrayList<Float>();
		    resp = new ArrayList<Float>();
		    angle = new ArrayList<Float>();
		    
		    num_pnt = 0;
		    pos = (int) LINCOOR(maxx,maxy,width);
		    label[pos] = (short) (num_cont+1);		                     // updating label with contour number
		    alpha = cal_angle(pos);                          	         // Select line direction. 
		    if (Math.abs(indx[pos])>0)      cross.get((int) (indx[pos]-1)).done = true;
		    
		    // Select normal to line. Normal points to the right of the line as the line is traversed from 0 to num-1.  
		    //Since points are sorted in reverse order before second iteration, first beta actually to point to left of line! 
		    beta = alpha+Math.PI/2.0;
		    if (beta >= 2.0*Math.PI)    beta -= 2.0*Math.PI;
		    
		    row.add((float) posx[pos]);                                      // Adding first point set
		    col.add((float) posy[pos]);
		    angle.add((float) beta);		    
		    resp.add((float)interpolate_response(eigval,maxx,maxy,posx[pos],posy[pos],width,height));
		    num_pnt++;
		    
		    if(DEBUG_show_tracing) resolved_p=debug_show_tracing(pos);
		    		    
////..........................  Marking double responses as processed..........................................//// 
		    octant = (int)(Math.floor(4.0/Math.PI*alpha+0.5))%4;	   // deciding octant
		    double_response(octant, maxx, maxy,num_cont,  alpha);
		    
 ////................find next point on left and right .....................////
		    for (it=1;it<=2;it++) {
		    	x = maxx;
		        y = maxy;
		        pos = (int) LINCOOR(x,y,width);
		        alpha = cal_angle(pos);
		        last_beta = alpha+Math.PI/2.0;
		        if (last_beta >= 2.0*Math.PI)        last_beta -= 2.0*Math.PI;
		        
// Search along the initial line direction in the first iteration and in opposite direction in the second iteration.
		        if (it == 1)     last_octant = (int)(Math.floor(4.0/Math.PI*alpha+0.5))%4; 
		        else  {          
		        	last_octant = (int)(Math.floor(4.0/Math.PI*alpha+0.5))%4+4;
		        	Collections.reverse(row);        // Sort points
		        	Collections.reverse(col);
		        	Collections.reverse(angle);
		        	Collections.reverse(resp);
		        }
		     
////.............................. Now start adding appropriate neighbors to the line.....(choosing best point)......................//// 
		        for (;;) {
		        	pos = (int) LINCOOR(x,y,width);
		        	alpha = cal_angle(pos);                                  // Orient line direction w.r.t. the last line direction. 
		        	px = posx[pos];                                          // sub-pixel points
		        	py = posy[pos];
		          
		        	octant = (int)(Math.floor(4.0/Math.PI*alpha+0.5))%4;
		        	
		        	octant = select_octant(octant,last_octant);	
		        	last_octant=octant;
		        	nextismax = false;                                                   
		        	nexti = -1;
		        	mindiff = Double.MAX_VALUE;
///................searching neighbours............................///
		        	for (i=0;i<3;i++) {                                                    // Determine appropriate neighbor in 3 direction.
		        		nextx = x+dirtab[octant][i][0];
		        		nexty = y+dirtab[octant][i][1];
		        		if (nextx < 0 || nextx >= height || nexty < 0 || nexty >= width)       continue;
		            
		        		nextpos = (int) LINCOOR(nextx,nexty,width);
		        		if (ismax[nextpos] == 0)          continue;
		        		nextismax = true;
		        		nextalpha = cal_angle(nextpos);
		        		
		        		dx = Math.abs(posx[nextpos] - px);
		        		dy = Math.abs(posy[nextpos] - py);                         //dy<= 0.1
		        		if(dy<=0.3) continue;
		        		dist = Math.sqrt(dx*dx+dy*dy);                            // distance
		        		diff = Math.abs(alpha-nextalpha);                          // angle
		        		
		        		if (diff >= Math.PI/2.0)     diff = Math.PI-diff;
		        		
		        		if(diff<  Math.PI/2.0) {
		        			diff = dist;                                        // comparing function to choose neighbour point //if(diff<  Math.PI/20)
			        		if (diff < mindiff) {
			        			mindiff = diff;
			        			nexti = i; 
			        		}
		        		}
		        	}	//    searching complete      

////..........................  Marking double responses as processed..........................................////
		          double_response(octant, x, y, num_cont,  alpha);
		   
		          if (!nextismax || nexti==-1)   break;                              // Have we found the end of the line? 
		            
		          x += dirtab[octant][nexti][0];                                    // If not, add the neighbor to the line.
		          y += dirtab[octant][nexti][1];
		          pos = (int) LINCOOR(x,y,width);
		          
		          nx = normx[pos];                 ny = normy[pos];
		          beta = Math.atan2(ny,nx);
		          last_beta=cal_beta(beta, last_beta);                 // Orient normal to the line direction w.r.t. the last normal.
		          
		          row.add((float) posx[pos]);
		          col.add((float) posy[pos]);
		          angle.add((float) last_beta);
		          resp.add((float) interpolate_response(eigval,x,y,posx[pos],posy[pos],width,height));       // add response
		          num_pnt++;
		          
		          if(DEBUG_show_tracing) resolved_p=debug_show_tracing(pos);

////..........................  Determining the junction..........................................////		          
		         if (label[pos] > 0) {                                // If the label is already marked indicate it is a junction point. 
		        	  k = label[pos]-1;                                // Look for the junction point in the other line.
		        	  if (k == num_cont) {                                   // if line intersects itself.
		        		  for (j=0;j<num_pnt-1;j++) {
		        			  if (row.get(j) == posx[pos] && col.get(j) == posy[pos]) {
		        				  if (j == 0) {                               // Contour is closed.
		        					  cls = contour_class.cont_closed;
		        					 /* Collections.reverse(row);
		        					  Collections.reverse(col);
		        					  Collections.reverse(angle);
		        					  Collections.reverse(resp);*/
		        					  num_pnt--;
		        					  it = 2;		        	
		        				  } 
		        				  else {
		        					  if (it == 2) {
		        						  if (cls == contour_class.cont_start_junc)                   // Determine contour class. 
		        							  cls = contour_class.cont_both_junc;
		        						  else
		        							  cls = contour_class.cont_end_junc;
		                        
		        						  junc.add(new junction(num_cont,num_cont,j,(float)posx[pos],(float)posy[pos]));
		        						  num_junc++;
		        					  } 
		        					  else {
		        						  cls = contour_class.cont_start_junc;                        // Determine contour class. 
		        						  // Index num_pnt-1-j is the correct index since the line is going to be sorted in reverse. 
		        						  junc.add(new junction(num_cont,num_cont,num_pnt-1-j,(float)posx[pos],(float)posy[pos]));
		        						  num_junc++;
		        					  }
		        				  }
		        				  break;
		        			  }
		        		  }		        	
		        		  j = -1;                                               // Mark this case as being processed for the algorithm below.
		        	  } else {
		        		  for (j=0;j<cont.get(k).num;j++) {
		        			  mindist = Math.sqrt(Math.pow(cont.get(k).row.get(j) -posx[pos],2)+Math.pow(cont.get(k).col.get(j) - posy[pos],2));  
		        			  if (mindist<0.0000000000000000001) break;
		        		  }
// If no point can be found on the other line a double response must have occur. 
// In this case, find the nearest point on the other line and add it to the current line. 
		        		  if (j == cont.get(k).num) {
		        			  mindist = Double.MAX_VALUE;
		        			  j = -1;
		        			  for (l=0;l<cont.get(k).num;l++) {
		        				  dx = posx[pos]-cont.get(k).row.get(l);
		        				  dy = posy[pos]-cont.get(k).col.get(l);
		        				  dist = Math.sqrt(dx*dx+dy*dy);
		        				  if (dist < mindist) {
		        					  mindist = dist;
		        					  j = l;
		        				  }
		        			  }
		        			  
		        			  beta = cont.get(k).angle.get(j);
		        			  
		        			  row.add(cont.get(k).row.get(j));                        // Add the point with index j to the current line.
		        			  col.add(cont.get(k).col.get(j));
		        			  angle.add((float) cal_beta(beta, last_beta));
		        			  resp.add(cont.get(k).response.get(j));
		        			  num_pnt++;
		        		  }
		        	  }
		            
// Add the junction point only if it is not one of the other line's end-points. 
		        	  if (j > 0 && j < cont.get(k).num-1) {
		        		  if (it == 1)                                   // Determine contour class. 
		        			  cls = contour_class.cont_start_junc;
		        		  else if (cls == contour_class.cont_start_junc)
		        			  cls = contour_class.cont_both_junc;
		        		  else
		        			  cls = contour_class.cont_end_junc; 
		        		  junc.add(new junction(k, num_cont,j,row.get((int) (num_pnt-1)), col.get((int) (num_pnt-1))));
		        		  num_junc++;
		        	  }
		        	  break;
		        	} // end of adding to junction point
		        
		          	label[pos] = (short) (num_cont+1);                 // if not a junction
		          	if (Math.abs(indx[pos])>0)   cross.get((int) (indx[pos]-1)).done = true;
		        }
		    }
		    
		    if (num_pnt > 1) {                               // Only add lines with at least two points.        
		    	cont.add(new contour(num_pnt,row,col,angle,resp,cls));	
		    	
		        num_cont++;
		      } else {                                            // if cant add to contour then reset everything 
		    	  for (i=-1;i<=1;i++) {
		    		  for (j=-1;j<=1;j++) {
		    			  pos = (int) LINCOOR(BR(maxx+i),BC(maxy+j),width);
		    			  if (label[pos] == num_cont+1)   label[pos] = 0;
		    		  }
		    	  }
		      }
		}     // end of contour addition 
 
////........................ extend lines at their ends to find additional junctions ........................//// 
		if (extend_lines) {                     
			if (MODE_LIGHT)    s = 1;             // Sign by which the gradient has to be multiplied below.  
		    else   s = -1;
		      
		    length = MAX_LINE_EXTENSION;
		    for (i=0; i<num_cont; i++) {		            
		    	tmp_cont = cont.get(i);
		        num_pnt = tmp_cont.num;
		        if (num_pnt == 1  || tmp_cont.cont_class == contour_class.cont_closed)   continue;
		        
		        trow = tmp_cont.row;
		        tcol = tmp_cont.col;
		        tangle = tmp_cont.angle;
		        tresp = tmp_cont.response;
		         
		        for (it=-1; it<=1; it+=2) {                                      // Check both ends of the line (it==-1: start, it==1: end).
  // Determine the direction of search line. This is done by using the normal to the line (angle).
 //  Since this normal may point to left of the line (see below) we have to check for this case by
 // comparing the normal to the direction of the line at its respective end point.
		          
		        	if (it==-1) {                                // Start point of the line.
		        		if (tmp_cont.cont_class == contour_class.cont_start_junc ||  tmp_cont.cont_class == contour_class.cont_both_junc)
		        			continue;
		                dx = trow.get(1)-trow.get(0);
		                dy = tcol.get(1)-tcol.get(0);
		                alpha = tangle.get(0);
		                nx = Math.cos(alpha);
		                ny = Math.sin(alpha);
		                if (nx*dy-ny*dx < 0) {                        // Turn the normal by +90 degrees.
		                	mx = -ny;
		                	my = nx;
		                } else {                                   	 // Turn the normal by -90 degrees. 
		                	mx = ny;
		                	my = -nx;
		                }
		                px = trow.get(0);
		                py = tcol.get(0);
		                response = tresp.get(0);
		            } 
		            else{	                                     // End point of the line.	    	
		            	if (tmp_cont.cont_class == contour_class.cont_end_junc ||  tmp_cont.cont_class == contour_class.cont_both_junc)
		            		continue;
		            	dx = trow.get((int) (num_pnt-1))-trow.get((int) (num_pnt-2));
		                dy = tcol.get((int) (num_pnt-1))-tcol.get((int) (num_pnt-2));
		                alpha = tangle.get((int) (num_pnt-1));
		                nx = Math.cos(alpha);
		                ny = Math.sin(alpha);
		                if (nx*dy-ny*dx < 0) {                  // Turn the normal by -90 degrees.    
		                	mx = ny;
		                    my = -nx;
		                } else {                               // Turn the normal by +90 degrees.		                     
		                    mx = -ny;
		                    my = nx;
		                }
		                px = trow.get((int) (num_pnt-1));
		                py = tcol.get((int) (num_pnt-1));
		                response = tresp.get((int) (num_pnt-1));
		            }	
////...................... Determine the current pixel and calculate the pixels on the search line...................////		                                                          
		        	x = (long) Math.floor(px+0.5);
		        	y = (long) Math.floor(py+0.5);
		        	dx = px-x;
		        	dy = py-y;
		        	line = bresenham(mx,my,dx,dy,length);
		        	num_line = line.size();
		        	
// Now determine whether we can go only uphill (bright lines) or  downhill (dark lines) until we hit another line. 
		        	extx = new ArrayList<Float>();
		        	exty = new ArrayList<Float>();
		        	num_add = 0;
		        	add_ext = false;
		        	for (k=0; k<num_line; k++) {
		        		nextx = x+line.get(k).x;
		        		nexty = y+line.get(k).y;
		        		closestpnt = closest_point(px,py,mx,my,(double)nextx,(double)nexty);
		        		nextpx = closestpnt.cx;
		        		nextpy = closestpnt.cy;
		        		t = closestpnt.t;
		        		// Ignore points before or less than half a pixel away from the true end point of the line
		        		if (t <= 0.5)  continue;
		        		
		        		// Stop if the gradient can't be interpolated any more or if the next point lies outside the image. 
		        		if (nextpx < 0 || nextpy < 0 || nextpx >= height-1 || nextpy >= width-1 || nextx < 0 || nexty < 0 || nextx >= height || nexty >= width)
		        			break;
		        		closestpnt = interpolate_gradient(gradx,grady,nextpx,nextpy,(int)width);
		        		gx = closestpnt.cx;
		        		gy = closestpnt.cy;
		        		
		        		// Stop if we can't go uphill anymore.  This is determined by the dot product of the line direction and the gradient. 
		        		//  If it is smaller than 0 we go downhill (reverse for dark lines). 
		        		nextpos = (int) LINCOOR(nextx,nexty,width);		            	  
		        		if (s*(mx*gx+my*gy) < 0 && label[nextpos] == 0)  break;
		              
		        		if (label[nextpos] > 0) {                                     // Have we hit another line?
		        			m = label[nextpos]-1; 
		        			mindist = Double.MAX_VALUE;                               // Search for the junction point on the other line (contour m)
		        			j = -1;
		        			for (l=0; l<cont.get(m).num; l++) {
		        				dx = nextpx-cont.get(m).row.get(l);
		        				dy = nextpy-cont.get(m).col.get(l);
		        				dist = Math.sqrt(dx*dx+dy*dy);
		        				if (dist < mindist) {
		        					mindist = dist;
		        					j = l;
		        				}
		        			}	
		        			// This should not happen...  But better safe than sorry... 
				               if (mindist > 3.0)                             //3.0
				                 break;
		        			extx.add(cont.get(m).row.get(j));                          // j is index of junction point
		        			exty.add(cont.get(m).col.get(j));
		        			end_resp = cont.get(m).response.get(j);
		        			end_angle = cont.get(m).angle.get(j);
		        			beta = end_angle;
		        			if (beta >= Math.PI)  beta -= Math.PI;
		               
		        			diff1 = Math.abs(beta-alpha);
		        			if (diff1 >= Math.PI)     diff1 = 2.0*Math.PI-diff1;
		               
		        			diff2 = Math.abs(beta+Math.PI-alpha);
		        			if (diff2 >= Math.PI)     diff2 = 2.0*Math.PI-diff2;
		               
		        			if (diff1 < diff2)
		        				end_angle = beta;
		        			else
		        				end_angle = beta+Math.PI;
		        			num_add++;
		               
		        		/*	 if(DEBUG_show_extensions){
					    		resolved_p = new OvalRoi(cont.get(m).col.get(j)-0.25,cont.get(m).row.get(j)-0.25, 0.5, 0.5);
					    		resolved_p.setStrokeColor(Color.GREEN);
					    		resolved_p.setPosition(nFrame+1);
					    		image_overlay.add(resolved_p);			    
					    		imp.setOverlay(image_overlay);
					    		imp.updateAndRepaintWindow();
					    		imp.show();
				               }*/
		        			
		        			
		        			
		        			add_ext = true;
		        			break;
		        		} else {                                      // if no counter hit then simply add point
		        			extx.add((float) nextpx);
		        			exty.add((float) nextpy);
		        			num_add++;
		        			
		        			
		        			/*	 if(DEBUG_show_extensions){
				    		resolved_p = new OvalRoi(cont.get(m).col.get(j)-0.25,cont.get(m).row.get(j)-0.25, 0.5, 0.5);
				    		resolved_p.setStrokeColor(Color.GREEN);
				    		resolved_p.setPosition(nFrame+1);
				    		image_overlay.add(resolved_p);			    
				    		imp.setOverlay(image_overlay);
				    		imp.updateAndRepaintWindow();
				    		imp.show();
			               }*/
		        		}
		        	}
		        	if (add_ext) {                                   // Make room for the new points. 
		        		num_pnt += num_add;
		        		tmp_cont.row = trow;
		        		tmp_cont.col = tcol;
		        		tmp_cont.angle = tangle;
		        		tmp_cont.response = tresp;
		        		tmp_cont.num = num_pnt;
		        		if (it == -1) {                     // Move points on the line up num_add places. Insert at beginning of line. 
		        			for (k=0; k<num_add; k++) {               //cause order of insertion is different
		        				trow.add(0,extx.get(k));
		        				tcol.add(0,exty.get(k));
		        				tangle.add(0,(float) alpha);
		        				tresp.add(0,(float) response);
		        			}
		        			tangle.set(0,(float)end_angle);
		        			tresp.set(0,(float)end_resp);
		        			for (k=0; k<num_junc; k++) {                  // Adapt indices of the previously found junctions.
		        				if (junc.get(k).cont1 == i)    junc.get(k).pos += num_add;
		        			}
		        		} 
		        		else {                                            // Insert points at the end of the line.
		        			for (k=0; k<num_add; k++) {
		        				trow.add(extx.get(k));
		        				tcol.add(exty.get(k));
		        				tangle.add((float) alpha);
		        				tresp.add((float) response);
		        			}
		        			tangle.set( (int)(num_pnt-1),(float)end_angle);
		        			tresp.set((int)(num_pnt-1),(float)end_resp);
		        		}		         

		             // Add the junction point only if it is not one of the other line's end points. 
		        		if (j > 0 && j < cont.get(m).num-1) {
		        			if (it == -1) {
		        				if (tmp_cont.cont_class == contour_class.cont_end_junc)
		        					tmp_cont.cont_class = contour_class.cont_both_junc;
		        				else
		        					tmp_cont.cont_class = contour_class.cont_start_junc;
		        			} else {
		        				if (tmp_cont.cont_class == contour_class.cont_start_junc)
		        					tmp_cont.cont_class = contour_class.cont_both_junc;
		        				else
		        					tmp_cont.cont_class = contour_class.cont_end_junc;
		        			}
		        			junc.add(new junction());
		        			junc.get((int) num_junc).cont1 = m;
		        			junc.get((int) num_junc).cont2 = i;
		        			junc.get((int) num_junc).pos = j;
		        			if (it == -1) {
		        				junc.get((int) num_junc).x = trow.get(0);
		        				junc.get((int) num_junc).y = tcol.get(0);
		        			} else {
		        				junc.get((int) num_junc).x = trow.get((int) (num_pnt-1));
		        				junc.get((int) num_junc).y = tcol.get((int) (num_pnt-1));
		        			}
		        			num_junc++;
		        		}
		        	}
		        }
		    }		         
		} // end of extend line
		
  
		Collections.sort(junc);                    // Done with linking. Now split the lines at the junction points. 
		
		
		
		
		//// .................................... ends here....................
		if(split_lines){
			for (i=0;i<num_junc;i+=k) {
				j = (int) junc.get(i).cont1;
			    tmp_cont = cont.get(j);
			    num_pnt = tmp_cont.num;			     
			    for (k=0;i+k<num_junc;k++){              // Count how often line j needs to be split.
			    	if(junc.get(i+k).cont1 != j)       break;			    		
			    }
			    if (k == 1 && tmp_cont.row.get(0) == tmp_cont.row.get((int) (num_pnt-1)) && tmp_cont.col.get(0) == tmp_cont.col.get((int) (num_pnt-1))) {
// If only one junction point is found and the line is closed it only needs to be rearranged cyclically, but not split. 
			    	begin = junc.get(i).pos;
			    	trow = tmp_cont.row;
			    	tcol = tmp_cont.col;
			    	tangle = tmp_cont.angle;
			    	tresp = tmp_cont.response;	    
			    	//tmp_cont->row = xcalloc(num_pnt,sizeof(float));
			    	//tmp_cont->col = xcalloc(num_pnt,sizeof(float));
			    	//tmp_cont->angle = xcalloc(num_pnt,sizeof(float));
			    	//tmp_cont->response = xcalloc(num_pnt,sizeof(float));
			    	for (l=0;l<num_pnt;l++) {
			    		pos = (int) (begin+l);
			    		// Skip starting point so that it is not added twice. 
			    		if (pos >= num_pnt)
			    			pos = (int) (begin+l-num_pnt+1);
			    		tmp_cont.row.set(l,trow.get(pos));
			    		tmp_cont.col.set(l,tcol.get(pos));
			    		tmp_cont.angle.set(l,tangle.get(pos));
			    		tmp_cont.response.set(l,tresp.get(pos));
			    	}			    	
			    	tmp_cont.cont_class = contour_class.cont_both_junc;                  // Modify contour class. 
			    	} else {		                           // Otherwise the line has to be split.			    	 
			    		for (l=0;l<=k;l++) {
			    			if (l == 0) begin = 0;
			    			
			    			else    begin = junc.get(i+l-1).pos;
			    			
			    			if (l==k)    end = tmp_cont.num-1;
			          
			    			else    end = junc.get(i+l).pos;
			          
			    			num_pnt = end-begin+1;
			    			if (num_pnt == 1 && k > 1) {          // Do not add one point segments
			    				continue;
			    			}			          
			    			cont.add(new contour());
			    			//till begin+num_pnt, since last index is exclusive in subList function
			    			cont.get((int) num_cont).row = new ArrayList<Float>(tmp_cont.row.subList((int)begin,(int)( begin+num_pnt)));
			    			cont.get((int) num_cont).col = new ArrayList<Float>(tmp_cont.col.subList((int)begin,(int)( begin+num_pnt)));
			    			cont.get((int) num_cont).angle = new ArrayList<Float>(tmp_cont.angle.subList((int)begin,(int)( begin+num_pnt)));
			    			cont.get((int) num_cont).response = new ArrayList<Float>(tmp_cont.response.subList((int)begin,(int)( begin+num_pnt)));
			    			cont.get((int) num_cont).num = num_pnt;

			    			// Modify contour class. 
			    			if (l == 0) {
			    				if (tmp_cont.cont_class == contour_class.cont_start_junc || tmp_cont.cont_class == contour_class.cont_both_junc)
			    					cont.get((int)num_cont).cont_class = contour_class.cont_both_junc;
			    				else
			    					cont.get((int)num_cont).cont_class = contour_class.cont_end_junc;
			    			} else if (l == k) {
			    				if (tmp_cont.cont_class == contour_class.cont_end_junc || tmp_cont.cont_class == contour_class.cont_both_junc)
			    					cont.get((int)num_cont).cont_class = contour_class.cont_both_junc;
			    				else
			    					cont.get((int)num_cont).cont_class = contour_class.cont_start_junc;
			    			} else {
			    				cont.get((int)num_cont).cont_class = contour_class.cont_both_junc;
			    			}
			    			num_cont++;
			    		}
			    		num_cont= num_cont -1;
			    		cont.set(j,cont.get((int) num_cont)); //*???? WTF
			    		cont.remove((int) num_cont);
			    		tmp_cont = null;
			    	}
			    }		    
		 	}
		  		 
		  	// Finally, check whether all angles point to the right of the line. 
		  	for (i=0; i<num_cont; i++) {
		  		tmp_cont = cont.get(i);
		  		num_pnt = tmp_cont.num;
		  		trow = tmp_cont.row;
		  		tcol = tmp_cont.col;
		  		tangle = tmp_cont.angle;
		    	
		  		// One point of the contour is enough to determine the orientation. 
		  		k = (int) ((num_pnt-1)/2);
		  		// The next few lines are ok because lines have at least two points. 
		  		dx = trow.get(k+1)-trow.get(k);
		  		dy = tcol.get(k+1)-tcol.get(k);
		  		nx = Math.cos(tangle.get(k));
		  		ny = Math.sin(tangle.get(k));
// If the angles point to the left of the line they have to be adapted.
//  The orientation is determined by looking at the z-component of the cross-product of (dx,dy,0) and (nx,ny,0). 
		  		if (nx*dy-ny*dx < 0) {
		  			for (j=0; j<num_pnt; j++) {
		  				tangle.set(j,(float) (tangle.get(j)+Math.PI));
		  				if (tangle.get(j) >= 2*Math.PI)
		  					tangle.set(j,(float) (tangle.get(j)-2*Math.PI));
		  			}
		  		}
		  	}
		 
		  	
		  	
		  	/// pjb................start here
		  	//Remove lines with number of points less than threshold
		  	if(nMinNumberOfNodes>0){
		  		for (i=0; i<num_cont; i++) {
		  			if(cont.get(i).row.size()< nMinNumberOfNodes ){
		  				cont.remove(i);
		  				num_cont--;
		  				i--;					  
		  			}				  
		  		}
		  	}
		 
		  	
	}
		
	
	public static float slope(float x1, float y1, float x2, float y2) { 
		return (y2 - y1) / (x2 - x1); 
	} 
	 public static double angleBetween2Lines(Float x1,Float y1,Float x2,Float y2,Float x3,Float y3,Float x4,Float y4)
	 {
	     double angle1 = Math.atan2(y1 - y2, x1-x2);
	     double angle2 = Math.atan2(y3-y4, x3-x4);
	     double ret= Math.toDegrees(angle1-angle2);
	   //  double val = (ret * 180) / PI;
	     if (ret < 0) ret += 360;
	    
		 return ret;
			 
		 
//		 float M1 = slope(x1,y1,x2,y2);
//		 float M2 = slope(x3,y3,x4,y4);
//		 
//		 // Store the tan value  of the angle
//	        double angle = Math.abs((M2 - M1) / (1 + M1 * M2));
//	 
//	        // Calculate tan inverse of the angle
//	        double ret = Math.atan(angle);
//	 
//	        // Convert the angle from
//	        // radian to degree
//	        double val = (ret * 180) / PI;
//	 
//		    return val;
		 
	                               
	 }
	 
	 
//--------------------	 //BLOCK SPLIT StART--------------------------
	  	// [PJB: 4/12/2022] if the line has right or acute angle, split the line and store the sets.
	  	// check for each 3 consequtive points in a contour
	public void blocksplit() {
		
	  	
	  	
	  	int numpnt;
	  	double [] p0 =new double[2]; double []p1 =new double[2];
	  	double []p2 =new double[2];
		double pangle;
		//ArrayList<Integer> split_pos = new ArrayList<Integer>();
		pointcontour tmp_cont;
		pointtmcont=new ArrayList<pointcontour>();
//		System.out.print(cont.size());
	  	for(int i=0;i< tcont.size(); i++) {
	  		 tmp_cont= tcont.get(i);
	  		 int flagadd=1;
	  		 int lastcount=0;
	  		 for(int j=0;j<tmp_cont.num && (j+1)<tmp_cont.num &&(j+2)<tmp_cont.num; j++) {
//	  			double [] p0 ,p1,p2;
//	  			double pangle;
	  			p1[0]= (tmp_cont.col.get((int)j)).doubleValue();
	  			p1[1]= (tmp_cont.row.get((int)j)).doubleValue();
	  			
	  			p0[0]= (tmp_cont.col.get((int)(j+1))).doubleValue();
	  			p0[1]=( tmp_cont.row.get((int)(j+1))).doubleValue();
	  			
	  			p2[0]= (tmp_cont.col.get((int)(j+2))).doubleValue();
	  			p2[1]= (tmp_cont.row.get((int)(j+2))).doubleValue();
	  			
	  			
	  			pangle= computeAngle(p0,p1,p2);
	  			if(Math.toDegrees(pangle) < (180-angledev) || Math.toDegrees(pangle)>(180+angledev) ) {
	  				flagadd=0;
	  				
	  			//	System.out.println("contour no"+(i+1)+ "  "+Math.toDegrees(pangle));
	  				//split_pos.add((int)(j+1));
	  				//System.out.println("cont:" +i+"   "+split_pos.get(count-1));
	  				ArrayList<Float> row1=new ArrayList<Float>();
  				    ArrayList<Float> col1=new ArrayList<Float>();
	  				ArrayList<Float> widthL1=new ArrayList<Float>();
	  				ArrayList<Float> widthR1=new ArrayList<Float>();
	  				for(int k=lastcount;k<= j+1 ;k++) {
	  					row1.add(tmp_cont.row.get(k));
	  					col1.add(tmp_cont.col.get(k));
  					  widthL1.add(tmp_cont.width_l.get(k));
  				      widthR1.add(tmp_cont.width_l.get(k));
	  				}
	  				//oct
	  				if(row1.size()>=2)
	  				 pointtmcont.add(new pointcontour((int)row1.size(),row1,col1, widthL1, widthR1));
	  				lastcount=j+2;
	  				
//	  				
//	  				ArrayList<Float> row2=new ArrayList<Float>();
//	  				ArrayList<Float> col2=new ArrayList<Float>();
//	  				ArrayList<Float> widthL2=new ArrayList<Float>();
//	  				ArrayList<Float> widthR2=new ArrayList<Float>();
//	  				for(int k=j+2;k< tmp_cont.num ;k++) {
//	  				//tmcont.add(new pointcontour(num,pxs_al,pxy_al, width_l, width_r))
//	  					row2.add(tmp_cont.row.get(k));
//	  					col2.add(tmp_cont.col.get(k));
//	  					widthR2.add(tmp_cont.width_r.get(k));
//	  					widthL2.add(tmp_cont.width_l.get(k));	
//	  				}
//	  				if(row2.size()>=2)
//	  				pointtmcont.add(new pointcontour((int)row2.size(),row2,col2, widthL2, widthR2));
//	  			    break;
	  			
	  			}// IF CONDITION END
	  			
	  					
	  			
	  		}
	  		if(flagadd==1) {
	  			pointtmcont.add(tmp_cont);
	  		}
	  		else {
	  			ArrayList<Float> row2=new ArrayList<Float>();
  				ArrayList<Float> col2=new ArrayList<Float>();
  				ArrayList<Float> widthL2=new ArrayList<Float>();
  				ArrayList<Float> widthR2=new ArrayList<Float>();
  				for(int k=lastcount;k< tmp_cont.num ;k++) {
  				//tmcont.add(new pointcontour(num,pxs_al,pxy_al, width_l, width_r))
  					row2.add(tmp_cont.row.get(k));
  					col2.add(tmp_cont.col.get(k));
  					widthR2.add(tmp_cont.width_r.get(k));
  					widthL2.add(tmp_cont.width_l.get(k));	
  				}
  				if(row2.size()>=2)
  				pointtmcont.add(new pointcontour((int)row2.size(),row2,col2, widthL2, widthR2));
	  				
	  		}
	  			
	  	}//loop of contour
	  	
	}
	 //BLOCK SPLIT END 
//----------------------------------------------------------------------------------------------
	
	
	
	//-------------------BLOCKK COMBINE CONTOURS------------------------
	
	public void blockcombine() {
	     pointcontour cont1,cont2;
		 double langle,pangle;
		 HashMap<Integer, Double> AngC= new HashMap<Integer, Double>();
	     for(int i=0;i<pointtmcont.size();i++) {
	    	pointcontour tmpcont=pointtmcont.get(i);
	    	int numpnt=(int)tmpcont.num;
	    	 if(tmpcont.col.get(0) < tmpcont.col.get(numpnt-1)) {
	    		 Collections.reverse(pointtmcont.get(i).row);
	    		 Collections.reverse(pointtmcont.get(i).col);
	    		 Collections.reverse(pointtmcont.get(i).width_l);
	    		 Collections.reverse(pointtmcont.get(i).width_r);
	    	 }
	    	// System.out.println(tmpcont.col.get(0)+"(0) ------(nop-1)"+tmpcont.col.get(numpnt-1));
	     }
	
		  for(int i=0;i<pointtmcont.size();i++) {
			     ArrayList<Integer> pair = new ArrayList<Integer>();
				 cont1 = pointtmcont.get(i);
				 long nop1 = cont1.num;
				 Float px1,x1 = null,x2 =null,x3 = null,x4=null,y1=null,y2=null,y3=null,y4=null, max, maxy ;
				 int  max_ind, maxy_ind;
				 boolean flag1, flag2;
		
				 px1= cont1.col.get((int)(nop1-1)); 
				// System.out.println(px1 +", "+ px2);
				 double lastlangle= 361;
					 for(int j=0;j<pointtmcont.size();j++) {                 //j=i+1
						
						 cont2 =pointtmcont.get(j);
						 long nop2 =cont2.num;
						 Float lx1= cont2.col.get((int)(nop2-1)); 
						 
						
						 // contour2 follows contour1 ( x value of c1 is less than x value of c2) 
					
						 // this if only store a segment no that matches its immediate right segment. 
						 if(px1 < lx1) {                                           
							 //end two points of contour1
							 
							 if ( cont1.col.get((int)(0)) > cont1.col.get((int)(nop1-1))) {
							 x1=   cont1.col.get((int)(0)); 
							 x2=  cont1.col.get((int)(1)); 
							 
							 y1=   cont1.row.get((int)(0)); 
							 y2=  cont1.row.get((int)(1)); 
							 }
							 
							 else {
							 x1=   cont1.col.get((int)(nop1-1)); 
							 x2=  cont1.col.get((int)(nop1-2)); 
							 
							 y1=   cont1.row.get((int)(nop1-1)); 
							 y2=  cont1.row.get((int)(nop1-2)); 
							 	 
							 }
							 
							 
//							 
							 //start two points of contour2
							if( cont2.col.get((int)(nop2-1)) <  cont2.col.get((int)(0))) {
							 x3=   cont2.col.get((int)(nop2-1)); 
							 x4=  cont2.col.get((int)(nop2-2)); 
							 y3=   cont2.row.get((int)(nop2-1)); 
							 y4=  cont2.row.get((int)(nop2-2)); 
							}
							else {
								x3=   cont2.col.get((int)(0)); 
								x4=  cont2.col.get((int)(1)); 
								y3=   cont2.row.get((int)(0)); 
								y4=  cont2.row.get((int)(1)); 
								}
							
							 if((x3-x1) >= -10.00 && (x3-x1) <100.00 && (y3-y1) >= -10.00 && (y3-y1) <10.00)  {
								
								 langle=  angleBetween2Lines(x1,y1,x2,y2,x3,y3,x4,y4);
					     
								 double [] p0 =new double[2]; double []p1 =new double[2];
								 double []p2 =new double[2]; 
								 p1[0]= x1.doubleValue();
						  		 p1[1]= y1.doubleValue();
						  			
						  		p0[0]= x3.doubleValue();
						  		p0[1]= y3.doubleValue();
						  			
						  		p2[0]= x4.doubleValue();
						  		p2[1]= y4.doubleValue();
								 pangle =Math.toDegrees(computeAngle(p0,p1,p2));
						      
							//	 if(langle>= (180- (angledev)) && langle <= (180+(angledev)) && pangle>= (180- (angledev)) && pangle <= (180+(angledev))) {
								 	if((langle>= (180- (angledev)) && langle <= (180+(angledev)) && pangle>= (180- (angledev)) && pangle <= (180+(angledev)))
								 			|| (langle>0.00 && langle <=10.00)) {
								 		System.out.println("contour_" +(i+1)+"& contour_"+(j+1)+" = " +langle +" && "+pangle);
								 		
								 		//								 		 matchPair.put(i, j);
								 		if (lastlangle > langle) {  
								 	        pair.clear();
								 	        pair.add(j);
								 	       lastlangle = langle;
								 	    }
								 	}
								 		
							 }
							  
						 }
						 
						 
			 
				 }
					
				
//			    ArrayList<Integer>  pairold = matchPair.get(i);
//			    if(pairold !=null  )
//			    	pair.addAll(pairold);
//		
//			    Set<Integer> set1 = new LinkedHashSet<Integer>();
//			    set1.addAll(pair);
//
//			    // delete al elements of arraylist
//			    pair.clear();
//
//			    // add element from set to arraylist
//			    pair.addAll(set1); 
//			    if (pair != null)
//			    	matchPair.put(i,pair);
//				
//				
//				for(int p=0;p< pair.size();p++) {				 
//					 ArrayList<Integer> oldcontent= new  ArrayList<Integer>();
//					 if( matchPair.get(pair.get(p)) !=null) {
//						 oldcontent=  matchPair.get(pair.get(p));
//						 oldcontent.add(i); 
//					 }
//					 else {
//						 oldcontent.add(i);
//					 }
//					 Set<Integer> set2 = new LinkedHashSet<Integer>();
//					    set2.addAll(oldcontent);
//
//					    // delete all elements of arraylist
//					    oldcontent.clear();
//
//					    // add element from set to arraylist
//					    oldcontent.addAll(set2);
//					    matchPair.put(pair.get(p),oldcontent);
//					   
//				}
			matchPair.put(i,pair);	
		  }	
	}
	//----------------------END BLOCKCOMBINE()------------------
	
	//--------------combine--------------------
	
	public void combine() {
		
		boolean addcont[]=new boolean[pointtmcont.size()];
		
		for(Map.Entry<Integer, ArrayList<Integer>> entry : matchPair.entrySet()) {
			Integer key =entry.getKey();
			//ArrayList<Integer> value= entry.getValue();
			ArrayList<Integer> newline = new ArrayList<Integer>();
			if(addcont[key]==false){
				//addcont[key]=true;
				//ArrayList<Integer> newline = new ArrayList<Integer>();
				newline.clear();
				recadd(key,addcont,newline);
				//System.out.println(newline);
				//---------arrange in descending order------------	
				for(int i=0; i< newline.size()-1;i++) {
					 
					       int swapped = 0;
					        for (int j = 0; j < newline.size()- i - 1; j++) {
					            if (pointtmcont.get(newline.get(j)).col.get(0) < pointtmcont.get(newline.get(j+1)).col.get(0)) {
					            	Collections.swap(newline,j, j + 1);
					                swapped = 1;
					            }
					        }
					 
					        // If no two elements were swapped by inner loop,
					        // then break
					        if (swapped == 0)
					            break;
					   
				}
				contourList.add(newline);
			}
			
				
			
		}
	}
	public void recadd(Integer key,boolean [] addcont,ArrayList<Integer> newline) {
		
		if(addcont[key]==false) {
		   newline.add(key);
		   addcont[key]=true;
		
		   ArrayList<Integer> value= matchPair.get(key);
		   for(int i=0; i<value.size();i++ ) {
			   if(addcont[value.get(i)]==false) {
				   recadd(value.get(i), addcont, newline);
			   }
		   }	
		}
		else return;
   }
	
	
	//----------------end combine------------------
	public void show_contours(){
		  PolygonRoi polyline_p;
		  PointRoi point_p;
		  contour tmp_cont;
		  long num_pnt;
		  String Roi_name;
		  int i,j,k,p,q;
		  tcont= new ArrayList<pointcontour>();
		  
		  
		  imp = IJ.openImage("/Users/winja/Desktop/SIV_WORK/CurveTrace1/mt_trace/data/whites.png");
			if(null == imp){                                                                            
				IJ.noImage();	return;
			}		
			//else if (imp.getType() != ImagePlus.GRAY8 && imp.getType() != ImagePlus.GRAY16 ){
				//IJ.error("8, 16 or 32-bit grayscale image required");       return;
			//}
				
	////......  2. Getting active image processor and parameters ----------------
			ip = imp.getProcessor();
			nCurrentSlice = imp.getCurrentSlice();
			nStackSize = imp.getStackSize();
		  
		  
		  
		  
		  int color_n = 8;
		  double nx,ny;
		  double line_avg_width;
		  double langle;
		
		  Color[] colors;
		  Color[] colors_bord;
		  Color single_main;
		  Color single_border;
		  colors = new Color[] {Color.BLUE, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED, Color.YELLOW};		  
		  colors_bord = new Color[8];
		  colors_bord[0] = new Color(0,0,139);//dark blue
		  colors_bord[1] = new Color(0,139,139); //dark cyan
		  colors_bord[2] = new Color(0,100,0); //dark green
		  colors_bord[3] = new Color(139,0,139); //dark magenta
		  colors_bord[4] = new Color(255,140,0); //dark orange
		  colors_bord[5] = new Color(199,21,133); //mediumvioletred
		  colors_bord[6] = new Color(139,0,0); //dark red
		  colors_bord[7] = new Color(153,153,0); //dark yellow
		  
		  
		  Roi_name  = "contour_f_";
		  Roi_name = Roi_name.concat(Integer.toString(nFrame+1));
		  Roi_name = Roi_name.concat("_c_");
		  if(nRoiLinesType>0){
			  //black
			  if(nRoiLinesColor==1){
				  for(i=0;i<8;i++){
					  colors[i]=Color.BLACK;
					  colors_bord[i]=Color.GRAY;
				  }
			  }
			  //black
			  if(nRoiLinesColor==2){
				  for(i=0;i<8;i++){
					  colors[i]=Color.WHITE;
					  colors_bord[i]=Color.GRAY;
				  }
			  }
			  if(nRoiLinesColor>=3){
				  single_main = colors[nRoiLinesColor-3];
				  single_border = colors_bord[nRoiLinesColor-3];
				  for(i=0;i<8;i++){
					  colors[i]=single_main;
					  colors_bord[i]=single_border;
				  }
			  }		  
			  
	  		  roi_manager = RoiManager.getInstance();
			  if(roi_manager == null) roi_manager = new RoiManager();
			  
			  
			  
			  //Joining contours bucket
			  

					 
		  //upto this ok
			  
			  
			  
			  
			  //
			  
			  ArrayList<Float> width_l= new ArrayList<Float>();
			  ArrayList<Float> width_r= new ArrayList<Float>();
			  
			  
			  for(i=0;i<cont.size();i++) {
				 tmp_cont=cont.get(i);
				 num_pnt=tmp_cont.num;
			   //  width_r = new float[(int) num_pnt];
				//  width_l = new float[(int) num_pnt];
				  
				  if(num_pnt<100)continue;                         //100
				  int []check=new int [(int)num_pnt];
				  
				  j=0;k=j+1;
				  check[0]=1;
				  int len=(int)num_pnt;
				  
				  int n=1;
				  while(j<num_pnt && k<num_pnt) {
					  double diff= tmp_cont.angle.get(j)-tmp_cont.angle.get(k);
					  int count=0;
					  while(k<num_pnt) {
						  double diff1=tmp_cont.angle.get(j)-tmp_cont.angle.get(k);
						  if(Math.abs(diff1-diff) >0.4  ) {                                                // if(Math.abs(diff1-diff) >0.2  ) {   
							  check[k]=1;
							  j=k;
							  n++;
							  break;
						  }
						  else if(count>100) {
							  check[k]=1;
							  j=k;
							  n++;
							  break;
						  }
						  k++;count++;
					  } 
				  }
				  if(check[k-1]!=1) {
					  check[k-1]=1;
					  n++;
				  }
//				
//				  float[] pxs = new float[n];
//				  float[] pxy = new float[n];
//				  float a,b;
			     
				  k=0;
				  ArrayList<Float> pxs_al = new ArrayList<Float>();
				  ArrayList<Float> pxy_al = new ArrayList<Float>();
				  int g=0;
	
				  for(j=0;j<num_pnt;j++){
					  if(check[j]==1) {
						  //[26_05_22: to skip distance only in forward direction]
						  if(j==0) {
							  pxs_al.add(tmp_cont.row.get(j));
							  pxy_al.add(tmp_cont.col.get(j));
							  width_l.add(tmp_cont.width_l.get(j));
							  width_r.add(tmp_cont.width_r.get(j));

							 
//							  pxs[k]=tmp_cont.row.get(j);
//							  pxy[k]=tmp_cont.col.get(j);
							 	  
							// point_p  = new PointRoi((int)(pxy[k]),(int)(pxs[k]));
							 // a= pxy_al.get(k).floatValue();
							//  b=pxs_al.get(k).floatValue();
							 point_p  = new PointRoi((int)(pxy_al.get(k).floatValue()),(int)(pxs_al.get(k).floatValue()));
							 
							// if((int)(pxs_al.get(k).floatValue())-(int)(pxs_al.get(k-1).floatValue())> 2.0) 
							    image_overlay.add(point_p); 
							// g++;
							  k++;
							 
						  }
						  
//						  if(j!=0 && Math.abs((pxy_al.get(k-1).floatValue()) - tmp_cont.col.get(j)) > 1) {
						  if(j!=0 && Math.abs((pxs_al.get(k-1).floatValue()) - tmp_cont.row.get(j)) > 1) {
						 // if(j!=0 && (pxy[k-1]) - tmp_cont.col.get(j) > 5) {
							  
							 pxs_al.add((float)tmp_cont.row.get(j));         //y
							 pxy_al.add((float)tmp_cont.col.get(j));         //x
							 
							 width_l.add(tmp_cont.width_l.get(j));
							 width_r.add(tmp_cont.width_r.get(j));
							    
//						  pxs[k]=tmp_cont.row.get(j);
//						  pxy[k]=tmp_cont.col.get(j);
						  
						//  point_p  = new PointRoi((int)(pxy[k]),(int)(pxs[k]));
							 
//							a= pxy_al.get(k).floatValue();
//					    	b=pxs_al.get(k).floatValue();
							  point_p  = new PointRoi((int)(pxy_al.get(k).floatValue()),(int)(pxs_al.get(k).floatValue()));
							//  if((int)(pxs_al.get(k).floatValue())-(int)(pxs_al.get(k-1).floatValue())> 1.0 || j==num_pnt-1) 
								  image_overlay.add(point_p); 
						       k++;
						     // g++;
						  
						  }
					  } 
					  
					  
				  }
				 
				    
				 
				  
				  //[25_05_22  : adding points to overlay]
				  int lengthx = pxy_al.size();
				  long num= (long)lengthx;
				  if( num > 1){
				// Collections.reverse(pxy_al); 
				// Collections.reverse(pxs_al); 
				// Collections.reverse( width_l); 
				// Collections.reverse(width_r); 
				  tcont.add(new pointcontour(num,pxs_al,pxy_al, width_l, width_r));
				 // tcont.get(i).width_l = width_l;
				//  tcont.get(i).width_r = width_r;
				  
				  }
				  int lengths =pxs_al.size();
				  float[] pxs = new float[lengths];      //y
				  float[] pxy = new float[lengthx];     //x
				    
				  for (int t = 0; t < lengthx; t++) {
				    pxy[t] = pxy_al.get(t).floatValue();}
				    
				  for (int t = 0; t < lengths; t++) {
					    pxs[t] = pxs_al.get(t).floatValue();}
			}
				  //end 
			  // split the contours and save it in a new pointcontour(no of p)
			   blocksplit();
			  pointcontour tmpcont;
			  
			  blockcombine();
			 //after combine
//---------------- To combine the same property contours-----------//			   
			  System.out.println(matchPair);
			  //in matchA keep only two adjacent, based on min distance and lowest angle 
			  
			  combine();
//------------show list of new line combined in array------------			  
	  for(int l=0 ; l<contourList.size() ; l++) {
		  System.out.println(contourList.get(l));
	  }
		System.out.println(contourList.size()); 
		
//     for(i=0;i<pointtmcont.size();i++) {
//    	 tmpcont=pointtmcont.get(i);
//    	int numpnt=(int)tmpcont.num;
//    	 if(tmpcont.col.get(0) < tmpcont.col.get(numpnt-1)) {
//    		 Collections.reverse(pointtmcont.get(i).row);
//    		 Collections.reverse(pointtmcont.get(i).col);
//    		 Collections.reverse(pointtmcont.get(i).width_l);
//    		 Collections.reverse(pointtmcont.get(i).width_r);
//    	 }
//     }
		
		
	  slicecontour=new ArrayList<pointcontour>();
	  for(i=0;i<contourList.size();i++) {
		  ArrayList<Float> row1=new ArrayList<Float>();
		  ArrayList<Float> col1=new ArrayList<Float>();
		  ArrayList<Float> widthL1=new ArrayList<Float>();
		  ArrayList<Float> widthR1=new ArrayList<Float>();
		  
		  ArrayList<Integer> list= contourList.get(i);
		  for(j=0;j<list.size();j++) {

		     tmpcont= pointtmcont.get(list.get(j));
		     for(int l=0; l<tmpcont.num;l++) {	
		    	 
				  row1.add(tmpcont.row.get(l));
				  col1.add(tmpcont.col.get(l));
				  widthL1.add(tmpcont.width_l.get(l));
			      widthR1.add(tmpcont.width_l.get(l));
		     }
			      
		  }
		  if(row1.size()>=1) {
		    	System.out.println("cont  ----> "+col1);
		        slicecontour.add(new pointcontour((int)row1.size(),row1,col1, widthL1, widthR1)); 
		     
		     
		  }
	  }
			  
				  //[27_05_22: only adding contours with higher points]
	  		//	for(i=0;i<pointtmcont.size();i++) {
	  		//		tmpcont =pointtmcont.get(i);
			  for(i=0;i<slicecontour.size();i++) {
				  tmpcont =slicecontour.get(i);
				  num_pnt=tmpcont.num;
				  float[] pxx = new float[(int)num_pnt];      //x
				  float[] pyy = new float[(int)num_pnt];     //y

				  
				
//  -------------- // To show in graphics the line using Polyline//----------------
				  
				  
				  for (int t = 0; t < num_pnt; t++) {
					    pxx[t] = tmpcont.col.get(t);
				  //}
				//  for (int f = 0; f < num_pnt; f++) {
					     pyy[t] = tmpcont.row.get(t);}
				  
				  
			
					 
						  polyline_p = new PolygonRoi(pxx, pyy, Roi.POLYLINE);
						  polyline_p.fitSpline();
						  polyline_p.setPosition(nFrame+1);
						  polyline_p.setStrokeColor(colors[i%color_n]);
						  polyline_p.setStrokeWidth(5.0);
						  polyline_p.setName(Roi_name.concat(Integer.toString(i+1)));
						  
						  if(nRoiLinesType == 1 || nRoiLinesType == 3)
							  	roi_manager.addRoi(polyline_p);
						  
						  if(compute_width && nRoiLinesType ==2){
							  line_avg_width = 0;
							  for(j=0;j<num_pnt;j++){
								  line_avg_width +=tmpcont.width_l.get(j)+tmpcont.width_r.get(j);
							  }				 
							  line_avg_width =line_avg_width /num_pnt;
							  polyline_p.setStrokeWidth(line_avg_width);
							  polyline_p.updateWideLine((float) line_avg_width);
				
							  roi_manager.addRoi(polyline_p);
						  }
						  image_overlay.add(polyline_p); 
						  
						 
				  
			  } // each contour loop
			  
			
	         
			  
		 	  double first_x = subimage_Width * sliceN;
              double first_y = subimage_Height * 0 ; 
              image_overlay.translate(first_x ,first_y); 
			  imp.setOverlay(image_overlay);
			  imp.updateAndRepaintWindow();
			  imp.show();		  
			  
			  roi_manager.setVisible(true);
			  roi_manager.toFront();
			  
	    }
	}
	
	
	
	public void re_compute_contour() {
		
		
		
		int i=0;
		System.out.println(cont.size());
			
		// starting iteration
		while(i<cont.size()) {
			
			int index=cal_next_e(i);
			
			if(index==-1) {
				i++;
			}
		}
		
		
		i=0;
		System.out.println(cont.size());
			
		// starting iteration
		while(i<cont.size()) {
			
			int index=cal_next_s(i);
			
			if(index==-1) {
				i++;
			}
		}
		
		System.out.println(cont.size());
		i=0;
		
		// starting iteration
		while(i<cont.size()) {
			
			int index=cal_next_s(i);
			
			if(index==-1) {
				i++;
			}
		}
		System.out.println(cont.size());
		
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	public int cal_next_e(int i) {
		
		
		int c_num_pnt, n_num_pnt, index=-1;
		float x, y, dx, dy;
		double alpha, beta, dist, diff, min_d=Float.MAX_VALUE;
		boolean rev=true;
		contour  c_cont,n_cont;
		jun = new ArrayList<junc_pnt>();
		
	//  searching closest to end of current contour
		c_cont=cont.get(i);                    // contour
		c_num_pnt=(int)c_cont.num;             // number of points
		x=cont.get(i).row.get(c_num_pnt-1);  
		y=cont.get(i).col.get(c_num_pnt-1);
		alpha=cont.get(i).angle.get(c_num_pnt-1);
		
		
		for(int j=i+1;j<cont.size();j++) {
			
			// checking point
			n_cont = cont.get(j);  
			n_num_pnt = (int)n_cont.num;
			
			
			//starting point comparison
			dx=Math.abs(x-n_cont.row.get(0));           
			dy=Math.abs(y-n_cont.col.get(0));
			
			dist=Math.sqrt(dx*dx+dy*dy);
			beta=n_cont.angle.get(0);
			
			diff=Math.abs(alpha-beta);
			
			if(diff>=Math.PI/2.0) {
				diff=Math.PI-diff;
			}
			 
			
			if(dist<min_d && dx<a && diff<b) {
				
				min_d=dist;
				index=j;
				rev=false;
			}
			
			// ending point comparison
			dx=Math.abs(x-n_cont.row.get(n_num_pnt-1));            // ending point
			dy=Math.abs(y-n_cont.col.get(n_num_pnt-1));
			dist=Math.sqrt(dx*dx+dy*dy);
			
			beta=n_cont.angle.get(n_num_pnt-1);
			diff=Math.abs(alpha-beta);
			if(diff>=Math.PI/2.0) {
				diff=Math.PI-diff;
			}
			 
			 
			if(dist<min_d && dx<a && diff<b) {
				
				min_d=dist;
				index=j;
				rev=true;
			}
			
		}
		
		if(index!=-1 && min_d<c) {
			n_cont = cont.get(index);  
			n_num_pnt = (int)n_cont.num;
			
			if(rev==true) {
				jun.add(new junc_pnt(n_cont.row.get(n_num_pnt-1), n_cont.row.get(n_num_pnt-1),i));
			}
			else {
				jun.add(new junc_pnt(n_cont.row.get(0), n_cont.row.get(0),i));
			}
			
			for(int k=0;k<jun.size();k++) {
				if(jun.get(k).id==index) {
					jun.get(k).id=i;
				}
			}
			
			
			
			if(rev==true) {
				Collections.reverse(n_cont.row);
                Collections.reverse(n_cont.col);
                Collections.reverse(n_cont.angle);
                Collections.reverse(n_cont.response);
                
			}
		
            
			cont.get(i).row.addAll(n_cont.row);
			cont.get(i).col.addAll(n_cont.col);
			cont.get(i).angle.addAll(n_cont.angle);
			cont.get(i).response.addAll(n_cont.response);
			cont.get(i).num+=n_cont.num;
			cont.remove(index);
			return 1;
		}
	
	

		return -1;
		
	}
	
	

	
	public int cal_next_s(int i) {
		
		
		int c_num_pnt, n_num_pnt, index=-1;
		float x, y, dx, dy;
		double alpha, beta, dist, diff, min_d=Float.MAX_VALUE;
		boolean rev=true;
		contour  c_cont,n_cont;
		jun = new ArrayList<junc_pnt>();
		
	//  searching closest to end of current contour
		c_cont=cont.get(i);                    // contour
		c_num_pnt=(int)c_cont.num;             // number of points
		x=cont.get(i).row.get(0);  
		y=cont.get(i).col.get(0);
		alpha=cont.get(i).angle.get(0);
		
		
		for(int j=i+1;j<cont.size();j++) {
			
			// checking point
			n_cont = cont.get(j);  
			n_num_pnt = (int)n_cont.num;
			
			
			//starting point comparison
			dx=Math.abs(x-n_cont.row.get(0));           
			dy=Math.abs(y-n_cont.col.get(0));
			
			dist=Math.sqrt(dx*dx+dy*dy);
			beta=n_cont.angle.get(0);
			
			diff=Math.abs(alpha-beta);
			
			if(diff>=Math.PI/2.0) {
				diff=Math.PI-diff;
			}
			 
			
			if(dist<min_d && dx<a && diff<b) {
				
				min_d=dist;
				index=j;
				rev=false;
			}
			
			// ending point comparison
			dx=Math.abs(x-n_cont.row.get(n_num_pnt-1));            // ending point
			dy=Math.abs(y-n_cont.col.get(n_num_pnt-1));
			dist=Math.sqrt(dx*dx+dy*dy);
			
			beta=n_cont.angle.get(n_num_pnt-1);
			diff=Math.abs(alpha-beta);
			if(diff>=Math.PI/2.0) {
				diff=Math.PI-diff;
			}
			 
			 
			if(dist<min_d && dx<a && diff<b) {
				
				min_d=dist;
				index=j;
				rev=true;
			}
			
		}
		
		if(index!=-1 && min_d<c) {
			n_cont = cont.get(index);  
			n_num_pnt = (int)n_cont.num;
			
			if(rev==true) {
				jun.add(new junc_pnt(n_cont.row.get(n_num_pnt-1), n_cont.row.get(n_num_pnt-1),i));
			}
			else {
				jun.add(new junc_pnt(n_cont.row.get(0), n_cont.row.get(0),i));
			}
			
			for(int k=0;k<jun.size();k++) {
				if(jun.get(k).id==index) {
					jun.get(k).id=i;
				}
			}
			
			
			
			if(rev==true) {
				Collections.reverse(n_cont.row);
                Collections.reverse(n_cont.col);
                Collections.reverse(n_cont.angle);
                Collections.reverse(n_cont.response);
                
			}
			Collections.reverse(c_cont.row);
            Collections.reverse(c_cont.col);
            Collections.reverse(c_cont.angle);
            Collections.reverse(c_cont.response);
            
			cont.get(i).row.addAll(n_cont.row);
			cont.get(i).col.addAll(n_cont.col);
			cont.get(i).angle.addAll(n_cont.angle);
			cont.get(i).response.addAll(n_cont.response);
			cont.get(i).num+=n_cont.num;
			cont.remove(index);
			return 1;
		}
	
	

		return -1;
		
	}
	
	

	
	
	public double cal_angle(int pos) {
		double alpha, nx, ny;
		nx = -normy[pos];           
	    ny = normx[pos];
	    alpha = Math.atan2(ny,nx);                          	           
	    if (alpha < 0.0)         alpha += 2.0*Math.PI;
	    if (alpha >= Math.PI)    alpha -= Math.PI;
	    return alpha;
	}
	
	
	
	// ................ends here
	
	public OvalRoi debug_show_tracing(int pos) {
		OvalRoi resolved_p;
	    	resolved_p = new OvalRoi(posy[pos]-0.25,posx[pos]-0.25, 0.5, 0.5);
    		resolved_p.setStrokeColor(Color.YELLOW);
    		resolved_p.setPosition(nFrame+1);
    		image_overlay.add(resolved_p);			    
    		imp.setOverlay(image_overlay);
    		imp.updateAndRepaintWindow();
    		imp.show();
		return resolved_p;
	}
	
	public void double_response(int octant, long x,long y,long num_cont, double alpha) {
		for (int i=0;i<2;i++) {
	    	long nextx = x+cleartab[octant][i][0];
	    	long nexty = y+cleartab[octant][i][1];
	    	if (nextx < 0 || nextx >= height || nexty < 0 || nexty >= width)   continue;
	      
	    	int nextpos = (int) LINCOOR(nextx,nexty,width);
	    	if (ismax[nextpos] > 0) {
	    		double nextalpha = cal_angle(nextpos);
	    		double diff = Math.abs(alpha-nextalpha);
	    		if (diff >= Math.PI/2.0)       diff = Math.PI-diff;
	        
	    		if (diff < MAX_ANGLE_DIFFERENCE) {                // if angle diff is less than max angle diff mark point as done
	    			label[nextpos] = (short) (num_cont+1);                     
	    			if (Math.abs(indx[nextpos])>0)    cross.get((int) (indx[nextpos]-1)).done = true;
	    		}
	    	}
	    }
	}
	
	public int select_octant(int octant, int last_octant) {
		switch(octant) {
    	case 0:
    		if (last_octant >= 3 && last_octant <= 5) octant = 4;
    		break;
    	case 1:
    		if (last_octant >= 4 && last_octant <= 6)  octant = 5;
    		break;
    	case 2:
    		if (last_octant >= 5 && last_octant <= 7)  octant = 6;
    		break;
    	case 3:
    		if (last_octant == 0 || last_octant >= 6)  octant = 7;
    		break;
	}
		
		return octant;
	}

	public double cal_beta(double beta, double last_beta) {
		double  diff1, diff2;                 // Orient normal to the line direction w.r.t. the last normal.
		
        if (beta < 0.0)         beta += 2.0*Math.PI;
        if (beta >= Math.PI)    beta -= Math.PI;
        
        diff1 = Math.abs(beta-last_beta);
        
        if (diff1 >= Math.PI)    diff1 = 2.0*Math.PI-diff1;
        
        diff2 = Math.abs(beta+Math.PI-last_beta);
        if (diff2 >= Math.PI)      diff2 = 2.0*Math.PI-diff2;
        
        if (diff1 < diff2) {                                             // add the angle
          last_beta = beta;
        } else {
          last_beta = beta+Math.PI;
        }
        return last_beta;
	}
	

	
/** ...................1....getting image parameters........................*/	
	public boolean get_image_parameters(int i) {
		imp = IJ.openImage(str);
		if(null == imp){                                                                            
			IJ.noImage();	
			return false;
		}		
		//else if (imp.getType() != ImagePlus.GRAY8 && imp.getType() != ImagePlus.GRAY16 ){
			//IJ.error("8, 16 or 32-bit grayscale image required");       
			//return false;
		//}
		Opener opener = new Opener();
		ImagePlus img = opener.openImage(directory.getPath() + File.separator + "img" + i + ".jpg");
		
		
	//	int subimage_Width = img.getWidth();
    //    int subimage_Height = img.getHeight();
		
		
		
		ip = img.getProcessor();
		//ip = imp.getProcessor();
		nCurrentSlice = imp.getCurrentSlice();
		width = ip.getWidth();
		height = ip.getHeight();
		nStackSize = imp.getStackSize();
		return true;
	}

	/**................2.......Showing parameter dialog........................*/
	public boolean show_parameters_dialog(){	
		int nDetectionType;
		String [] DetectionType = new String [] {"White lines on dark background", "Dark lines on white background"};
		String [] RoiType = new String [] {"Nothing", "Only lines", "Lines of average width", "Lines with width border"};
		String [] RoiColor = new String [] {"Rainbow", "Black", "White", "Blue", "Cyan","Green","Magenta","Orange","Pink","Red","Yellow"};
		String [] sLabelsCheckbox = new String [] {"subpixel x,y (red circles)","eigenvector (green arrows)", "tracing lines (yellow circles)","eigenvalues", "extensions (green circles)","junctions"};
		boolean [] sLabelsDefault = new boolean [] {Prefs.get("stegers_source.show_subpixel", DEBUG_show_subpixel), Prefs.get("stegers_source.show_eigenvector", DEBUG_show_eigenvector), Prefs.get("stegers_source.show_tracing", DEBUG_show_tracing), Prefs.get("stegers_source.show_eigenvalue", DEBUG_show_eigenvalue),Prefs.get("stegers_source.show_extensions", DEBUG_show_extensions),Prefs.get("stegers_source.show_junctions", DEBUG_show_junctions), };
		
		GenericDialog paramD = new GenericDialog("Parameters");
		paramD.addChoice("Detection type:", DetectionType, Prefs.get("stegers_source.mode_light", "White lines on dark background"));
		//paramD.setInsets(10, 50, 0); 
		paramD.addNumericField("Line width (SD of profile)", Prefs.get("stegers_source.sigma", SIGMA), 2, 4,"pixels");
		//paramD.setInsets(10, 100, 0);
		paramD.addNumericField("Maximum angle difference", Prefs.get("stegers_source.max_angle_difference", MAX_ANGLE_DIFFERENCE*180/Math.PI), 1,4,"degrees");
		paramD.setInsets(10, 50, 0); 
		paramD.addCheckbox("Extend line ends", Prefs.get("stegers_source.extend_lines", extend_lines));
		paramD.addNumericField("Maximum line extension", Prefs.get("stegers_source.max_line_extension", MAX_LINE_EXTENSION/SIGMA), 1,4,"*line width, pixels");
		paramD.setInsets(10, 50, 0);
		paramD.addCheckbox("Split lines at junctions", Prefs.get("stegers_source.split_lines", split_lines));
		paramD.addNumericField("Minimum number of points in line", Prefs.get("stegers_source.min_nodes", nMinNumberOfNodes), 0,3,"");
		paramD.setInsets(10, 50, 0);
		//
	    paramD.addNumericField("Maximum Angle of deviation", Prefs.get("stegers_source.dev-angle", angledev), 1,4,"degrees");
		paramD.setInsets(10, 50, 0);
		//
		paramD.addCheckbox("Correct line position", Prefs.get("stegers_source.correct_pos", correct_pos));
		paramD.setInsets(10, 50, 0);
		paramD.addCheckbox("Compute line width", Prefs.get("stegers_source.compute_width", compute_width));
		paramD.addNumericField("Maximum width search", Prefs.get("stegers_source.max_line_width", MAX_LINE_WIDTH/SIGMA), 1,4,"*line width, pixels");
		paramD.setInsets(10, 0, 0);
		//MAX_LINE_WIDTH = (2.5*SIGMA);
		paramD.addChoice("Add to overlay and RoiManager:", RoiType, Prefs.get("stegers_source.roitype", "Only lines"));
		paramD.addChoice("Color of added lines:", RoiColor, Prefs.get("stegers_source.roicolor", "Rainbow"));
		paramD.addMessage("~~~~~~~~~~~~ Learning/Debug  ~~~~~~~~~~~~");
		paramD.addCheckboxGroup(3,3,sLabelsCheckbox,sLabelsDefault);
		paramD.setResizable(false);
		paramD.showDialog();
		if (paramD.wasCanceled()) return false;
		nDetectionType = paramD.getNextChoiceIndex();
		Prefs.set("stegers_source.mode_light", DetectionType[nDetectionType]);
		if(nDetectionType ==0)  MODE_LIGHT = true;
		else  MODE_LIGHT = false;
		SIGMA = paramD.getNextNumber();
		Prefs.set("stegers_source.sigma", SIGMA);
		MAX_ANGLE_DIFFERENCE = paramD.getNextNumber()*Math.PI/180;
		Prefs.set("stegers_source.max_angle_difference", MAX_ANGLE_DIFFERENCE*180/Math.PI);		
		extend_lines = paramD.getNextBoolean();
		Prefs.set("stegers_source.extend_lines", extend_lines);
		MAX_LINE_EXTENSION = paramD.getNextNumber()*SIGMA;
		Prefs.set("stegers_source.max_line_extension", MAX_LINE_EXTENSION/SIGMA);	
		split_lines = paramD.getNextBoolean();
		Prefs.set("stegers_source.split_lines", split_lines);
		nMinNumberOfNodes = (int) paramD.getNextNumber();
		Prefs.set("stegers_source.min_nodes", nMinNumberOfNodes);
		//
		angledev = (int) paramD.getNextNumber();
		Prefs.set("stegers_source.dev_angle", angledev);
		//
		correct_pos = paramD.getNextBoolean();
		Prefs.set("stegers_source.correct_pos", correct_pos);
		compute_width = paramD.getNextBoolean();
		Prefs.set("stegers_source.compute_width", compute_width);
		MAX_LINE_WIDTH = paramD.getNextNumber()*SIGMA;
		Prefs.set("stegers_source.max_line_width", MAX_LINE_WIDTH/SIGMA);		
		nRoiLinesType = paramD.getNextChoiceIndex();
		Prefs.set("stegers_source.roitype", RoiType[nRoiLinesType]);
		nRoiLinesColor = paramD.getNextChoiceIndex();
		Prefs.set("stegers_source.roicolor", RoiColor[nRoiLinesColor]);
		DEBUG_show_subpixel = paramD.getNextBoolean();
		Prefs.set("stegers_source.show_subpixel", DEBUG_show_subpixel);
		DEBUG_show_eigenvector = paramD.getNextBoolean();
		Prefs.set("stegers_source.show_eigenvector", DEBUG_show_eigenvector);
		DEBUG_show_tracing = paramD.getNextBoolean();
		Prefs.set("stegers_source.show_tracing", DEBUG_show_tracing);
		DEBUG_show_eigenvalue = paramD.getNextBoolean();
		Prefs.set("stegers_source.show_eigenvalue", DEBUG_show_eigenvalue);
		DEBUG_show_extensions = paramD.getNextBoolean();
		Prefs.set("stegers_source.show_extensions", DEBUG_show_extensions);
		DEBUG_show_junctions = paramD.getNextBoolean();
		Prefs.set("stegers_source.show_junctions", DEBUG_show_junctions);
		return true;
	}
	
/**....................3....Computing line parameters........................*/
	public void compute_line_points() {
// For each point in the image determine whether there is a local maximum of second directional derivative in direction (nx[l],ny[l]) within pixels's boundaries.  
//set ismax[l] to 2 if ev[l] is larger than high, 1 if ev[l] is larger than low, 0 otherwise. 
//			                     (px[l],py[l]) ........  sub-pixel position of the maximum.
//		                        Parameter mode --- maxima (dark lines points) or  minima (bright line points)  
//		                        The partial derivatives of the image are input as ku[]. 
		double[][] line_points;
		position poscalc = new position(ip, SIGMA);
		
		//get line points		
		line_points = poscalc.compute_line_points(MODE_LIGHT);
		
		//old version  ----  line_points = DerivativeOfGaussian.get_line_points_stegers(ip, SIGMA, MODE_LIGHT);
		eigval = line_points[0];   //	[0] = first eigenvalue
		normx  = line_points[1];   //	[1] = eigenvector coordinate y
		normy  = line_points[2];   //	[2] = eigenvector coordinate x
		gradx  = line_points[3];   //	[3] = derivative of image in y
		grady  = line_points[4];   //	[4] = derivative of image in x
		posx   = line_points[5];   //	[5] = "super-resolved" y		t_y, or dlpy
		posy   = line_points[6];   //	[6] = "super-resolved" x		t_x, or dlpx
	}
	
/**....................4....Computing threshold levels........................*/	
	public boolean get_threshold_levels(){
/// Provides all valid points with saliency value at it and dialogue to pick thresholds for line detection 
		int l;
		double val;
		
		//new image processor with saliency map 
		final ImageProcessor threshold_tmp_ip = new FloatProcessor((int)width, (int)height);
		
		for(int py = 0; py < height; ++py){
			for(int px = 0; px < width; ++px){
				l = (int) LINCOOR(py,px,width);
				val = eigval[l];
				if(!Double.isNaN(posx[l])){
					if(Math.abs(posy[l]-px)<=PIXEL_BOUNDARY && Math.abs(posx[l]-py)<=PIXEL_BOUNDARY && val>0){
						threshold_tmp_ip.setf(px,py,(float) val);
					}				
					else
						threshold_tmp_ip.setf(px,py,(float) 0);
				}
			}		
		}//for cycle
		
		threshold_tmp_ip.resetMinAndMax();
		final double threshold_tmp_ip_min = threshold_tmp_ip.getMin();
		final double threshold_tmp_ip_max = threshold_tmp_ip.getMax();
		final double range = threshold_tmp_ip_max - threshold_tmp_ip_min;
		             //final ImagePlus threshold_imp_fl = new ImagePlus("Threshold map float", threshold_tmp_ip);
		             //final ImageProcessor threshold_ip = threshold_tmp_ip.convertToByteProcessor(); // final to make accessible in anonymous inner class
		final ImagePlus threshold_imp = new ImagePlus("Threshold map", threshold_tmp_ip); // final to make accessible in anonymous inner class		
		final double threshold_map_scale_factor = (range) / 256;
		threshold_imp.resetDisplayRange();
		            //threshold_imp_fl.show();
		threshold_imp.show();
		
		ThresholdSal thresholds_gd = new ThresholdSal(); // RSLV: make NonBlockingGenericDialog();
	
		thresholds_gd.addMessage("Upper threshold (green) defines where line tracing will start");
		thresholds_gd.addSlider("Upper threshold", threshold_tmp_ip_min, threshold_tmp_ip_max, Prefs.get("stegers_source.high", threshold_tmp_ip_min + 0.75*range)); // RSLV: limit?
		thresholds_gd.addMessage("Lower threshold (blue) defines the area where line tracing will occur");
		thresholds_gd.addSlider("Lower threshold", threshold_tmp_ip_min, threshold_tmp_ip_max, Prefs.get("stegers_source.low",threshold_tmp_ip_min + 0.25*range)); // RSLV: limit?
		
		DialogListener dlg_listener = new DialogListener(){
			//@Override
			public boolean dialogItemChanged(GenericDialog gd, java.awt.AWTEvent e){
				// get threshold parameters
				double ut = gd.getNextNumber()-threshold_tmp_ip_min;
				double lt = gd.getNextNumber()-threshold_tmp_ip_min;
							
				// use luts to highlight regions
				// NOTE: LUTs are 8-bit, so map is only an approximation
				int but = (int)(ut / threshold_map_scale_factor);
				int blt = (int)(lt / threshold_map_scale_factor);
				byte[] threshold_lut_r = new byte[256];
				byte[] threshold_lut_g = new byte[256];
				byte[] threshold_lut_b = new byte[256];
				
				// create lut
				for(int i = 0; i < 256; ++i){
					if(i < blt){
						// retain gray scale
						threshold_lut_r[i] = (byte)i;
						threshold_lut_g[i] = (byte)i;
						threshold_lut_b[i] = (byte)i;
					}
					else if(i < but){
						// set to lower threshold colour
						threshold_lut_r[i] = (byte)0;
						threshold_lut_g[i] = (byte)0;
						threshold_lut_b[i] = (byte)255;
					}
					else{
						// set to upper threshold colour
						threshold_lut_r[i] = (byte)0;
						threshold_lut_g[i] = (byte)255;
						threshold_lut_b[i] = (byte)0;
					}
				}
				LUT threshold_lut = new LUT(threshold_lut_r, threshold_lut_g, threshold_lut_b);
				
				// set LUT and update image
				//threshold_ip.setLut(threshold_lut);
				threshold_tmp_ip.setLut(threshold_lut);
				threshold_imp.updateAndDraw();
				return true; // true == accepted values, false == incorrect values
			}
		};
		thresholds_gd.addDialogListener(dlg_listener);
		dlg_listener.dialogItemChanged(thresholds_gd, null); // force update of lut
		thresholds_gd.setOKLabel("Continue tracing");
		thresholds_gd.setCancelLabel("Cancel tracing");
		
		// focus on threshold imp window, (sometimes get lost behind other debug image windows)
		threshold_imp.getWindow().setVisible(true);
		threshold_imp.getWindow().toFront();
		thresholds_gd.showDialog();
		
		if(thresholds_gd.wasCanceled()){
			threshold_imp.close();
			return false;
		}
		
		// get user specified threshold
		high = thresholds_gd.getNextNumber();
		Prefs.set("stegers_source.high",high);
		low = thresholds_gd.getNextNumber();
		Prefs.set("stegers_source.low",low);
		threshold_imp.close();
		return true;
	}
	
/** ...................5...Get valid line points (maxima within boundary of pixel, plus higher than low and high)   */
	public void getismax(double low, double high){
		ismax = new int [(int)(width*height)];
		int l;
		double val;
		for(int py = 0; py < height; ++py){
			for(int px = 0; px < width; ++px){
				l = (int) LINCOOR(py,px,width);
				val = eigval[l];
				if(!Double.isNaN(posx[l])){
					if(Math.abs(posy[l]-px)<=PIXEL_BOUNDARY && Math.abs(posx[l]-py)<=PIXEL_BOUNDARY){
						if(val>=low){
							if(val>=high)
								ismax[l]=2;
							else
								ismax[l]=1;
						}
					}
					else
						ismax[l]=0;
				}
				else
					ismax[l]=0;
			}
		}
	}	

/**....................6...adding to Roi sub-pixel position of line points...........*/		
	private void add_subpixelxy() {
		OvalRoi resolved_p;
		int l;
		 for (int zzpx=0; zzpx<width; zzpx++) {
				for (int zzpy=0; zzpy<height; zzpy++) {
					l = (int) LINCOOR(zzpy,zzpx,width);
					//* show super-resolved lines positions		    	 
					 resolved_p = new OvalRoi(posy[l]-0.25,posx[l]-0.25, 0.5, 0.5);
					 resolved_p.setPosition(nFrame+1);
			    	 resolved_p.setStrokeColor(Color.RED);
			    	 image_overlay.add(resolved_p);	
				}
		    }
	}	
	
/**....................7... Extract the line width by using a facet model line detector on an image of absolute value of gradient. */
	void compute_line_width(double[] dx,double[] dy){
	  double[]   grad;
	  int    i, j, k;
	  int    r, c;
	  int l;
	  long    x, y, dir;
	  ArrayList<offset> line;
	  int    num_line;//max_line, 
	  double  length;
	  contour tmp_cont;
	  long    num_points;// max_num_points;
	  float[]   width_r, width_l;
	  float[]  grad_r, grad_l;
	  float[]   pos_x, pos_y;// correct, asymm, contrast;
	  double  d, dr, dc, drr, drc, dcc;
	  double  i1, i2, i3, i4, i5, i6, i7, i8, i9;
	  double  t1, t2, t3, t4, t5, t6;
	  double[][]  eigvalvect;	  
	  double  a, b, t;
	  //long    num;
	  double  nx, ny;
	  double  n1, n2;
	  double  p1, p2;
	  double  val;
	  double  px, py;
	  int    num_contours;
	  


	  /*  max_num_points = 0;
	  for (i=0; i<num_contours; i++) {
	    num_points = contours[i]->num;
	    if (num_points > max_num_points)
	      max_num_points = num_points;
	  }

	  width_l = xcalloc(max_num_points,sizeof(*width_l));
	  width_r = xcalloc(max_num_points,sizeof(*width_r));
	  grad_l = xcalloc(max_num_points,sizeof(*grad_l));
	  grad_r = xcalloc(max_num_points,sizeof(*grad_r));
	  pos_x = xcalloc(max_num_points,sizeof(*pos_x));
	  pos_y = xcalloc(max_num_points,sizeof(*pos_y));
	  correct = xcalloc(max_num_points,sizeof(*correct));
	  contrast = xcalloc(max_num_points,sizeof(*contrast));
	  asymm = xcalloc(max_num_points,sizeof(*asymm));

	  grad = xcalloc(width*height,sizeof(*grad));
	  memset(grad,0,width*height*sizeof(*grad));*/
	  grad = new double[(int) (width*height)];

	  length = MAX_LINE_WIDTH;
	/*  max_line = ceil(length*3);
	  line = xcalloc(max_line,sizeof(*line));*/

	  /* Compute the gradient image. */
	  for (r=0; r<height; r++) {
	    for (c=0; c<width; c++) {
	      l = (int) LINCOOR(r,c,width);
	      grad[l] = Math.sqrt(dx[l]*dx[l]+dy[l]*dy[l]);
	    }
	  }

	  num_contours = this.cont.size();
	  for (i=0; i<num_contours; i++) {
	    tmp_cont = this.cont.get((int) i);
	    num_points = tmp_cont.num;
	    
	    pos_x = new float[(int) num_points];
	    pos_y = new float[(int) num_points];
	    grad_r = new float[(int) num_points];
	    grad_l = new float[(int) num_points];
	    width_r = new float[(int) num_points];
	    width_l = new float[(int) num_points];

	    
	    
	    for (j=0; j<num_points; j++) {	    		    	
	      px = tmp_cont.row.get(j);
	      py = tmp_cont.col.get(j);
	      pos_x[j] = (float) px;
	      pos_y[j] = (float) py;
	      r = (int) Math.floor(px+0.5);
	      c = (int) Math.floor(py+0.5);
	      nx = Math.cos(tmp_cont.angle.get(j));
	      ny = Math.sin(tmp_cont.angle.get(j));
	      // Compute the search line. 
	      line = bresenham(nx,ny,0.0,0.0,length);
	      width_r[j] = width_l[j] = 0;
	      // Look on both sides of the line. 
	      for (dir=-1; dir<=1; dir+=2) {
	    	  num_line = line.size();
	        for (k=0; k<num_line; k++) {
	          x = BR(r+dir*line.get(k).x);
	          y = BC(c+dir*line.get(k).y);
	          i1 = grad[(int) LINCOOR(BR(x-1),BC(y-1),width)];
	          i2 = grad[(int) LINCOOR(BR(x-1),y,width)];
	          i3 = grad[(int) LINCOOR(BR(x-1),BC(y+1),width)];
	          i4 = grad[(int) LINCOOR(x,BC(y-1),width)];
	          i5 = grad[(int) LINCOOR(x,y,width)];
	          i6 = grad[(int) LINCOOR(x,BC(y+1),width)];
	          i7 = grad[(int) LINCOOR(BR(x+1),BC(y-1),width)];
	          i8 = grad[(int) LINCOOR(BR(x+1),y,width)];
	          i9 = grad[(int) LINCOOR(BR(x+1),BC(y+1),width)];
	          t1 = i1+i2+i3;
	          t2 = i4+i5+i6;
	          t3 = i7+i8+i9;
	          t4 = i1+i4+i7;
	          t5 = i2+i5+i8;
	          t6 = i3+i6+i9;
	          dr = (t3-t1)/6;
	          dc = (t6-t4)/6;
	          drr = (t1-2*t2+t3)/6;
	          dcc = (t4-2*t5+t6)/6;
	          drc = (i1-i3-i7+i9)/4;
	          //eigvalvect = compute_eigenvals(2*drr,drc,2*dcc,eigval,eigvec);
	          eigvalvect = position.compute_eigenvals(2*drr,drc,2*dcc);
	          val = -eigvalvect[0][0];
	          if (val > 0.0) {
	            n1 = eigvalvect[1][0];
	            n2 = eigvalvect[1][1];
	            a = 2.0*(drr*n1*n1+drc*n1*n2+dcc*n2*n2);
	            b = dr*n1+dc*n2;
	            //solve_linear(a,b,&t,&num);
	            t= (-1)*b/a;
	            //if (num != 0) {
	            if (!Double.isNaN(t)) {
	              p1 = t*n1;
	              p2 = t*n2;
	              if (Math.abs(p1) <= 0.5 && Math.abs(p2) <= 0.5) {
	                /* Project the maximum point position perpendicularly onto the
	                   search line. */
	                a = 1;
	                b = nx*(px-(r+dir*line.get(k).x+p1))+ny*(py-(c+dir*line.get(k).y+p2));
	                //solve_linear(a,b,&t,&num);
	                t= (-1)*b/a;
	                d = (-i1+2*i2-i3+2*i4+5*i5+2*i6-i7+2*i8-i9)/9;
	                if (dir == 1) {
	                  grad_r[j] = (float) (d+p1*dr+p2*dc+p1*p1*drr+p1*p2*drc+p2*p2*dcc);
	                  width_r[j] = (float) Math.abs(t);
	                } else {
	                  grad_l[j] = (float) (d+p1*dr+p2*dc+p1*p1*drr+p1*p2*drc+p2*p2*dcc);
	                  width_l[j] = (float) Math.abs(t);
	                }
	                break;
	              }
	            }
	          }
	        }
	      }
	    }

	    fix_locations(width_l,width_r,grad_l,grad_r,pos_x,pos_y,tmp_cont);
	  }
/*
	  free(line);
	  free(grad);
	  free(asymm);
	  free(contrast);
	  free(correct);
	  free(pos_y);
	  free(pos_x);
	  free(grad_r);
	  free(grad_l);
	  free(width_r);
	  free(width_l);*/
	}	
	
/**....................8...adding to Roi transverse eigenvectors at each pixel........*/
	private void add_eigenvectors() {
		Arrow eigenvector_arr;
		int l;
		float[] dxx =new float[2];
		float[] dyy =new float[2];
		 for (int zzpx=0; zzpx<width; zzpx++) {
				for (int zzpy=0; zzpy<height; zzpy++) {
					l = (int) LINCOOR(zzpy,zzpx,width);
			    	
			    	//* Show vectors directions
			    	 dxx[0] = (float) (zzpx+0.5*normx[l]+0.5);
			    	 dxx[1] = (float) (zzpx-0.5*normx[l]+0.5);
			    	 dyy[0] = (float) (zzpy-0.5*normy[l]+0.5);
			    	 dyy[1] = (float) (zzpy+0.5*normy[l]+0.5);
			    	 eigenvector_arr = new Arrow(dxx[0], dyy[0], dxx[1], dyy[1]);		    	
					 eigenvector_arr.setStrokeColor(Color.GREEN);
					 eigenvector_arr.setPosition(nFrame+1);
					 eigenvector_arr.setStrokeWidth(0.05);
					 eigenvector_arr.setHeadSize(0.5);
					 image_overlay.add(eigenvector_arr);
				}
		    }
	}
	
/**....................9...adding to Roi subpixel position of line points..............*/
	private void add_junctions() {
		Roi junc_roi;
		for(int i=0; i<junc.size();i++){
			junc_roi = new Roi(junc.get(i).y-0.5,junc.get(i).x-0.5,1,1);
			junc_roi.setPosition(nFrame+1);
			junc_roi.setStrokeColor(Color.MAGENTA);
	    	image_overlay.add(junc_roi);		
		}
	}
		
	
	
/** Calculate the closest point to (px,py) on the line (lx,ly) + t*(dx,dy)
	 *  and return the result in (cx,cy), plus the parameter in t. */
	public doublepoint closest_point(double lx,double ly,double dx,double dy,double px,double py){	
	  doublepoint cp = new doublepoint(0,0,0); 
	  double mx, my, den, nom, tt;
	  mx = px-lx;
	  my = py-ly;
	  den = dx*dx+dy*dy;
	  nom = mx*dx+my*dy;
	  if (den != 0)  tt = nom/den;
	  else   tt = 0;
	  cp.cx = lx+tt*dx;
	  cp.cy = ly+tt*dy;
	  cp.t = tt;
	  return cp;
	}

/** Interpolate the gradient of the gradient images gradx and grady with width
	 * width at the point (px,py) using linear interpolation, and return the
	 * result in (gx,gy) (doublepoint format)
	 * */
	public doublepoint interpolate_gradient(double gradx[], double grady[], double px, double py, int width){
	  doublepoint gradxy = new doublepoint(0,0,0); 
	  long   gix, giy, gpos;
	  double gfx, gfy, gx1, gy1, gx2, gy2, gx3, gy3, gx4, gy4;

	  gix = (long) Math.floor(px);
	  giy = (long) Math.floor(py);
	  gfx = px % 1.0; //check whether it works as promised
	  gfy = py % 1.0;
	  gpos = LINCOOR(gix,giy,width);
	  gx1 = gradx[(int) gpos];
	  gy1 = grady[(int) gpos];
	  gpos = LINCOOR(gix+1,giy,width);
	  gx2 = gradx[(int) gpos];
	  gy2 = grady[(int) gpos];
	  gpos = LINCOOR(gix,giy+1,width);
	  gx3 = gradx[(int) gpos];
	  gy3 = grady[(int) gpos];
	  gpos = LINCOOR(gix+1,giy+1,width);
	  gx4 = gradx[(int) gpos];
	  gy4 = grady[(int) gpos];
	  gradxy.cx = (1-gfy)*((1-gfx)*gx1+gfx*gx2)+gfy*((1-gfx)*gx3+gfx*gx4);
	  gradxy.cy = (1-gfy)*((1-gfx)*gy1+gfx*gy2)+gfy*((1-gfx)*gy3+gfx*gy4);
	  return gradxy;
	}
	
/** Compute the response of the operator with sub-pixel accuracy by using the
	 *  facet model to interpolate the pixel accurate responses. */
	public double interpolate_response(double resp[],long x, long y, double px, double py, long width, long height){
	  double i1, i2, i3, i4, i5, i6, i7, i8, i9, t1, t2, t3, t4, t5, t6, d, dr, dc, drr, drc, dcc, xx, yy;

	  i1 = resp[(int) LINCOOR(BR(x-1),BC(y-1),width)];
	  i2 = resp[(int) LINCOOR(BR(x-1),y,width)];
	  i3 = resp[(int) LINCOOR(BR(x-1),BC(y+1),width)];
	  i4 = resp[(int) LINCOOR(x,BC(y-1),width)];
	  i5 = resp[(int) LINCOOR(x,y,width)];
	  i6 = resp[(int) LINCOOR(x,BC(y+1),width)];
	  i7 = resp[(int) LINCOOR(BR(x+1),BC(y-1),width)];
	  i8 = resp[(int) LINCOOR(BR(x+1),y,width)];
	  i9 = resp[(int) LINCOOR(BR(x+1),BC(y+1),width)];
	  t1 = i1+i2+i3;
	  t2 = i4+i5+i6;
	  t3 = i7+i8+i9;
	  t4 = i1+i4+i7;
	  t5 = i2+i5+i8;
	  t6 = i3+i6+i9;
	  d = (-i1+2*i2-i3+2*i4+5*i5+2*i6-i7+2*i8-i9)/9;
	  dr = (t3-t1)/6;
	  dc = (t6-t4)/6;
	  drr = (t1-2*t2+t3)/6;
	  dcc = (t4-2*t5+t6)/6;
	  drc = (i1-i3-i7+i9)/4;
	  xx = px-x;
	  yy = py-y;
	  return d+xx*dr+yy*dc+xx*xx*drr+xx*yy*drc+yy*yy*dcc;
	}
	
/** Threshold an image above min and return the result as a run-length encoded region in out. */
	public region threshold(int image[],long min){	
	region outr = new region();
	long   grey;
	long   r,c,l,num;
	boolean   inside;
	ArrayList<chord> rl = new ArrayList<chord>();
	inside = false;
	num = 0;
	rl.add(new chord());
	for (r=0; r<height; r++) {
	    for (c=0; c<width; c++) {
	      l = LINCOOR(r,c,width);
	      grey = (long) image[(int) l];
	      if (grey >=min) {
	        if (!inside) {
	        	inside = true;
	            rl.get((int) num).r = (short) r;
	            rl.get((int) num).cb = (short) c;
	        }
	      } else {
	        if (inside) {
	        	inside = false;
	            rl.get((int) num).ce =(short)(c - 1);
	            num++;
	            rl.add(new chord());
	        }
	      }
	    }
	    if (inside) {
	    	   inside = false;
	    	   rl.get((int)num).ce = (short)(width-1);
	    	   num++;
	    	   rl.add(new chord());
	    }
	  }
	outr.rl = new ArrayList<chord>(rl);
	outr.num = num;
	return outr;
	}
	
/** ........Translate row and column coordinates into  its one-dimensional array. */
	public static long LINCOOR(long row, long col,long width){ 
		return (long)((row)*(width)+(col));				
	}
	
/** Function adding all found contours to results table */
	public void add_results_table(){
		int i,j, num_pnt;
		contour tmp_cont;
		String Roi_name ="contour_f_";
		String Cont_name;
		Roi_name = Roi_name.concat(Integer.toString(nFrame+1));
		Roi_name = Roi_name.concat("_c_");
		
		
		for (i=0; i<cont.size(); i++) {
			 ArrayList<Float> str = new ArrayList<Float>();
			 ArrayList<Float> str2 = new ArrayList<Float>();
			  Cont_name = Roi_name.concat(Integer.toString(i+1));
			  tmp_cont = cont.get(i);  
			  num_pnt = (int) tmp_cont.num;
			  int k=0;
			  for(j=0;j<num_pnt;j++){
//				  ptable.incrementCounter();
//				  ptable.addLabel(Cont_name);
//				  ptable.addValue("Frame Number", nFrame+1);
//				  ptable.addValue("Contour Number", i+1);
//				  ptable.addValue("Point Number", j+1);
//				  ptable.addValue("X_(px)",tmp_cont.col.get(j));
//				  ptable.addValue("Y_(px)",tmp_cont.row.get(j));
//				  ptable.addValue("Angle_of_normal_(radians)",tmp_cont.angle.get(j));
//				  ptable.addValue("Response",tmp_cont.response.get(j));
//				  str.add(tmp_cont.col.get(j));
//			  	  str2.add(tmp_cont.row.get(j)); 
			  	  
				  if(j==0) {
					  str.add(tmp_cont.col.get(j));
			  	     str2.add(tmp_cont.row.get(j)); 
			  	     
			  	      ptable.incrementCounter();
					  ptable.addLabel(Cont_name);
					  ptable.addValue("Frame Number", nFrame+1);
					  ptable.addValue("Contour Number", i+1);
					  ptable.addValue("Point Number", j+1);
					  ptable.addValue("X_(px)",tmp_cont.col.get(j));
					  ptable.addValue("Y_(px)",tmp_cont.row.get(j));
					  ptable.addValue("Angle_of_normal_(radians)",tmp_cont.angle.get(j));
					  ptable.addValue("Response",tmp_cont.response.get(j));
					  k++;
				  }
				  //19/10
				  if(j!=0 && Math.abs((str.get(k-1).floatValue()) - tmp_cont.col.get(j)) >= -30) {
				// if(j!=0) {
					  str.add(tmp_cont.col.get(j));
				  	  str2.add(tmp_cont.row.get(j));
				  	  
				  	 ptable.incrementCounter();
					  ptable.addLabel(Cont_name);
					  ptable.addValue("Frame Number", nFrame+1);
					  ptable.addValue("Contour Number", i+1);
					  ptable.addValue("Point Number", j+1);
					  ptable.addValue("X_(px)",tmp_cont.col.get(j));
					  ptable.addValue("Y_(px)",tmp_cont.row.get(j));
					  ptable.addValue("Angle_of_normal_(radians)",tmp_cont.angle.get(j));
					  ptable.addValue("Response",tmp_cont.response.get(j));
					  k++;
				  }
				  if(compute_width){
					  ptable.addValue("Width_left_(px)",tmp_cont.width_l.get(j));
					  ptable.addValue("Width_right_(px)",tmp_cont.width_r.get(j));
					  if (correct_pos){
						  ptable.addValue("Assymetry",tmp_cont.asymmetry.get(j));
						  //does not make much sense
						  ptable.addValue("Contrast",tmp_cont.contrast.get(j));
					  }
				  } 
			  }
			  if(str.size()>0 && str2.size()>0) {
			//  System.out.println("contour no."+(i+1) + "_x:" + str);
			//  System.out.println("contour no."+(i+1) + "_y:" + str2);
			  }
		  }
	}
	
	
public void pyj(){
		
		try {
			ProcessBuilder builder = new ProcessBuilder("python", "C:\\Users\\winja\\Desktop\\SIV_WORK\\CurveTrace1\\mt_trace\\src\\stegers\\add.py", "1", "4");
			Process process = builder.start();
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			BufferedReader readers = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			
			String lines = null;
			
			while ((lines=reader.readLine()) != null) {
				System.out.println("lines" + lines);
				
			}
			while ((lines=readers.readLine()) != null) {
				System.out.println("Error lines: " + lines);
				
			}
				
			}
		catch(Exception e){
			e.printStackTrace();
					
		}
		
		
	}
	
		
/** Mirror row coordinate at the borders of image; height must be a defined variable in the calling function containing the image height. */
	public long BR(long row) {
		if (row < 0)  
			return -1*row; 				
		else{
			if (row >= height) 
				return height - row + height - 2; 
			else
				return row;
		}
	}

/** Mirror the column coordinate at the borders of the image; width must be a defined variable in the calling function containing the image width. */
	public long BC(long col) { 
	 if (col < 0) 
		 return (-1*col);
	 else{
		 if (col >= width)
			return  width - (col) + width - 2;
		 else 
	     	return col;
		 }
	}

/** Modified Bresenham algorithm. It returns in line all pixels that are intersected by a half line less than length away from the point (px,py)
	along the direction (nx,ny).  The point (px,py) must lie within the pixel of the origin, i.e., fabs(px) <= 0.5 and fabs(py) <= 0.5. */
	public ArrayList<offset> bresenham(double nx, double ny, double px, double py, double length){	
	  ArrayList<offset> line = new ArrayList<offset>(); 
	  int i, x, y, s1, s2, xchg, maxit;
	  double e, dx, dy, t;

	  x = 0;
	  y = 0;
	  dx = Math.abs(nx);
	  dy = Math.abs(ny);
	  s1 = (int) Math.signum(nx);
	  s2 = (int) Math.signum(ny);
	  px *= s1;
	  py *= s2;
	  if (dy > dx) {
	    t = dx;
	    dx = dy;
	    dy = t;
	    t = px;
	    px = py;
	    py = t;
	    xchg = 1;
	  } else {
	    xchg = 0;
	  }
	  maxit = (int) Math.ceil(length*dx);
	  e = (0.5-px)*dy/dx-(0.5-py);
	  for (i=0; i<=maxit; i++) {
	    line.add(new offset(x,y));		  	    
	  
	    while (e >= -1e-8) {
	      if (Math.abs(xchg)>0) x += s1;
	      else y += s2;
	      e--;
	      if (e > -1) {
	    	line.add(new offset(x,y));
	   
	      }
	    }
	    if (Math.abs(xchg)>0) y += s2;
	    else x += s1;
	    e += dy/dx;
	  }
//	  *num_points = n;
	  return line;
	}
	
/** Correct the extracted line positions and widths.  The algorithm first closes
	   gaps in the extracted data width_l, width_r, grad_l, and grad_r to provide
	   meaningful input over the whole line.  Then the correction is calculated.
	   After this, gaps that have been introduced by the width correction are again
	   closed.  Finally, the position correction is applied if correct_pos is set.
	   The results are returned in width_l, width_r, and cont. */
	public static void fix_locations(float[] width_l,float[] width_r, float[] grad_l,float[] grad_r, float[] pos_x, float[] pos_y, contour cont){
	  int    i;
	  long    num_points;
	  double  px, py;
	  double  nx, ny;
	  double  w_est, r_est, w_real, h_real, corr;//, w_strong, w_weak;
	  double  correct, asymmetry, response, widthx, contrast;
	  boolean    weak_is_r;
	  boolean    correct_start, correct_end;
	  
	  float[] asymm;
	  float[] contr;
	  float[][] tempd;
	  float[] correction;
	  correctionx corrx = new correctionx(); 

	  tempd = fill_gaps(width_l,grad_l,null,cont);
	  width_l = tempd[0];
	  grad_l = tempd[1];
	  tempd =fill_gaps(width_r,grad_r,null,cont);
	  width_r = tempd[0];
	  grad_r = tempd[1];
	  
	  num_points = cont.num;
	  asymm = new float[(int) num_points];
	  contr = new float[(int) num_points];
	  correction = new float[(int) num_points];

	  // Calculate true line width, asymmetry, and position correction. 
	  if(correct_pos) 
	  {
	    // Do not correct the position of a junction point if its width is found
	    // by interpolation, i.e., if the position could be corrected differently
	    // for each junction point, thereby destroying the junction. */
	    correct_start = ((cont.cont_class == contour_class.cont_no_junc ||
	                      cont.cont_class == contour_class.cont_end_junc ||
	                      cont.cont_class == contour_class.cont_closed) &&
	                     (width_r[0] > 0 && width_l[0] > 0));
	    correct_end = ((cont.cont_class == contour_class.cont_no_junc ||
	                    cont.cont_class == contour_class.cont_start_junc ||
	                    cont.cont_class == contour_class.cont_closed) &&
	                   (width_r[(int) (num_points-1)] > 0 && width_l[(int) (num_points-1)] > 0));
	    // Calculate the true width and assymetry, and its corresponding
	    //   correction for each line point. 
	    for (i=0; i<num_points; i++) 
	    {
	      if (width_r[i] > 0 && width_l[i] > 0) 
	      {
	        w_est = (width_r[i]+width_l[i])*LINE_WIDTH_COMPENSATION;
	        if (grad_r[i] <= grad_l[i]) 
	        {
	          r_est = grad_r[i]/grad_l[i];
	          weak_is_r = true;
	        } 
	        else 
	        {
	          r_est = grad_l[i]/grad_r[i];
	          weak_is_r = false;
	        }
	        //corrx = correct.line_corrections(new Double(SIGMA), new Double(w_est), new Double(r_est));
	        corrx = correctx.line_corrections(SIGMA, w_est, r_est);
	        
	        w_real = corrx.w;
	        h_real = corrx.h;
	        corr = corrx.correction;
	        //w_strong = corrx.w_strong;
	        //w_weak = corrx.w_weak;
	        w_real /= LINE_WIDTH_COMPENSATION;
	        corr /= LINE_WIDTH_COMPENSATION;
	        width_r[i] = (float) w_real;
	        width_l[i] = (float) w_real;
	        if (weak_is_r) {
	          asymm[i] = (float) h_real;
	          correction[i] = (float) (-corr);
	        } 
	        else {
	          asymm[i] = (float) (-h_real);
	          correction[i] = (float) corr;
	        }
	      }
	    }

	    tempd = fill_gaps(width_l,correction,asymm,cont);
	    width_l = tempd[0];
	    correction = tempd[1];
	    asymm = tempd[2];
	    
	    for (i=0; i<num_points; i++)
	      width_r[i] = width_l[i];

	    /* Adapt the correction for junction points if necessary. */
	    if (!correct_start)
	      correction[0] = 0;
	    if (!correct_end)
	      correction[(int) (num_points-1)] = 0;

	    for (i=0; i<num_points; i++) {
	      px = pos_x[i];
	      py = pos_y[i];
	      nx = Math.cos(cont.angle.get(i));
	      ny = Math.sin(cont.angle.get(i));
	      px = px+correction[i]*nx;
	      py = py+correction[i]*ny;
	      pos_x[i] = (float) px;
	      pos_y[i] = (float) py;
	    }
	  }


	  /* Update the position of a line and add the extracted width. */
	  //cont->width_l = xcalloc(num_points,sizeof(float));
	  //cont->width_r = xcalloc(num_points,sizeof(float));
	  
	  cont.width_l = new ArrayList<Float>();
	  cont.width_r = new ArrayList<Float>();
	  for (i=0; i<num_points; i++) {
	    cont.width_l.add(width_l[i]);
	    cont.width_r.add(width_r[i]);
	    cont.row.set(i,(float) pos_x[i]);
	    cont.col.set(i,(float) pos_y[i]);
	  }

	  /* Now calculate the true contrast. */
	  if (correct_pos) {
	    //cont->asymmetry = xcalloc(num_points,sizeof(float));
	    //cont->contrast = xcalloc(num_points,sizeof(float));
	    for (i=0; i<num_points; i++) {
	      response = cont.response.get(i);
	      asymmetry = Math.abs(asymm[i]);
	      correct = Math.abs(correction[i]);
	      widthx = cont.width_l.get(i);
	      if (widthx < MIN_LINE_WIDTH)
	        contrast = 0;
	      else
	        contrast = 
	          (response/Math.abs(convol.phi2(correct+widthx,SIGMA)+
	                         (asymmetry-1)*convol.phi2(correct-widthx,SIGMA)));
	      //seems like it always above threshold in my experience
	      //if (contrast > MAX_CONTRAST)
	      //  contrast = 0;
	      contr[i] = (float) contrast;
	    }
	  
	   tempd= fill_gaps(contr,null,null,cont);
	   contr=tempd[0];
	    
	    cont.asymmetry = new ArrayList<Float>();
	    cont.contrast = new ArrayList<Float>();
	    
	    for (i=0; i<num_points; i++) {
	      cont.asymmetry.add(asymm[i]);
	      if (MODE_LIGHT)
	        cont.contrast.add(contr[i]);
	      else
	        cont.contrast.add(-contr[i]);
	    }
	  }
	}

/** Fill gaps in the arrays master, slave1, and slave2, i.e., points where
	   master=0, by interpolation (interior points) or extrapolation (end points).
	   The array master will usually be the width of the line, while slave1 and
	   slave2 will be values that depend on master[i] being 0, e.g., the gradient
	   at each line point.  The arrays slave1 and slave2 can be NULL. 
	 * @return */
	public static float[][] fill_gaps(float[] master, float[] slave1, float[] slave2, contour t_cont){
	  int    i, j, k, s, e;
	  long    num_points;
	  double  m_s, m_e, s1_s, s1_e, s2_s, s2_e, d_r, d_c, arc_len, len;
	  
	  num_points = t_cont.num;
	  float [][] result_m_s = new float[3][master.length];

	  //for(i=0;i<master.length;i++)
	  //{
		  result_m_s[0]=master;
		  result_m_s[1]=slave1;
		  result_m_s[2]=slave2;
	  //}
	  
	  for (i=0; i<num_points; i++) {
	    if (result_m_s[0][i] == 0.0) {
	      for (j=i+1; j<num_points; j++) {
	        if (result_m_s[0][j] > 0)
	          break;
	      }
	      m_s = 0;
	      m_e = 0;
	      s1_s = 0;
	      s1_e = 0;
	      s2_s = 0;
	      s2_e = 0;
	      if (i > 0 && j < num_points-1) {
	        s = i;
	        e = j-1;
	        m_s = result_m_s[0][s-1];
	        m_e = result_m_s[0][e+1];
	        if (slave1 != null) {
	          s1_s = result_m_s[1][s-1];
	          s1_e = result_m_s[1][e+1];
	        }
	        if (slave2 != null) {
	          s2_s = result_m_s[2][s-1];
	          s2_e = result_m_s[2][e+1];
	        }
	      } else if (i > 0) {
	        s = i;
	        e = (int) (num_points-2);
	        m_s = result_m_s[0][s-1];
	        m_e = result_m_s[0][s-1];
	        result_m_s[0][e+1] = (float) m_e;
	        if (slave1 != null) {
	          s1_s = result_m_s[1][s-1];
	          s1_e = result_m_s[1][s-1];
	          result_m_s[1][e+1] = (float) s1_e;
	        }
	        if (slave2 != null) {
	          s2_s = result_m_s[2][s-1];
	          s2_e = result_m_s[2][s-1];
	          result_m_s[2][e+1] = (float) s2_e;
	        }
	      } else if (j < num_points-1) {
	        s = 1;
	        e = j-1;
	        m_s = result_m_s[0][e+1];
	        m_e = result_m_s[0][e+1];
	        result_m_s[0][s-1] = (float) m_s;
	        if (slave1 != null) {
	          s1_s = result_m_s[1][e+1];
	          s1_e = result_m_s[1][e+1];
	          result_m_s[1][s-1] = (float) s1_s;
	        }
	        if (slave2 != null) {
	          s2_s = result_m_s[2][e+1];
	          s2_e = result_m_s[2][e+1];
	          result_m_s[2][s-1] = (float) s2_s;
	        }
	      } else {
	        s = 1;
	        e = (int) (num_points-2);
	        m_s = result_m_s[0][s-1];
	        m_e = result_m_s[0][e+1];
	        if (slave1 != null) {
	          s1_s = result_m_s[1][s-1];
	          s1_e = result_m_s[1][e+1];
	        }
	        if (slave2 != null) {
	          s2_s = result_m_s[2][s-1];
	          s2_e = result_m_s[2][e+1];
	        }
	      }
	      arc_len = 0;
	      for (k=s; k<=e+1; k++) {
	        d_r = t_cont.row.get(k)-t_cont.row.get(k-1);
	        d_c = t_cont.col.get(k)-t_cont.col.get(k-1);
	        arc_len += Math.sqrt(d_r*d_r+d_c*d_c);
	      }
	      len = 0;
	      for (k=s; k<=e; k++) {
	        d_r = t_cont.row.get(k)-t_cont.row.get(k-1);
	        d_c = t_cont.col.get(k)-t_cont.col.get(k-1);
	        len += Math.sqrt(d_r*d_r+d_c*d_c);
	        result_m_s[0][k] = (float) ((arc_len-len)/arc_len*m_s+len/arc_len*m_e);
	        if (slave1 != null)
	        	result_m_s[1][k] = (float) ((arc_len-len)/arc_len*s1_s+len/arc_len*s1_e);
	        if (slave2 != null)
	        	result_m_s[2][k] = (float) ((arc_len-len)/arc_len*s2_s+len/arc_len*s2_e);
	      }
	      i = j;
	    }
	  }
	  return result_m_s;
	}

}