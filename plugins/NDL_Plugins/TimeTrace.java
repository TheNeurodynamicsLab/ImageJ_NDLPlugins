/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Balaji
 */
import ProgTools.MultiFileDialog;
import ProgTools.MultiSelectFrame;
import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.CurveFitter;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.*;
import java.awt.datatransfer.*;
import javax.swing.*;

public class TimeTrace extends Thread{
    ImagePlus imp;
    RoiManager Manager;
  //  TraceData[] Data;
    TraceData [] zData = null;
    TraceData [] fit = null ;
    TraceData Average;
    TraceData Err;
    ResultsTable cd_Profile;    // place holder for storing centered ZProfile
    ResultsTable rt;
    java.awt.List ROIList;
    Hashtable Rois;
    double Variance;
    double MeanIntensity;
    double Error;
    boolean showAverage = true;
    boolean showAll = false;
    boolean CalAverage = true;
    private boolean SubStack = true;    // Determines if a the series needs to be treated in chucks
    static PlotWindow graph; // To display the average  pixel intensity of all ROIs across the series
    int uiMeasure = 2; // 2 for Pixel average and 3 for Total Intensity;
    private boolean zCentered = true; // Determines if the intensities along the z-Axis is extracted around the focal plane (zcenter/zco-ordinate).
    private int depth = 10; // # of image slices on both sides of the focal plane that would be considered for the measurement
    private boolean filtered = true; //Determines whethere the intesnity filter is on/off. If on then only the datapoints less than IMax are considered for the fit.
    private double IMax = 723522 /176 ;  // Intensity threshold: Data points above this value are filtered for the fit
    public void setTotIntensity(boolean TotIntensity ){
       uiMeasure = (TotIntensity) ? 3 : 2;
    }
    public void setSubStack(boolean x){
        SubStack = x;
    }
    public void setAverage(boolean x){
        CalAverage = x;
        if (!CalAverage)
            showAverage = false;
   }
    public void setDispAll(boolean x){
        showAll = x;
        if(x)
            CalAverage = x;
    }
    public void setAveDisp(boolean x){
        showAverage = x;
        if(x)
            CalAverage = x;
    }
    TimeTrace(ImagePlus imp, RoiManager Manager){
        if (imp != null && Manager != null){
            this.imp = imp;
            this.Manager = Manager;
        }
    }
    TimeTrace(){ // Not working properly I think it is timing issue. Need to fix it in later version
        imp = WindowManager.getCurrentImage();
        Manager = new RoiManager();
        if (imp == null || Manager == null){
            IJ.showMessage("Could not initialize ImagePlus/Roi manager");
            return ;
        }
    }
    public int[] getAllIndexes(int count){
             int[] indexes = new int[count];
             for (int i=0; i<count; i++)
                indexes[i] = i;
             return indexes;
        }
    @Override
    public void run(){
                if (imp != null && Manager != null){
                ROIList = Manager.getList();
                Rois = Manager.getROIs();
                int indexes[] = ROIList.getSelectedIndexes();
                if(indexes.length == 0)
                    indexes = getAllIndexes(ROIList.getItemCount());
                if(indexes.length == 0){
                    IJ.showMessage("You need to add atleast one ROI");
                    return;
                }
                boolean Skip = false;
                if(indexes.length > 148  ){
                    String CurrentVersion = IJ.getVersion();
                   int  newVersion =  CurrentVersion.compareToIgnoreCase("1.40");
                    if (newVersion < 0){
                        Skip = true;
                        IJ.showMessage("Warning","Results table in ImageJ version 1.40 and less can  only display 150 (148 ROis) columns only. Excess "+(indexes.length - 148)+" ROis will be omitted");
                    }
                }
                ImageStatistics stat = new ImageStatistics();
                int MaxSlice = imp.getStackSize();

                int BeginSlice = 1;
                int EndSlice = MaxSlice;
                int TotSlice = MaxSlice;

                if(isSubStack()){
                    GenericDialog gd = new GenericDialog("Sub stack parameters");
                    gd.addNumericField("Begin at slice number",1,0);
                    gd.addNumericField("End at slice number",MaxSlice,0);
                    gd.showDialog();

                    if(gd.wasOKed()){

                        BeginSlice = (int)gd.getNextNumber();
                        EndSlice = (int)gd.getNextNumber();

                        BeginSlice = (BeginSlice >0 && BeginSlice < MaxSlice && BeginSlice < EndSlice) ? BeginSlice : 0;
                        EndSlice = (EndSlice < MaxSlice ) ? EndSlice : MaxSlice;

                        TotSlice = EndSlice - BeginSlice + 1;
                    }


                }
                if(CalAverage){
                    Average = new TraceData(TotSlice);
                    Err = new TraceData(TotSlice);
                }
                if(iszCentered()){
                    zData = new TraceData[indexes.length];
                    fit = new TraceData[indexes.length];
                }

                String Mean = "";
                double Sum, SqSum;
                Roi roi = null;
                int zSlice  = 0, zMin = 0, zMax =0;
                rt = new ResultsTable();
                cd_Profile = new ResultsTable();
                ImageProcessor ip = imp.getProcessor();
                int highLimit = (Skip) ? 148 : indexes.length;
                int nPatches = ((highLimit + 3) / 150) + 1 ;
               /* if(zCentered){
                    //cd_Profile.setValue(Mean, TotSlice, SqSum);
                    cd_Profile.show("zProfile");
                }*/
                for(int i = 1 ; i < nPatches; i++) rt.addColumns();
                for(int i = 0; i < highLimit ; i ++){ //ImageJ 1.40 and below results table can only handle 150 columns
                    roi = (Roi)Rois.get(ROIList.getItem(indexes[i]));
                    //rt.setHeading(i,"ROI" + i + "\t");
                    rt.setHeading(i+2,roi.getName()/*+"\t"*/);
                }
                rt.setHeading( 0, "Average");
                rt.setHeading( 1, "Err");
                //ROIList = Manager.getList();
                imp.unlock();
                double Int = 0;
                for (int CurSlice = BeginSlice, SliceNum = 0 ; CurSlice <= EndSlice ; CurSlice ++, SliceNum++){
                    imp.setSlice(CurSlice+1);
                    Sum = 0;
                    SqSum = 0;
                    rt.incrementCounter();
                    cd_Profile.incrementCounter();
                    /*if(ReCtrMean){
                        recenter(imp,CurSlice+1);
                        imp.setSlice(CurSlice+1);
                        imp.setRoi(all);
                    }*/
                  for (int CurIdx = 0; CurIdx < highLimit; CurIdx++){ //ImageJ 1.40 and below results table can only handle 150 columns
                        
                        roi = (Roi)Rois.get(ROIList.getItem(indexes[CurIdx]));
                        imp.setRoi(roi);
                        stat = imp.getStatistics(uiMeasure); // MEAN = 2
                        Int = (uiMeasure == 2) ? stat.mean : stat.mean *stat.area;
                        rt.addValue(CurIdx+2,Int);

                        if(iszCentered()){
                            if(CurSlice == BeginSlice){
                                zData[CurIdx] = new TraceData(22);
                                fit[CurIdx] = new TraceData(5);
                            }
                            zSlice = Manager.getSliceNumber(roi.getName());
                            zMin = zSlice - 10 ;//zRange/2 ;
                            zMax = zSlice + 10 ;//zRange/2 ;
                            if(CurSlice >= zMin && CurSlice <= zMax){
                                    zData[CurIdx].addData(CurSlice, Int);
                                    //if(cd_Profile.getCounter() == 0) cd_Profile.incrementCounter();
                                    cd_Profile.addValue(roi.getName(),Int);
                                
                            }                
                        }
                        Sum += Int;
                        SqSum += (Int * Int) ;
                    }
                    if(CalAverage){
                        MeanIntensity = Sum/indexes.length;
                        Average.addData(SliceNum, MeanIntensity);
                        Variance = ((SqSum/indexes.length)- MeanIntensity*MeanIntensity);
                        Error = (true /*StdErr*/) ? java.lang.Math.sqrt(Variance/indexes.length)
                                                                            : java.lang.Math.sqrt(Variance);
                        Err.addData(SliceNum,Error);
                        rt.addValue("Average",MeanIntensity);
                        rt.addValue("Err",Error);
                    }

                }
                if(iszCentered()){
                        cd_Profile.show("zProfile");
                        ResultsTable fitResults = new ResultsTable();
                        double [] ydata = null, xdata = null, ny = null, nx = null;
                        ResultsTable filData = new ResultsTable();
                        String  Heading = cd_Profile.getColumnHeading(1);
                        //double IMax = (uiMeasure == 2 )? 723522 /176 : 723522.0 ; //4110 is the pixel average
                        for(int CurIdx = 0 ; CurIdx < highLimit ; CurIdx++){
                            ydata = zData[CurIdx].getY(true);
                            xdata = zData[CurIdx].getX(true);
                            //filData.s
                            if(isFiltered()){
                               int datalength = ydata.length;
                                ny = new double[datalength];
                                nx = new double[datalength];
                                int newIdx = 0;
                                   for(int index = 0; index < datalength ; index++){
                                       if( ydata[index] < getIMax()  /* ~30 % of pixels are saturated in a
                                                                    15 dia disc . Intensity threshold) */ ){
                                           ny[newIdx] = ydata[index];
                                           nx[newIdx] = xdata[index];
                                           while(filData.getCounter() <= newIdx)
                                               filData.incrementCounter();
                                           filData.setValue(cd_Profile.getColumnHeading(CurIdx)+"_x", newIdx, nx[newIdx]);
                                           filData.setValue(cd_Profile.getColumnHeading(CurIdx)+"_y", newIdx, ny[newIdx]);
                                           newIdx++;
                                       }
                                       
                                   }
                                ny = Arrays.copyOf(ny, newIdx-1);
                                nx = Arrays.copyOf(nx, newIdx-1);
                                  
                            }

                            CurveFitter Fitter =  isFiltered() ? new CurveFitter(nx,ny) : new CurveFitter(zData[CurIdx].getX(true),zData[CurIdx].getY(true));
                            Fitter.doFit(CurveFitter.GAUSSIAN);
                            int nParam = Fitter.getNumParams(); //4 for Gaussian
                            IJ.log(Fitter.getResultString());
                            double [] fitParam = Fitter.getParams();
                            fitResults.incrementCounter();
                            if(CurIdx == 0){

                            }
                            fitResults.addLabel("ROI Name", roi.getName());
                            fitResults.addValue("Number of Datapoints", Fitter.getYPoints().length);

                            for(int i = 0 ; i < nParam ; i ++){
                                //fit[CurIdx].addData(i, fitParam[i]);
                               /* if(CurIdx == 0){
                                    fitResults.incrementCounter();
                                    fitResults.addLabel("Param"+i);
                                    //fitResults.addLabel(""+i);
                                }*/
                                //fitResults.incrementCounter();
                                roi = (Roi)Rois.get(ROIList.getItem(indexes[CurIdx]));
                                fitResults.addValue("Param"+i, fitParam[i]);
                            }

                            fitResults.addValue("Goodness of Fit", Fitter.getFitGoodness());
                            fitResults.addValue("RSquared", Fitter.getRSquared());
                            double filt = Fitter.getFitGoodness() > 0.95 ? 1 : 0;
                            fitResults.addValue("Filter", filt);
                            fitResults.addValue("MaxInt", zData[CurIdx].getYMax());
                            fitResults.addValue("MinInt", zData[CurIdx].getYMin());
                            double filtInt = (Fitter.getFitGoodness() > 0.95 ) ? (fitParam[1]-fitParam[0]) : zData[CurIdx].getYPk();
                            fitResults.addValue("FiltInt", filtInt );
                          /*  if(CurIdx == 0){
                                fitResults.incrementCounter();
                                fitResults.addLabel("Number of Datapoints");
                            }
                            fitResults.setValue(roi.getName(),0,(Fitter.getYPoints()).length);
                            
                            for(int i = 0 ; i < nParam ; i ++){
                                fit[CurIdx].addData(i, fitParam[i]);
                                if(CurIdx == 0){
                                    fitResults.incrementCounter();
                                    fitResults.addLabel("Param"+i);
                                    //fitResults.addLabel(""+i);
                                }
                                //fitResults.incrementCounter();
                                roi = (Roi)Rois.get(ROIList.getItem(indexes[CurIdx]));
                                fitResults.setValue(roi.getName(),i+1, fitParam[i]);
                            }
                            if(CurIdx == 0)  {
                                String [] Label = {"Area","Goodness of Fit","RSquared"};
                                for(int i = 0 ; i < 3/* no of additional parameters ; i++){
                                    fitResults.incrementCounter();
                                    fitResults.addLabel(Label[i]);
                                }
                                    fitResults.addLabel("Goodness of Fit");
                                    fitResults.addLabel("RSquared");
                               }
                            

                            fitResults.setValue(roi.getName(),nParam+1, ((fitParam[1]-fitParam[0])*fitParam[4]*Math.sqrt(2*Math.PI)));
                            fitResults.setValue(roi.getName(),nParam+2,Fitter.getFitGoodness());
                            fitResults.setValue(roi.getName(),nParam+3,Fitter.getRSquared());*/
                            



                    }
                        fitResults.show("Fit Results");
                        filData.show("Filtered Data");
                }
                    
                if(showAverage)
                {
                    rt.show("Time Trace(s)");
                    double [] xAxis = new double[TotSlice];
                    for(int nFrames = 1 ; nFrames <= TotSlice ; nFrames++)
                       xAxis[nFrames-1] = nFrames;
                    Plot plot = new Plot("Time Trace Average","Time (Frames)","Average Intensity",xAxis,Average.getY());
                    //plot.addErrorBars(Err);
                    plot.draw();
                    if(WindowManager.getImage("Time Trace Average")== null)
                        graph = null;
                   if(graph == null){
                        graph = plot.show();
                        //graph.addErrorBars(Err);
                        graph.addPoints(xAxis,Average.getY(),PlotWindow.CIRCLE);
                    }
                    else{
                        graph.drawPlot(plot);
                       // graph.addErrorBars(Err);
                        graph.addPoints(xAxis,Average.getY(),PlotWindow.CIRCLE);
                    }
                }
            }

    }


