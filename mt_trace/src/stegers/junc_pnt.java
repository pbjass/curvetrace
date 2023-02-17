package stegers;

/** A chord in a run-length encoded region */
public class junc_pnt {
	
	 public float x;   
	
	 public float y;  
	 
	 public int id;  
	  
		
	  public junc_pnt()
	  {
				x=-1;
				y=-1;
				id=-1;
	  }
	  
	  public junc_pnt (float a,float b,int c)
	  {
			x=a;
			y=b;
			id=c;
	  }

}