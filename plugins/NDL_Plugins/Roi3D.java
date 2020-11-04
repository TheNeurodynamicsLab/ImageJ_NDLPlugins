/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package NDL_Plugins;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 *
 * @author Balaji
 */
public class Roi3D{
        
    //transient guiTranslateRoi roiTranslator;
    //TreeMap <Integer,serialisableRois> sRois;
    transient TreeMap<Integer,Roi>  rois; //Tree map datastructure for storing a collection of regular ImageJ Rois. 
                                // If the tree contains the rois about the same center but in different slices we might consider it as 
                                // 3D ROI. The requirement of same XY center is only suggestive but not enforced/expected.
                                // The slice number is used as a key and it is mapped to a 2D roi in that slice.
    
    private int nSlices ;               //the thickness span of the 3D ROI epxressed in number of image slices
    
    private int centerX; //Stores the 3D roi's center
    
    private int centerY; //Stores the 3D roi's center
    
    private int centerZ; //Stores the 3D roi's center
    
    private String Name;
   // private int centerSlice;
    private boolean validCenter;
    private boolean multipleRoiperSlice;
    private Integer centerSlice;
    
    /**
     * Function to fetch the 2D roi that is part of this 3D set and mapped to the slice number.
     * If there is no entry for the slice number the function will return null. This behavior is the reflection of the get method in 
     * collections framework of Java. 
     * @param slice the slice number for which the 2D ROi is requested for. Internally it is used as a key to fetch the Roi. 
     * @return : returns the 2D roi present in slice 
     */
    public final Roi get2DRoi(int slice){
               return rois.get(slice);
    }
    /**
     * Moves the ROIs to the new location given by in X and Y position (in pixels)
     * @param newX
     * @param newY 
     */
    public void translateRoisXY(int newX, int newY){
        rois.forEach((i,troi)->{
            troi.setLocation(newX, newY);
            //test here
        });
        //recenter();
    }
    public void translateRoiXY(int newX,int newY, int forSlice){
        rois.get(forSlice).setLocation(newX, newY);
    }   
    interface shiftPosition {
    abstract void shift(double x, double y);
    }
    
    public void translateRoisXYrel(double xShift, double yShift){
       rois.forEach((i,roi)->{
           Rectangle bounds = roi.getBounds();
           
           double newX = bounds.getX() + xShift;
           double newY = bounds.getY() + yShift;
           roi.setLocation(newX, newY);
        });
    }
    public void translateRoiXYrel(double xShift, double yShift, int forSlice){
        Roi roi = rois.get(forSlice);
        Rectangle bounds = roi.getBounds();
        //double centerX = bounds.getLocation().x + bounds.getWidth()/2.0 ;
        //double centerY = bounds.getLocation().y + bounds.getHeight()/2.0 ;
        double newX = bounds.getX() + xShift;
        double newY = bounds.getY() + yShift;
        roi.setLocation(newX,newY);
    }
    public Roi3D() {
        
        this.rois = new TreeMap<>();
        nSlices = 0;
        centerX = 0;
        centerY = 0;
        centerY = 0;
    }
    public Roi3D(int no_of_slices){
        
        this.rois = new TreeMap<>();
        nSlices = no_of_slices;
        centerX = 0;
        centerY = 0;
        centerY = 0;
    }
    public Roi3D(Roi[] roisArray){
        
        this.rois = new TreeMap<>();
        nSlices = 0;
        centerX = 0;
        centerY = 0;
        centerZ = 0;
        
        for(Roi roi:roisArray){
            this.rois.put(roi.getPosition(), roi);
        }
        FindCenter();
        
        nSlices = roisArray.length;  /* nSlices is not the thickness of the 3DRoi
                                     /* thickness can be obtained /set using the 
                                     /* idiom : this.rois.firstKey()- this.rois.lastKey();
                                     /*
                                     **/
    }

    public void FindCenter() {
        Rectangle bounds;
        centerZ =   (rois.lastKey() + rois.firstKey())/2;  
        this.centerSlice = rois.ceilingKey(centerZ);
        
        
        bounds = rois.firstEntry().getValue().getBounds();
        centerX = (int)bounds.getCenterX();
        centerY = (int)bounds.getCenterY();
        
        validCenter = true;
        
    }
    /**
     * Returns the first key (slice no) in this Treelist. The slice number is the key while the 2D rois are the values in 
     * this tree list. Thus the first key in this list coresponds to the lowest 'z' - position in this 3D roi. Use getEndSlice 
     * to get the highest(deepest) 'z' - position in this 3D roi. 
     * @return 
     */
    public int getStartSlice(){
        return rois.firstKey();
    }
    /**
     * Returns the last key (slice no) in this Treelist. The slice number is the key while the 2D rois are the values in 
     * this tree list. Thus the first key in this list coresponds to the highest 'z' - position in this 3D roi. Use getStartlice 
     * to get the lowest 'z' - position in this 3D roi. 
     * @return 
     */
    public int getEndSlice(){
        return rois.lastKey();
    }
    /**Returns the difference between the start and the endslice need not corespond the number of slice. Multiply this with z dimension to 
     * get the scale.      * 
     * @return 
     */
    public int getThickness() {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        
        return (rois.lastKey()-rois.firstKey()+1);
    }
    