    public double[] getAverageData() {
        return (double[]) Average.getY().clone();
    }

    /**
     * @return the zCentered
     */
    public boolean iszCentered() {
        return zCentered;
    }

    /**
     * @param zCentered the zCentered to set
     */
    public void setzCentered(boolean zCentered) {
        this.zCentered = zCentered;
    }

    /**
     * @return the SubStack
     */
    public boolean isSubStack() {
        return SubStack;
    }

    /**
     * @return the filtered
     */
    public boolean isFiltered() {
        return filtered;
    }

    /**
     * @param filtered the filtered to set
     */
    public void setFiltered(boolean filtered) {
        this.filtered = filtered;
    }

    /**
     * @return the IMax
     */
    public double getIMax() {
        return IMax;
    }

    /**
     * @param IMax the IMax to set
     */
    public void setIMax(double IMax) {
        this.IMax = IMax;
    }
}
class TraceData extends Object{
    double[] xData = null;
    double[] yData = null;
    double x_Max = Double.MIN_VALUE;
    double y_Max =Double.MIN_VALUE;
    double x_Min = Double.MAX_VALUE;
    double y_Min = Double.MAX_VALUE;
    double x_Sum = 0;
    double y_Sum = 0;
    int CurrPos = 0;
    int DataLength = 0;
    int ActLength =0;                       //Needs comment : to say what is the difference between DataLength and ActLength
                                            // Actlength - the number of datapoints that are non zero ?
                                            // Datalength - the capacity of the Data ie) the maximum number of data pts that can be held in the object
    //boolean Y_Only = false;
   public TraceData( int length){
        if (length > 0){
            DataLength = length;
            xData = new double[DataLength];
            yData = new double[DataLength];
        }
    }
   public TraceData( double[] x, double[] y){
        if( x != null && y != null){
            xData = (double[])x.clone();
            yData = (double[])y.clone();
            DataLength = Math.min(xData.length,yData.length);
        }
    }
   public boolean addData(double x, double y){
        if (CurrPos >= DataLength){
            IJ.showMessage("OOPS! I am full you can not add anymore to me");
            return false;
        }

        xData[CurrPos] = x;
        yData[CurrPos] = y;
        CurrPos++;
        ActLength =  CurrPos > ActLength ? CurrPos : ActLength;

        /* Update the stat parameters: This is the only entry point of the data */

        setStat( CurrPos-1);

        return true;
    }
   public double getX(int pos){
       if(pos < DataLength)
           return xData[pos];
       return xData[DataLength];
   }
   public double getY(int pos){
      if(pos < DataLength) return yData[pos];
      return yData[DataLength];
   }
   public double[] getXY(int pos){
       double[] XY = new double[2];
       if (pos < DataLength){
            XY[1] = xData[pos];
            XY[2] = yData[pos];
       }
       else{
            XY[1] = xData[DataLength];
            XY[2] = yData[DataLength];
       }
       return XY;
   }
   public boolean setPosition(int pos){
       if(pos < DataLength){
           CurrPos = pos;
           return true;
       }
     return false;
   }
   public int getPosition(){
       return CurrPos;
   }
   public int getDataLength(){
       return DataLength;
   }
   public double[] getX(){
       return (double [])xData.clone();
   }
   public double[] getY(){
       return (double [])yData.clone();
   }
   public double[] getX(boolean trimmed){
       return Arrays.copyOf(xData, ActLength);
   }
   public double[] getY(boolean trimmed){
       return Arrays.copyOf(yData, ActLength);
   }
   public boolean setLength(int length){
       if (DataLength != 0)
           return false;            // The object is holding a data array. In order to reset one needs to use the override method (just an extra protection)
        if (length > 0){
            DataLength = length;
            xData = new double[DataLength];
            yData = new double[DataLength];
            CurrPos = ActLength = 0;
            x_Min = y_Min = Double.MAX_VALUE;
            x_Max= y_Max = x_Sum = y_Sum = 0;
            return true;
        }
       return false;
   }
   public void OverrideLength(int length){
       if (length == 0){
           xData = null;
           yData = null;
           x_Min = y_Min = Double.MAX_VALUE;
            x_Max= y_Max = x_Sum = y_Sum = 0;
           return;
       }
       DataLength = length;
       xData = new double[DataLength];
       yData = new double[DataLength];
       CurrPos = ActLength = 0;
       x_Min = y_Min = Double.MAX_VALUE;
       x_Max= y_Max = x_Sum = y_Sum = 0;
       return;
   }
  void  setStat(double x, double y){
       x_Max = (x_Max > x ) ? x_Max : x ;
        y_Max = (y_Max > y ) ? y_Max : y ;

        x_Min = (x_Min < x ) ? x_Min  : x ;
        y_Min = (y_Min < y ) ? y_Min : y ;

        x_Sum += x;
        y_Sum += y;
   }
  void setStat(int pos){

        double x = getX(pos);
        double y = getY(pos);

        x_Max = (x_Max > x ) ? x_Max : x ;
        y_Max = (y_Max > y ) ? y_Max : y ;

        x_Min = (x_Min < x ) ? x_Min  : x ;
        y_Min = (y_Min < y ) ? y_Min : y ;

        x_Sum += x;
        y_Sum += y;
  }

  void setStat(boolean all){
      if(all){
          double x =0;
          double y = 0;
          for(int i = 0 ; i < ActLength ; i++){
            x = getX(i);
            y = getY(i) ;
            
            x_Max = (x_Max > x ) ? x_Max : x ;
            y_Max = (y_Max > y ) ? y_Max : y ;

            x_Min = (x_Min < x ) ? x_Min  : x ;
            y_Min = (y_Min < y ) ? y_Min : y ;

            x_Sum += x;
            y_Sum += y;
          }
      }else{
          setStat(this.getPosition());
      }
  }
  public double getXMax(){
      return x_Max;
  }
  public double getYMax(){
      return y_Max;
  }
  public double getXMin(){
      return x_Min;
  }
  public double getYMin(){
      return y_Min;
  }
  public double getXSum(){
      return x_Sum;
  }
  public double getYSum(){
      return y_Sum;
  }
  public double getYPk(){
      return y_Max - y_Min ;
  }
  public double getXPk(){
      return x_Max - x_Min;
  }

}
