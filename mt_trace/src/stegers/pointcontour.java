package stegers;

import java.util.ArrayList;

public class pointcontour {
	
	/** number of points */
	public long  num
	/** row coordinates of the line points (Y coordinate in ImageJ) */;                
	public ArrayList<Float> row;
	/** column coordinates of the line points (X coordinate in ImageJ)  */
	public ArrayList<Float> col;
	/** width to the left of the line */
	public ArrayList<Float> width_l;
	/** width to the right of the line */
	public ArrayList<Float> width_r;
	
	//default constructor
		public pointcontour()
		{
			num = 0;
			row = new ArrayList<Float>();
			col = new ArrayList<Float>();
			width_l = new ArrayList<Float>();
			width_r = new ArrayList<Float>();
		}
		
		public pointcontour(long nnum, ArrayList<Float> nrow, ArrayList<Float> ncol, ArrayList<Float> widthL, ArrayList<Float> widthR)
		{
		    num=nnum;
		    row=nrow;
		    col=ncol;
		    width_l =widthL;
			width_r =widthR;
		}

}