    /**
     * This function iteratively compares the center of mass of roi with that of the geometric center (centroid) if the difference is more than
     * the threshold (limit) then moves the center of the roi (and hence the centroid ) to that of center of mass. This is done until the difference 
     * converges to less than than the set threshold.
     *
     * @param imp : It takes in the imageplus on which the rois need to be centered.
     * @return status : it returns if the recentering was sucessfull (ie did it converge).
     */
    public boolean Recenter(ImagePlus imp){
                                //yet to implement here. Currently it is implemented in the Time series analyser class. 
        return false;
    }
    /**This function repositions the 3D Roi in z (depth) to a new center which is different
     * from the original center by the argument zCtrDiff. The difference is simply added to the 
     * zPositions (i.e Difference is defined as new - old). This requires mapping to be changed and change in key values. 
     * One way to do this efficiently would be to check for the differences and for positive differences (moving the ROis 
     * towards the z = 0) the tree need to be iterated from the low key values during the remapping while for negative differences
     * iterate in the opposite direction. Currently it is inefficient as it creates another copy.
     * 
     * @param zCtrDiff : the difference by which the z center need to move. Difference = new - old (positive values pulls it up and 
     * negative values pushes down.
     * @return : returns the new center position (slice number)
     */
    public int repositionZ(int zCtrDiff){        //zCtrDiff := as newCenter - oldCenter
        
     //int newZCtr = this.centerZ + zCtrDiff;
     
    /* int oldStart = rois.firstKey();
     int oldEnd = rois.lastKey();
     Roi templateRoi  = rois.get(oldStart);
     TreeMap<Integer, Roi> tmpRois;
     tmpRois = new TreeMap<>();*/
                                                //Creating an all new list everytime is not a good way. However at this point  
                                                //this is the implementation. Next version need to implement the algo described 
                                                //in the top.
     //int newPosition = oldStart + zCtrDiff;    
     System.out.print("\nRepositioning: Current Center is = "+ centerZ + "The diff. is =  " + zCtrDiff);
     if(zCtrDiff > 0){
         Set<Integer> keyDesSet = rois.descendingKeySet();
         ArrayList <Integer> keys2keep = new ArrayList <>();
         keyDesSet.forEach(keys2keep::add);
         keys2keep.forEach((Integer key)->{
            rois.get(key).setPosition(key+zCtrDiff);
            rois.put(key+zCtrDiff, rois.get(key));
            rois.remove(key);
         });
         // int key = dkeys.get(0);
         //for(int count = 0 ; count < zCtrDiff ; count ++){
           //  rois.remove(key+count);
         //}
     }
     if(zCtrDiff < 0){
         Set<Integer> keySet = rois.keySet();
         ArrayList<Integer> keys = new ArrayList<>();
         //int newPosition = 0;
         keySet.forEach(keys::add);
         keys.forEach((Integer key)->{
            int newPosition = key+zCtrDiff > 0 ? key+zCtrDiff : 0;
            rois.get(key).setPosition(newPosition);
            rois.put(newPosition, rois.get(key));
            rois.remove(key);
         });
        /* int sz = keys.size();
         int lastKey =0;
         if (sz != 0)
             lastKey = keys.get(sz);
         for(int count = 0 ; count < zCtrDiff ; count ++){
             rois.remove(lastKey-count);
         }*/
         
     }
    /* for(int curSlice = oldStart ; curSlice <= oldEnd ; curSlice++){
         Roi roi = rois.get(curSlice);
         newPosition = curSlice + zCtrDiff;
         if(roi != null){
            newPosition = roi.getPosition() + zCtrDiff;
            roi.setPosition(newPosition);
            tmpRois.put(newPosition,roi);
         }else{                                 // Not sure if there will ever be null!!            
             Roi nwRoi = (Roi) templateRoi.clone();
             nwRoi.setPosition(curSlice);
             tmpRois.put(newPosition, nwRoi);
         }
     } */
     
    // this.rois = tmpRois;
     FindCenter();
     
     System.out.print("\nRepositioned..: New Center"+ this.centerZ);
     return this.centerZ;
    }
    
    /**
     * @param noSlices //the number of slices the user neeeds at the end
     * @return void
     */
    public boolean resizeInZ(int noSlices){
        
        int diff =  noSlices - getThickness();
       
        if(diff < 0){
            //remove the arrayelements from both the ends or from one end and then reposition in Z. 
            // Using the submask has the dependancy  on original so a deep copy is required. Instead
            // we remove the rois from the list. 
           
             //diff *= -1; 
            for(int count = diff ; count <= 0; count++)
                remove(this.getRoi(this.getEndSlice()));
           
             this.repositionZ(-diff/2);
           
        }
        else{
            //add the roi from the final slice to diff number of slices and then reposition in Z
            Roi tmpRoi = this.rois.get(this.getEndSlice());
            Roi newRoi;
            
            switch ( tmpRoi.getType()){
                case Roi.OVAL:
                     newRoi = new OvalRoi(tmpRoi.getBounds().x,tmpRoi.getBounds().y,tmpRoi.getBounds().getHeight(),tmpRoi.getBounds().width);  
                     break;
                default:
                     newRoi = new Roi(tmpRoi.getBounds().x,tmpRoi.getBounds().y,tmpRoi.getBounds().getHeight(),tmpRoi.getBounds().width);  
                     break;     
            }
            
            for(int count = 0 ; count < diff ; count++)
                this.addRoi(tmpRoi);
            this.repositionZ(diff/2);
             IJ.log ( "Done resizing the new size is " + this.getThickness() 
                                    + " Starting at : " + this.getStartSlice() + " and ending at : " + this.getEndSlice());
            
        }
        return true;
    }
    /**
     * 
     * @param roi
     * @param nSlice
     * @return nSlice : returns the slice number corresponding to the roi or -1 in the event where the addition is not successful. 
     */
    public boolean addRoi(Roi roi, int nSlice){
        roi.setPosition(nSlice);
        this.validCenter = false;
        return addRoi(roi);
        //return -1;
    }
    /**
     * 
     * @param roi
     * @return Error Status: returns true if the error /exception occurred during addition of roi
     */
    public boolean addRoi(Roi roi) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      boolean error = false;
        try{
            if(!rois.containsKey(roi.getPosition()))
                        rois.put(roi.getPosition(), roi);
            else{
                if(multipleRoiperSlice){
                    ShapeRoi sr  = new ShapeRoi(roi);
                    rois.put(roi.getPosition(), sr.or(new ShapeRoi(rois.get(roi.getPosition()))));
                }
            }
       }catch( Exception E){
            error = true;
       }
       validCenter = false;
       return error;     //returns true if there is an error and the roi could not be added.
   }

    /**
     *
     * @param roiset
     * @return Error Status: True means error has occurred
     */
    public boolean addRoiSet(Roi[] roiset){
       boolean status = false; 
       for(Roi roi : roiset){
         status = addRoi(roi);  
       }
       validCenter = false;
       return status;
   }
    /**
     * 
     * @param roiset
     * @param nSlice
     * @return Error status: True means error has occurred
     */
    public boolean addRoiSet(Roi[] roiset, int [] nSlice){
        boolean status = false;
        if(roiset.length != nSlice.length){
            //Erroroneous call we need to have the same number of slice numbers as the rois
            //ToDo: Implement throwins an error message or exception
            return true;
        }
        int count = 0;
        for(Roi roi: roiset)
            status = addRoi(roi,nSlice[count++]);  
        validCenter = false;
        return status;
   }
    /**
     * 
     * @param nSlice
     * @return 
     */
    public Roi getRoi(int nSlice){
        return this.rois.get(nSlice);
    }
    /**
     * 
     * @return :Returns the set of 2D rois that constitute this 3D roi.
     */
    public Roi [] getRoiSet(){
        Roi [] roiArray = new Roi[rois.size()];
        Set roiSet = rois.entrySet();
        Iterator it = roiSet.iterator();
        int count = 0;
        while(it.hasNext()){
            Map.Entry roiEntry = (Map.Entry)it.next();
            roiArray[count++] = (Roi) roiEntry.getValue();
        }
        return roiArray;
    }

    /**
     * @return the Name of this 3D Roi
     */
    public String getName() {
        return Name;
    }

    /**
     * @param Name the Name to set for this 3D Roi
     */
    public void setName(String Name) {
        this.Name = Name;
    }

    /**
     * @return the nSlices
     */
    public int getnSlices() {
        return nSlices;
    }

    /**
     * @param nSlices the nSlices to set
     */
    public void setnSlices(int nSlices) {
        this.nSlices = nSlices;
    }

    /**
     * @return the centerX
     */
    public int getCenterX() {
        if(!validCenter)
            FindCenter();
        return centerX;   
    }

    /**
     * @param centerX the centerX to set
     */
    public void setCenterX(int centerX) {
        this.centerX = centerX;
        
    }

    /**
     * @return the centerY
     */
    public int getCenterY() {
        if(!validCenter)
            FindCenter();
        return centerY;
    }

    /**
     * @param centerY the centerY to set
     */
    public void setCenterY(int centerY) {
        this.centerY = centerY;
    }

    /**
     * @return the centerZ
     */
    public int getCenterZ() {
        if(!validCenter)
            FindCenter();
        return centerZ;
    }

    /**
     * @param centerZ the centerZ to set
     */
    public void setCenterZ(int centerZ) {
        this.centerZ = centerZ;
        validCenter = true;
    }

    public boolean remove(Roi roi) {
        validCenter = false;
        return this.rois.remove(roi.getPosition(), roi); 
    }

    
    
    
}
